# Generated-holder decoding: initial host comparison

## Question and operation boundary

Does the sparse fixed-repeat checkpoint optimization remain useful inside a
complete generated holder's `decodeInto`, including frame preflight, scalar
decoding, borrowed ranges, and repeat index binding?

The baseline runtime is `0d242ca`; the candidate is merged `ed7218c` from
[#99](https://github.com/trancee/kompact/pull/99). Their maintained common runtime
sources differ only in `KompactCursorRepeats.kt`. No production runtime or
generator source was changed for this experiment.

Both revisions ran the same new `benchmarks/holders` consumer. Its sources and
generated decoder sources were compared byte-for-byte before measurement.
The [source hashes](../../benchmarks/results/holders-2026-10-17/source-sha256.txt)
identify the previously uncommitted harness used for all six runs.

Each measured decode alternates two prepared messages, resets a caller-owned
cursor, invokes the actual generated holder decoder, checks success, and
returns a primitive checksum. Message construction, holder/range/cursor
allocation, and checkpoint storage allocation are outside measurement.
The fixed and variable messages contain a scalar header and a borrowed
128-byte blob. Fixed elements are 16-bit scalars; variable elements have
8-bit byte-length prefixes and payload lengths cycling from zero to eight.
Counts are `65`, `4096`, and `65536`.

This is complete holder binding, not materialization of every repeated value,
blob copying, string conversion, application I/O, or a production latency
distribution. Separate tests consume elements across checkpoint boundaries.
The allocating control returns a new eight-byte array.

The practical comparison threshold is the repository's 10% regression budget.
The hypothesis is a greater-than-10% improvement for large fixed repeats
without a greater-than-10% regression in the other maintained cases. The
small fixed-repeat result below does **not** meet that condition. This is the
historical measurement; the
[aligned-prefix follow-up](aligned-count-prefix-benchmark.md) addresses that
finding without changing the budget.

## Toolchain and environment

Measured on 2026-10-17: Apple M1, eight physical cores, 8 GiB memory, macOS
`27.0.1`, AC power, Temurin `25.0.4+7-LTS`, JVM bytecode target 21.
Kotlin is `2.4.21`, Gradle `9.8.0`, KSP `2.3.12`,
`kotlinx-benchmark` plugin/runtime `0.5.0`, and JMH `1.37`.
CI uses JDK 21; these scores are not a cross-JDK comparison.

No task-owned builds or other measurements ran concurrently with the measured
sessions. The host was shared, not dedicated: affinity, CPU frequency, thermal
state, and unrelated host load were not controlled. No special fork VM
options were configured. These are host diagnostics, not Android ART,
Android Native, or iOS timing guarantees.

## Profile and retained reports

Every case uses average time in `ns/op`, five warmups, five 300 ms measurement
iterations, one measurement thread, and three independent JVM forks: 15 raw
samples per case. Each report contains all eight case/parameter identities.

First, the GC-profiler task ran in baseline/candidate/candidate/baseline order:

| Order | Runtime | Retained report |
| --- | --- | --- |
| 1 | `0d242ca` | [baseline-a](../../benchmarks/results/holders-2026-10-17/baseline-a/allocations.json) |
| 2 | `ed7218c` | [candidate-a](../../benchmarks/results/holders-2026-10-17/candidate-a/allocations.json) |
| 3 | `ed7218c` | [candidate-b](../../benchmarks/results/holders-2026-10-17/candidate-b/allocations.json) |
| 4 | `0d242ca` | [baseline-b](../../benchmarks/results/holders-2026-10-17/baseline-b/allocations.json) |

The small-case slowdown triggered a second, unprofiled baseline/candidate
comparison using the same warmup/iteration/fork configuration:
[baseline](../../benchmarks/results/holders-2026-10-17/baseline-unprofiled/jvm.json)
and [candidate](../../benchmarks/results/holders-2026-10-17/candidate-unprofiled/jvm.json).
This separates the timing observation from GC-profiler overhead; it does not
eliminate host noise or explain the compiler/runtime cause.

Reports retain JMH environment metadata, reported errors, and all raw samples,
but omit executable paths and VM system properties. Reproduction commands and
retention validation are in the
[measurement guide](../how-to/benchmark-runtime.md#measure-generated-holders).

## Results

The following table uses only the unprofiled comparison. Scores and JMH's
reported errors are in `ns/op`; deltas are candidate minus baseline.

| Case | Count | Baseline score +/- error | Candidate score +/- error | Delta | Change |
| --- | ---: | ---: | ---: | ---: | ---: |
| Fixed holder | 65 | 100.614 +/- 1.921 | 111.248 +/- 0.940 | +10.634 | +10.57% |
| Fixed holder | 4096 | 1740.539 +/- 536.232 | 125.635 +/- 18.692 | -1614.904 | -92.78% |
| Fixed holder | 65536 | 22235.623 +/- 300.435 | 269.034 +/- 3.318 | -21966.589 | -98.79% |
| Variable holder | 65 | 1050.489 +/- 52.576 | 1024.105 +/- 5.871 | -26.384 | -2.51% |
| Variable holder | 4096 | 63836.960 +/- 432.890 | 65055.603 +/- 1044.608 | +1218.643 | +1.91% |
| Variable holder | 65536 | 1040895.348 +/- 12276.545 | 1078144.762 +/- 98746.586 | +37249.414 | +3.58% |
| Scalar holder | - | 26.942 +/- 0.237 | 26.631 +/- 0.348 | -0.311 | -1.15% |
| Allocating control | - | 4.311 +/- 0.056 | 4.358 +/- 0.293 | +0.047 | +1.10% |

The 65,536-element fixed holder is about 83 times faster in this unprofiled
comparison. The two profiled comparisons independently showed 81.82 and
83.80 times faster decoding at that size. Their 4,096-element improvements
were 12.01 and 10.75 times; the unprofiled baseline at that size was noisy.
Do not attribute apparent variable-case improvements to the fixed-only change:
the unchanged 4,096-element variable baseline itself moved substantially
between the two profiled sessions.

The 65-element fixed holder was **11.63% and 21.09% slower** in the profiled
pairs, and **10.57% slower** without profiling. That consistent direction is
a small-case performance finding, not a passed regression gate for that
revision. This report does not waive or raise the 10% budget. The subsequent
[controlled investigation](aligned-count-prefix-benchmark.md) retains this
failure and measures the compatible prefix-reader correction.

### Allocation observations

Candidate profiled means, in JMH `gc.alloc.rate.norm` bytes per operation:

| Case | Count | Candidate-a | Candidate-b |
| --- | ---: | ---: | ---: |
| Fixed holder | 65 | 0.002809 | 0.002756 |
| Fixed holder | 4096 | 0.002802 | 0.003140 |
| Fixed holder | 65536 | 0.006165 | 0.007020 |
| Variable holder | 65536 | 23.214384 | 23.669740 |
| Scalar holder | - | 0.000617 | 0.000669 |
| Allocating control | - | 24.000100 | 24.000098 |

The baseline's slowest variable case was also approximately 23 B/op.
The normalized profiler floor grows with operation duration; these small
fractional values are not literal fractional objects or a zero-allocation
proof. The control's approximately 24 B/op demonstrates observable
allocation. Keep these results separate from the existing standalone
repeat-index allocation budget: generated variable-holder decoding performs
both preflight and index construction. No existing allocation budget changed.

## Correctness and maintenance

Before timing, ordinary generated-holder tests passed on the baseline JVM
and on the candidate JVM and iOS Simulator: 11 tests per executed target.
They cover empty repeats, checkpoint boundaries, the largest measured count,
alternating-input holder reuse, borrowed buffer identity, selected element
contents, truncation without partial holder mutation, and invalid fixture
counts. Initial no-op workload decoders failed behavioral assertions before
the real generated calls were implemented.

The four smoke cases passed, with positive finite scores. The report
validator's 16 tests cover the original runtime suite and the new holder
identities, missing cases, and required three five-sample forks. CI now
executes JVM fixtures/smoke and iOS Simulator fixtures; it deliberately does
not gate cross-machine timing.

The fixture build was forced with:

```bash
./gradlew -p benchmarks/holders jvmTest iosSimulatorArm64Test jvmBenchmarkJar \
  --no-daemon --rerun-tasks --no-build-cache --console=plain
./gradlew -p benchmarks/holders jvmSmokeBenchmark \
  --no-daemon --rerun-tasks --no-build-cache --console=plain
```

The [broader local gates](../ci.md) also passed: runtime JVM 423 tests,
Android host 398, iOS Simulator 403, processor 206, integration two, and
Gradle plugin 45. Runtime/processor/plugin ABI checks and 100% line/branch
Kover gates passed, as did plugin validation and Android AAR assembly.
The plugin tests/coverage ran separately from the multiplatform checks.
Forced Dokka generation produced no committed API-documentation diff.
Regenerated runtime, JVM, processor, and plugin POMs retained their
`ch.trancee.kompact` coordinates and `0.9.0-SNAPSHOT` version.
These are local results, not evidence of a remote run of the revised workflow.

The consumer uses the real code-generation plugin through an included build.
The unpublished root project has a distinct build-only group so it cannot
compete with `:kompact` for composite runtime substitution. Published module
coordinates, public APIs, wire behavior, and supported runtime targets are
unchanged. The consumer declares an iOS Simulator target to provide shared
metadata compilation; no iOS or macOS benchmark timing is registered.

Physical runtime evidence is recorded
[separately](physical-runtime-validation.md). In particular, simulator fixture
tests did not resolve the physical-iOS signing blocker. The later
[physical iOS follow-up](physical-runtime-validation.md#physical-ios-follow-up)
records six passing runtime executions after profile renewal; those are
runtime correctness and bounded GC-probe results, not holder timing.
