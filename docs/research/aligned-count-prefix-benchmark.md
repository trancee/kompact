# Aligned count prefixes and small generated holders

## Finding and correction

The [initial generated-holder comparison](generated-holder-benchmark.md)
found a 10.57% regression for 65 fixed elements despite the sparse index's
large-repeat improvement. A fresh controlled comparison reproduced it:
100.666 to 111.131 ns/op, **+10.40%**, above the unchanged 10% budget.
The [diagnostic samples](../../benchmarks/results/holders-prefix-2026-10-17/diagnostic-original/)
retain that failure rather than replacing the baseline.

[JIT diagnostics](../../benchmarks/results/holders-prefix-2026-10-17/jit-diagnosis.txt)
showed the original per-element `readFixedRepeat` at
326 bytecodes, rejected for hot inlining, and the merged sparse method at
315 bytecodes, hot-inlined; this VM's `FreqInlineSize` was 325.
Disabling only that method's inlining on the unchanged jars changed the
comparison to 100.435 versus 81.289 ns/op, **-19.06%**. The
[probe samples](../../benchmarks/results/holders-prefix-2026-10-17/diagnostic-noinline/)
implicate the compiled caller context, not merely checkpoint arithmetic.
This is a diagnostic intervention, not a portable compiler guarantee.
The probe used:
`-XX:CompileCommand=dontinline,ch.trancee.kompact.runtime.KompactCursorRepeats::readFixedRepeat`.
Acceptance measurements below use no special VM options.

The correction avoids generic bit-chunk reading for the already checked,
byte-aligned 8/16/32-bit fixed-repeat count prefix. A private helper assembles
unsigned bytes in little-endian order into a `Long`. Checkpoint arithmetic
and storage, bounds/error precedence, cursor/workspace binding, and failure
atomicity are unchanged. No compiler directive or deployment flag is needed.

Advancing/grouped checkpoint writes, endpoint-bounded loops, and workspace
extraction were rejected: some improved small holders but regressed direct
or large indexing by 35-65%. No experimental loop restructuring remains.
The decision and risks are recorded in
[ADR-0008](../adr/0008-framed-generated-views.md#2026-10-17-clarification-checked-aligned-count-prefix-reading).

## Comparison boundary and provenance

The acceptance baseline is merged `6115b87`; the candidate is that base plus
the retained uncommitted source change. An additional focused comparison
uses the original pre-sparse runtime `0d242ca`. The
[source/binary manifest](../../benchmarks/results/holders-prefix-2026-10-17/source-sha256.txt)
identifies the runtime, maintained workloads, generated decoders, and exact
measured binaries. Both reference runtime trees differ from the candidate
only in `KompactCursorRepeats.kt`; all maintained holder sources/configuration
and generated Kotlin sources match byte-for-byte.

The operation boundaries are unchanged: complete generated `decodeInto`
binding for holders, or one-pass fixed/two-pass variable cursor indexing
for the root benchmark. Preparation and storage allocation remain outside
measurement. Each full pair includes unchanged variable/scalar operations
and an allocating control, not just the optimized cases.

Measurements ran on 2026-10-17 on an Apple M1, eight cores, 8 GiB memory,
macOS `27.0.1`, AC power. Tooling: Kotlin `2.4.21`, KSP `2.3.12`,
Gradle `9.8.0`, `kotlinx-benchmark` `0.5.0`, JMH `1.37`.
JVM pairs used Temurin `25.0.4+7-LTS` or OpenJDK `21.0.12.1`.
Both jars in the JDK 21 pair were built on JDK 21: Kotlin's bytecode target
21 alone does not constrain generated JMH Java harness classes.
Native used release-optimized macOS Arm64 executables.

Every full profile used five warmups and five 300 ms measurements, `ns/op`,
one thread, three JVM forks (15 samples), or one five-sample Native run.
No task-owned builds, device tests, or other measurements overlapped a
timing session. The host was shared: unrelated load, affinity, frequency,
and thermal state were not controlled. Results are host diagnostics,
not Android ART/Native or physical-iOS latency guarantees.

## Generated-holder results

The complete unprofiled JDK 25 pair ran baseline first. JMH scores and
reported errors are `ns/op`; change is candidate relative to baseline.

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

The candidate-first JDK 25 GC-profiler pair and baseline-first JDK 21
GC-profiler pair also passed:

| Pair | Fixed 65 | Fixed 4096 | Fixed 65536 | Largest unchanged-control regression |
| --- | ---: | ---: | ---: | ---: |
| JDK 25, GC, reverse | -21.96% | -14.89% | -6.75% | +1.01% |
| JDK 21, GC | -24.84% | -11.12% | -8.03% | +0.77% |

The reverse JDK 25 fixed-65 baseline was noisy (102.138 +/- 9.636 ns/op),
so its exact percentage is less precise. The unprofiled and JDK 21 pairs
independently retain the improvement.

Against the original `0d242ca` runtime, the exact final holder jar measured
101.332 to 81.093 ns/op at 65 elements, 1505.565 to 97.998 at 4096, and
22615.306 to 250.155 at 65536: -19.97%, -93.49%, and -98.89%
(about 90.4 times faster at the largest size). This
[focused comparison](../../benchmarks/results/holders-prefix-2026-10-17/preoptimization-fixed/)
contains three fixed cases, not full-profile control acceptance.

## Direct indexing and Native tradeoff

The exact final root JVM jar passed both orders with GC profiling:

| Pair | Fixed 65 | Fixed 4096 | Fixed 65536 | Largest unchanged-control regression |
| --- | ---: | ---: | ---: | ---: |
| JDK 25, forward | -42.98% | -19.90% | -1.46% | +1.74% |
| JDK 25, reverse | -42.43% | -20.30% | -1.33% | +0.88% |

Both full release Native pairs passed the same mean-based 10% budget,
but the largest fixed case was slower and close to the threshold:

| Order | Fixed count | Baseline ns/op | Candidate ns/op | Change |
| --- | ---: | ---: | ---: | ---: |
| Forward | 65 | 21.107 | 17.899 | -15.20% |
| Forward | 4096 | 52.758 | 48.325 | -8.40% |
| Forward | 65536 | 599.466 | 641.021 | +6.93% |
| Reverse | 65 | 21.203 | 17.984 | -15.18% |
| Reverse | 4096 | 52.963 | 52.405 | -1.05% |
| Reverse | 65536 | 601.947 | 659.859 | +9.62% |

Unchanged Native controls were at most +3.87%. These observations do not
establish that every workload is faster or that the Native margin will
hold on another host. No budget was waived or increased.

Across the final GC-profiled JVM pairs, fixed operations remained below
`0.006 B/op`, scalars below `0.001 B/op`, and allocating controls at least
`24 B/op`. Direct variable indexing at 65536 stayed below `15.35 B/op`,
within its `20 B/op` budget. Complete variable holders remained around
`23 B/op`; their preflight plus indexing is not the standalone index-only
budgeted operation. Small fractional profiler values are not fractional objects
or universal zero-allocation proof.

## Retained full reports

Each directory contains `reference.json` and `candidate.json`, retained by
the maintained report checker. Every pair passed its optional `--baseline`
comparison, including unchanged controls:

| Pair | Reports |
| --- | --- |
| Holders, JDK 25, unprofiled, baseline first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/holders-jdk25-plain/) |
| Holders, JDK 25, GC, candidate first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/holders-jdk25-gc-reverse/) |
| Holders, JDK 21, GC, baseline first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/holders-jdk21-gc/) |
| Direct runtime, JDK 25, GC, baseline first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/runtime-jdk25-gc-forward/) |
| Direct runtime, JDK 25, GC, candidate first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/runtime-jdk25-gc-reverse/) |
| Direct runtime, release Native, baseline first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/runtime-native-forward/) |
| Direct runtime, release Native, candidate first | [pair](../../benchmarks/results/holders-prefix-2026-10-17/runtime-native-reverse/) |

The [measurement guide](../how-to/benchmark-runtime.md) gives the build,
JMH GC, unprofiled, and Native commands. The new
[`--baseline` check](../how-to/benchmark-runtime.md#automate-the-timing-comparison)
refuses incomplete, smoke, or incompatible reports; without that option,
CI still validates execution and report shape only. Retained reports omit
local VM paths/properties; meaningful VM options and run order are stated
above. The focused diagnostic subsets are not full-profile acceptance.

## Correctness and compatibility

The shared checkpoint matrix now includes 16-bit elements and count-byte
boundaries through 65536. A new test checks unsigned interpretation of every
32-bit prefix byte using exact workspace-capacity diagnostics, without
allocating large payloads. Removing the byte mask made it fail with expected
capacity 4 versus actual 1; the restored implementation passes.

Forced local gates passed: runtime JVM 424 tests, Android host 399,
iOS Simulator 404, KSP 206, integration two, plugin 45; root JVM/Native
fixtures two/three, and holder JVM/iOS Simulator fixtures 11 each.
Runtime/KSP/plugin ABI, 100% line/branch Kover, plugin validation, Android
AAR, fresh Android Native and iOS Arm64 debug/release linking passed.
Forced API Markdown generation produced no committed diff.
The report-tool suite passes 30 tests and the actual CLI rejects the
historical retained +10.57% regression.

Fresh physical Android binaries passed 404 tests each in three debug and
three release executions, with integrity and cleanup verified.
[Physical evidence](physical-runtime-validation.md#aligned-prefix-follow-up)
retains exact source/binary hashes and bounded allocation samples.
Physical iOS was explicitly deferred at measurement time for its signing
prerequisite; Simulator results did not replace it. The later
[physical iOS follow-up](physical-runtime-validation.md#physical-ios-follow-up)
validates merged `465cb86` with three debug and three release executions,
404 tests each, after profile renewal and a watchdog-safe runner correction.

This is a compatible internal performance fix: no public API, wire/schema,
status precedence, workspace capacity, dependencies, or supported targets
change. No migration or manual edit to released changelog sections is
needed. These were local results at measurement time; authoritative remote
CI for that change had not yet run.
