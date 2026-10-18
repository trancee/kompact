# Generated-holder decoding: initial host comparison

## Question and operation boundary

Does the sparse fixed-repeat checkpoint optimization still help inside a
complete generated holder's `decodeInto`? That call also runs frame
preflight, scalar decoding, borrowed ranges, and repeat index binding.

The baseline runtime is `0d242ca`. The candidate is merged `ed7218c` from
[#99](https://github.com/trancee/kompact/pull/99). Their maintained common
runtime sources differ only in `KompactCursorRepeats.kt`. This experiment
changed no production runtime or generator source.

Both revisions ran the same new `benchmarks/holders` consumer. Before
measurement, its sources and the generated decoder sources were compared byte
for byte. The
[source hashes](../../benchmarks/results/holders-2026-10-17/source-sha256.txt)
identify the benchmark code, uncommitted at the time, that all six runs used.

Each measured decode alternates between two prepared messages. It resets a
caller-owned cursor, calls the real generated holder decoder, checks for
success, and returns a primitive checksum. Message construction, the
allocation of holders, ranges, and cursors, and checkpoint storage allocation
are outside measurement. The fixed and variable messages contain a scalar
header and a borrowed 128-byte blob. Fixed elements are 16-bit scalars.
Variable elements have 8-bit byte-length prefixes, and their payload lengths
cycle from zero to eight. The counts are `65`, `4096`, and `65536`.

The benchmark measures complete holder binding. It does not measure
materializing every repeated value, copying blobs, converting strings,
application I/O, or a production latency distribution. Separate tests consume
elements across checkpoint boundaries. The allocating control returns a new
eight-byte array.

The comparison threshold is the repository's 10% regression budget. The
hypothesis was an improvement of more than 10% for large fixed repeats,
without a regression of more than 10% in the other maintained cases. The
small fixed-repeat result below does not meet that condition. This note is
the historical measurement. The
[aligned-prefix follow-up](aligned-count-prefix-benchmark.md) fixes that
finding without changing the budget.

## Toolchain and environment

Measured on 2026-10-17 on an Apple M1 with eight physical cores, 8 GiB memory,
macOS `27.0.1`, AC power, Temurin `25.0.4+7-LTS`, and JVM bytecode target 21.
The tools were Kotlin `2.4.21`, Gradle `9.8.0`, KSP `2.3.12`,
`kotlinx-benchmark` plugin and runtime `0.5.0`, and JMH `1.37`.
CI uses JDK 21. These scores are not a comparison across JDKs.

No builds or other measurements from this task ran during the measured
sessions. The host was shared, not dedicated. Nobody controlled affinity, CPU
frequency, thermal state, or unrelated host load. No special fork VM options
were set. The results are host diagnostics. They are not timing guarantees for
Android ART, Android Native, or iOS.

## Profile and retained reports

Every case uses average time in `ns/op`, five warmups, five 300 ms
measurement iterations, one measurement thread, and three independent JVM
forks. That gives 15 raw samples per case. Each report contains all eight
combinations of case and parameter.

First, the GC-profiler task ran in the order baseline, candidate, candidate,
baseline:

| Order | Runtime | Retained report |
| --- | --- | --- |
| 1 | `0d242ca` | [baseline-a](../../benchmarks/results/holders-2026-10-17/baseline-a/allocations.json) |
| 2 | `ed7218c` | [candidate-a](../../benchmarks/results/holders-2026-10-17/candidate-a/allocations.json) |
| 3 | `ed7218c` | [candidate-b](../../benchmarks/results/holders-2026-10-17/candidate-b/allocations.json) |
| 4 | `0d242ca` | [baseline-b](../../benchmarks/results/holders-2026-10-17/baseline-b/allocations.json) |

The small-case slowdown led to a second comparison, unprofiled, with the
same warmup, iteration, and fork settings:
[baseline](../../benchmarks/results/holders-2026-10-17/baseline-unprofiled/jvm.json)
and [candidate](../../benchmarks/results/holders-2026-10-17/candidate-unprofiled/jvm.json).
This comparison separates the timing result from GC-profiler overhead. It
does not remove host noise, and it does not explain the compiler or runtime
cause.

The reports keep JMH environment metadata, reported errors, and all raw
samples. They omit executable paths and VM system properties. The
[measurement guide](../how-to/benchmark-runtime.md#measure-generated-holders)
gives the commands to reproduce and validate the reports.

## Results

The following table uses only the unprofiled comparison. Scores and JMH's
reported errors are in `ns/op`. Each delta is the candidate minus the baseline.

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

In this unprofiled comparison, the 65,536-element fixed holder is about
83 times faster. The two profiled comparisons showed decoding 81.82 and
83.80 times faster at that size. At 4,096 elements they showed 12.01 and
10.75 times faster. The unprofiled baseline at that size was noisy.
Do not credit the apparent variable-case improvements to the change, which
touched only fixed repeats. The unchanged 4,096-element variable baseline
itself moved a lot between the two profiled sessions.

The 65-element fixed holder was 11.63% and 21.09% slower in the profiled
pairs, and 10.57% slower without profiling. All three runs point the same way.
That makes it a real small-case performance finding, and that revision did
not pass the regression budget. This note does not waive or raise the 10%
budget. The later
[controlled investigation](aligned-count-prefix-benchmark.md) keeps this
failure on record and measures the compatible prefix-reader fix.

### Allocation observations

The following table shows the candidate's profiled means, in JMH
`gc.alloc.rate.norm` bytes per operation:

| Case | Count | Candidate-a | Candidate-b |
| --- | ---: | ---: | ---: |
| Fixed holder | 65 | 0.002809 | 0.002756 |
| Fixed holder | 4096 | 0.002802 | 0.003140 |
| Fixed holder | 65536 | 0.006165 | 0.007020 |
| Variable holder | 65536 | 23.214384 | 23.669740 |
| Scalar holder | - | 0.000617 | 0.000669 |
| Allocating control | - | 24.000100 | 24.000098 |

The baseline's slowest variable case was also about 23 B/op.
The normalized profiler floor grows with operation duration. These small
fractional values are not fractional objects, and they do not prove zero
allocation. The control's value of about 24 B/op shows that the profiler sees
real allocation. Keep these results apart from the existing standalone
repeat-index allocation budget. Generated variable-holder decoding runs both
preflight and index construction. No existing allocation budget changed.

## Correctness and maintenance

Before timing, the ordinary generated-holder tests passed on the baseline JVM
and on the candidate JVM and iOS Simulator: 11 tests per executed target.
They cover empty repeats, checkpoint boundaries, the largest measured count,
reuse of a holder across alternating inputs, borrowed buffer identity,
selected element contents, truncation without partial holder mutation, and
invalid fixture counts. The first workload decoders did nothing, and they
failed the behavior assertions. The real generated calls came after that.

The four smoke cases passed with positive finite scores. The report checker,
`benchmarks/check_results.py`, had 16 tests at the time. They cover the
original runtime suite and the new holder cases, missing cases, and the
required three five-sample forks. CI runs the JVM holder fixtures and smoke
benchmark and the iOS Simulator holder fixtures. CI does not gate timing,
because timing differs between machines.

The fixture build was forced with:

```bash
./gradlew -p benchmarks/holders jvmTest iosSimulatorArm64Test jvmBenchmarkJar \
  --no-daemon --rerun-tasks --no-build-cache --console=plain
./gradlew -p benchmarks/holders jvmSmokeBenchmark \
  --no-daemon --rerun-tasks --no-build-cache --console=plain
```

The [broader local gates](../ci.md) also passed at measurement time. Test
counts were runtime JVM 423, Android host 398, iOS Simulator 403,
processor 206, integration two, and Gradle plugin 45. The ABI checks for the
runtime, processor, and plugin passed, as did the 100% line and branch Kover
gates, plugin validation, and Android AAR assembly. The plugin tests and
coverage ran separately from the multiplatform checks. Forced Dokka
generation produced no committed API documentation diff. The regenerated
runtime, JVM, processor, and plugin POMs kept their `ch.trancee.kompact`
coordinates and the project version at the time, `0.9.0-SNAPSHOT`. The
project version is now `0.10.0-SNAPSHOT`. These are local results. They do
not show a remote run of the revised workflow.

The consumer uses the real code-generation plugin through an included build.
The unpublished root project uses a separate group, `ch.trancee.kompact.build`,
so Gradle cannot pick it instead of `:kompact` when it substitutes the runtime
in the composite build. Published module coordinates, public APIs, wire
behavior, and supported runtime targets did not change. The consumer declares
an iOS Simulator target to get shared metadata compilation. It registers no
iOS or macOS benchmark timing.

[Physical runtime validation records](physical-runtime-validation.md) keeps
the physical runtime evidence. Simulator fixture tests did not resolve the
physical-iOS signing blocker. The later
[physical iOS follow-up](physical-runtime-validation.md#physical-ios-follow-up)
records six passing runtime executions after the profile renewal. Those are
runtime correctness and bounded GC-probe results, not holder timing.
