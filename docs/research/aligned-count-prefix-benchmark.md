# Aligned count prefixes and small generated holders

## Finding and correction

The [initial generated-holder comparison](generated-holder-benchmark.md)
found a 10.57% regression for 65 fixed elements, even though the sparse index
improved large repeats. A fresh controlled comparison reproduced it:
100.666 to 111.131 ns/op, +10.40%, above the unchanged 10% budget.
The [diagnostic samples](../../benchmarks/results/holders-prefix-2026-10-17/diagnostic-original/)
keep that failure on record instead of replacing the baseline.

The [JIT diagnostics](../../benchmarks/results/holders-prefix-2026-10-17/jit-diagnosis.txt)
showed that the original per-element `readFixedRepeat` was 326 bytecodes and
the JIT rejected it for hot inlining. The merged sparse method was
315 bytecodes and the JIT inlined it as hot. This VM's `FreqInlineSize` was
325. Disabling inlining for only that method, on the unchanged jars, changed
the comparison to 100.435 versus 81.289 ns/op, -19.06%. The
[probe samples](../../benchmarks/results/holders-prefix-2026-10-17/diagnostic-noinline/)
point at the compiled caller context, not only at checkpoint arithmetic.
The probe is a diagnostic intervention, not a portable compiler guarantee.
It used this option:
`-XX:CompileCommand=dontinline,ch.trancee.kompact.runtime.KompactCursorRepeats::readFixedRepeat`.
The acceptance measurements below use no special VM options.

The correction stops using generic bit-chunk reading for the fixed-repeat
count prefix when the prefix is 8, 16, or 32 bits wide, byte-aligned, and
already checked. A private helper, `readAlignedCountPrefix` in
`KompactCursorRepeats.kt`, assembles the unsigned bytes in little-endian order
into a `Long`. The change leaves these unchanged: checkpoint arithmetic and
storage, bounds checks and error precedence, cursor and workspace binding, and
failure atomicity. It needs no compiler directive or deployment flag.

Three other changes were tried and rejected: advancing or grouped checkpoint
writes, endpoint-bounded loops, and workspace extraction. Some of them
improved small holders but made direct or large indexing 35-65% slower. None
of these experimental loop changes remain in the code.
[ADR-0008](../adr/0008-framed-generated-views.md#2026-10-17-clarification-checked-aligned-count-prefix-reading)
records the decision and its risks.

## Comparison boundary and provenance

The acceptance baseline is merged `6115b87`. The candidate is that base plus
the source change described above, which was uncommitted when measured. A
further focused comparison uses the original runtime from before the sparse
index, `0d242ca`. The
[source and binary manifest](../../benchmarks/results/holders-prefix-2026-10-17/source-sha256.txt)
identifies the runtime, the maintained workloads, the generated decoders, and
the exact measured binaries. Both reference runtime trees differ from the
candidate only in `KompactCursorRepeats.kt`. All maintained holder sources,
the holder configuration, and the generated Kotlin sources match byte for byte.

The measured operations are the same as before. For holders, the operation is
the complete generated `decodeInto` binding. For the root benchmark, it is
cursor indexing: one pass for fixed repeats and two passes for variable
repeats. Preparation and storage allocation stay outside measurement. Each
full pair also measures the unchanged variable and scalar operations and an
allocating control, not only the optimized cases.

Measurements ran on 2026-10-17 on an Apple M1 with eight cores, 8 GiB memory,
macOS `27.0.1`, and AC power. The tools were Kotlin `2.4.21`, KSP `2.3.12`,
Gradle `9.8.0`, `kotlinx-benchmark` `0.5.0`, and JMH `1.37`.
JVM pairs used Temurin `25.0.4+7-LTS` or OpenJDK `21.0.12.1`.
Both jars in the JDK 21 pair were built on JDK 21. Kotlin's bytecode target
21 alone does not constrain the Java classes that JMH generates.
Native used release-optimized macOS Arm64 executables.

Every full profile used five warmups and five 300 ms measurements in `ns/op`
with one thread. JVM runs used three forks (15 samples). Native used one
five-sample run. No builds, device tests, or other measurements from this task
overlapped a timing session. The host was shared, and nobody controlled
unrelated load, affinity, frequency, or thermal state. The results are host
diagnostics. They are not latency guarantees for Android ART, Android Native,
or physical iOS.

## Generated-holder results

The complete unprofiled JDK 25 pair ran the baseline first. JMH scores and
reported errors are in `ns/op`. The change is the candidate relative to the
baseline.

| Case | Count | Baseline +/- error | Candidate +/- error | Change |
| --- | ---: | ---: | ---: | ---: |
| Fixed holder | 65 | 108.583 +/- 0.928 | 80.125 +/- 0.777 | -26.21% |
| Fixed holder | 4096 | 117.756 +/- 1.254 | 95.571 +/- 0.832 | -18.84% |
| Fixed holder | 65536 | 268.090 +/- 1.947 | 252.008 +/- 2.440 | -6.00% |
| Variable holder | 65 | 1023.686 | 1022.463 | -0.12% |
| Variable holder | 4096 | 63494.536 | 63481.955 | -0.02% |
| Variable holder | 65536 | 1015197.383 | 1017169.966 | +0.19% |
| Scalar holder | - | 26.477 | 26.524 | +0.18% |
| Allocating control | - | 4.279 | 4.281 | +0.05% |

Two GC-profiler pairs also passed. The JDK 25 pair ran the candidate first,
and the JDK 21 pair ran the baseline first:

| Pair | Fixed 65 | Fixed 4096 | Fixed 65536 | Largest unchanged-control regression |
| --- | ---: | ---: | ---: | ---: |
| JDK 25, GC, reverse | -21.96% | -14.89% | -6.75% | +1.01% |
| JDK 21, GC | -24.84% | -11.12% | -8.03% | +0.77% |

The fixed-65 baseline in the reverse JDK 25 pair was noisy
(102.138 +/- 9.636 ns/op), so its exact percentage is less precise. The
unprofiled pair and the JDK 21 pair each show the improvement independently.

Against the original `0d242ca` runtime, the exact final holder jar went from
101.332 to 81.093 ns/op at 65 elements, from 1505.565 to 97.998 at 4096, and
from 22615.306 to 250.155 at 65536. Those changes are -19.97%, -93.49%, and
-98.89%. The largest size is about 90.4 times faster. This
[focused comparison](../../benchmarks/results/holders-prefix-2026-10-17/preoptimization-fixed/)
contains three fixed cases. It is not a full-profile acceptance with controls.

## Direct indexing and the Native tradeoff

The exact final root JVM jar passed in both run orders with GC profiling:

| Pair | Fixed 65 | Fixed 4096 | Fixed 65536 | Largest unchanged-control regression |
| --- | ---: | ---: | ---: | ---: |
| JDK 25, forward | -42.98% | -19.90% | -1.46% | +1.74% |
| JDK 25, reverse | -42.43% | -20.30% | -1.33% | +0.88% |

Both full release Native pairs passed the same mean-based 10% budget.
The largest fixed case was slower in both, and close to the threshold:

| Order | Fixed count | Baseline ns/op | Candidate ns/op | Change |
| --- | ---: | ---: | ---: | ---: |
| Forward | 65 | 21.107 | 17.899 | -15.20% |
| Forward | 4096 | 52.758 | 48.325 | -8.40% |
| Forward | 65536 | 599.466 | 641.021 | +6.93% |
| Reverse | 65 | 21.203 | 17.984 | -15.18% |
| Reverse | 4096 | 52.963 | 52.405 | -1.05% |
| Reverse | 65536 | 601.947 | 659.859 | +9.62% |

The unchanged Native controls regressed by at most +3.87%. These results do
not show that every workload is faster, or that the Native margin holds on
another host. No budget was waived or raised.

Across the final GC-profiled JVM pairs, fixed operations stayed below
`0.006 B/op` and scalars below `0.001 B/op`. The allocating controls stayed at
or above `24 B/op`. Direct variable indexing at 65536 stayed below
`15.35 B/op`, within its `20 B/op` budget. Complete variable holders stayed
around `23 B/op`. A complete variable holder runs preflight plus indexing, so
it is not the index-only operation that the budget covers. Small fractional
profiler values do not mean fractional objects, and they do not prove zero
allocation in general.

## Retained full reports

Each directory contains `reference.json` and `candidate.json`. The maintained
report checker, `benchmarks/check_results.py`, validates their retained
format. Every pair passed the checker's optional `--baseline` comparison,
including the unchanged controls:

| Pair | Reports |
| --- | --- |
| Holders, JDK 25, unprofiled, baseline first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/holders-jdk25-plain/) |
| Holders, JDK 25, GC, candidate first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/holders-jdk25-gc-reverse/) |
| Holders, JDK 21, GC, baseline first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/holders-jdk21-gc/) |
| Direct runtime, JDK 25, GC, baseline first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/runtime-jdk25-gc-forward/) |
| Direct runtime, JDK 25, GC, candidate first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/runtime-jdk25-gc-reverse/) |
| Direct runtime, release Native, baseline first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/runtime-native-forward/) |
| Direct runtime, release Native, candidate first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/runtime-native-reverse/) |

The [measurement guide](../how-to/benchmark-runtime.md) gives the build, JMH
GC, unprofiled, and Native commands. The
[`--baseline` check](../how-to/benchmark-runtime.md#automate-the-timing-comparison),
added with this change, rejects incomplete, smoke, or incompatible reports.
CI runs the checker without that option, so CI checks only that the benchmarks
ran and that the reports have the expected shape. The retained reports omit
local VM paths and VM properties. This note states the VM options that matter
and the run order. The focused diagnostic subsets are not full-profile
acceptance.

## Correctness and compatibility

The shared checkpoint test matrix now includes 16-bit elements and count-byte
boundaries through 65536. A new test checks the unsigned interpretation of
every 32-bit prefix byte. It uses the exact workspace-capacity diagnostics, so
it needs no large payloads. With the byte mask removed, the test failed with
expected capacity 4 versus actual 1. The restored implementation passes.

These forced local gates passed at measurement time:

- Runtime tests: JVM 424, Android host 399, and iOS Simulator 404.
- KSP 206 tests, integration two, and Gradle plugin 45.
- Root benchmark fixtures: JVM two and Native three.
- Holder fixtures: JVM 11 and iOS Simulator 11.
- ABI checks for the runtime, KSP, and plugin, and 100% line and branch Kover
  coverage.
- Plugin validation, Android AAR assembly, and fresh Android Native and iOS
  Arm64 debug and release linking.

Forced API Markdown generation produced no committed diff. The report-tool
suite passed 30 tests, and the real CLI rejected the historical +10.57%
regression that this note retains.

Fresh physical Android binaries passed 404 tests in each of three debug and
three release executions. The runner verified binary integrity and cleanup.
The [physical evidence](physical-runtime-validation.md#aligned-prefix-follow-up)
keeps the exact source and binary hashes and the bounded allocation samples.
At measurement time, physical iOS was deferred on purpose because its signing
prerequisite was not met. Simulator results did not replace it. The later
[physical iOS follow-up](physical-runtime-validation.md#physical-ios-follow-up)
validates merged `465cb86` with three debug and three release executions,
404 tests each. That run followed a profile renewal and a fix to the runner's
launch-watchdog failure.

This is a compatible internal performance fix. It changes no public API, wire
format or schema, status precedence, workspace capacity, dependency, or
supported target. It needs no migration and no manual edit to released
changelog sections. These were local results at measurement time. The
authoritative remote CI for that change had not run yet.
