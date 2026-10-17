# Benchmark: fixed-repeat checkpoint work

## Hypothesis and boundary

The baseline uses runtime revision `b068bf9`, before changing the fixed-repeat
checkpoint loop. Benchmark sources, profiles, report validator, and retained
baseline are committed together. The [measurement guide](../how-to/benchmark-runtime.md)
defines the workloads, commands, comparison budgets, and allocation limits.

The hypothesis is that visiting checkpoints directly reduces fixed-indexing
time without changing validation, workspace contents, or allocations. Measure
cursor reset plus complete indexing and its success check; exclude fixture
creation. Block candidate timing regressions above 10% and enforce the guide's
allocation thresholds. Scalar and variable-repeat operations are controls.

## Toolchain and environment

Measurements ran sequentially on 2026-10-17 on an Apple M1 host with eight CPU
cores and 8 GiB RAM, macOS `27.0.1`, AC power, and no thermal warning reported
at the initial environment check. No CPU affinity, frequency lock, or exclusive
host reservation was applied; background load was not isolated.
The shared host is not an isolated performance laboratory. Toolchain:
Gradle `9.8.0`, Kotlin `2.4.21`, OpenJDK `25.0.4+7-LTS`, JVM bytecode target
21, `kotlinx-benchmark` `0.5.0`, JMH `1.37`. CI uses JDK 21; these JDK 25
measurements are not a CI-JDK comparison. No performance-specific JVM flags
were set; inherited encoding/locale properties and executable paths are
omitted from retained reports.

## Profile

Each main-profile record measures average `ns/op`: five warmups and five
300 ms samples, three JVM forks or one release-optimized macOS Native process.
Fixture preparation is outside the timed operation. Measurements include the
cursor reset and success check. Variable-repeat validation remains two-pass
to preserve failure atomicity.

The tasks are `:benchmarks:jvmBenchmark`, `:benchmarks:macosArm64Benchmark`,
and the separate `:benchmarks:jvmAllocationBenchmark`. Repeat sizes are
`65`, `4096`, and `65536`; the guide defines the complete input distribution.
The `fixed` profile includes only `RepeatIndexBenchmark.fixedIndex` and
otherwise retains the same measurement settings.

## Results

### Baseline

Raw samples and aggregate/error metrics are retained under
[`benchmarks/results/baseline/`](../../benchmarks/results/baseline/).

| Operation | Count | JVM ns/op | macOS Native ns/op |
|---|---:|---:|---:|
| Fixed index | 65 | 31.148 | 51.305 |
| Fixed index | 4096 | 1392.929 | 1983.178 |
| Fixed index | 65536 | 22116.589 | 31352.692 |
| Variable index | 65 | 657.013 | 573.745 |
| Variable index | 4096 | 42015.080 | 35628.259 |
| Variable index | 65536 | 676210.079 | 570011.474 |
| Array-copy control | - | 2.854 | 10.974 |
| Scalar read | - | 12.437 | 21.669 |
| Scalar write | - | 14.177 | 27.189 |

The separate JVM GC-profiler run records a `24.000 B/op` array-copy positive
control. Fixed indexing ranges from approximately `0.001` to `0.500 B/op`;
variable indexing from `0.015` to `15.319 B/op`. Both have approximately
`0.022 MB/sec` background allocation and no measured collections. Scalar
read/write are below `0.001 B/op`. These small normalized values are not
evidence of an exact zero-allocation contract.

The debug Native repeat probe records fixed samples `[0, 0, 0]` against
primitive `[0, 0, 0]`, and variable samples `[0, 0, 0]` against primitive
`[1, 0, 0]`. Both positive controls report `[1026, 1026, 1026]` swept objects.
The sample log is retained without the test report's host-identifying metadata.

### Fixed-repeat candidate

The candidate changes only the checkpoint-writing loop in
`KompactCursorRepeats.readFixedRepeat`: it visits `ceil(count / 64)` checkpoints
instead of visiting every element and testing a modulus. Prefix validation,
capacity and end checks still finish before any workspace mutation. Offset
arithmetic uses `Long` intermediates; unused caller-owned checkpoint slots
remain untouched. The variable-repeat implementation is unchanged.

The baseline harness commit is `0d242ca`. Candidate samples under
[`benchmarks/results/candidate/`](../../benchmarks/results/candidate/) were
collected on the same host and with identical profiles/toolchain after this
loop-only runtime change. This report and those samples accompany the change
in version control.

All scores/errors/deltas below are `ns/op`; errors are the runner-reported
values, not confidence intervals for the ratios.

| Target / count | Baseline score/error | Candidate score/error | Delta | Relative change / ratio |
|---|---:|---:|---:|---:|
| JVM / 65 | 31.148 +/- 0.102 | 10.254 +/- 0.098 | -20.894 | -67.08% / 3.04x |
| JVM / 4096 | 1392.929 +/- 17.022 | 23.664 +/- 0.113 | -1369.265 | -98.30% / 58.86x |
| JVM / 65536 | 22116.589 +/- 328.979 | 247.290 +/- 6.141 | -21869.299 | -98.88% / 89.44x |
| Native / 65 | 51.305 +/- 0.607 | 22.201 +/- 1.216 | -29.104 | -56.73% / 2.31x |
| Native / 4096 | 1983.178 +/- 3.148 | 61.259 +/- 9.633 | -1921.919 | -96.91% / 32.37x |
| Native / 65536 | 31352.692 +/- 351.853 | 606.756 +/- 6.145 | -30745.936 | -98.06% / 51.67x |

These ratios describe only this measured workload, not a promised portable
speedup. Unchanged JVM variable/scalar/copy controls ranged from `-0.82%` to
`+4.09%`; Native controls from `-6.30%` to `+0.79%`. Every control stayed within
the 10% regression budget. The separate GC-profiler run's unchanged controls
stayed between `-0.27%` and `+0.59%`.

Candidate JVM fixed indexing is below `0.006 B/op`, variable indexing below
`15.370 B/op`, scalar read/write below `0.001 B/op`, and the copy control at
least `24 B/op`. The fixed path's smaller normalized value is consistent with
its shorter operation and the profiler's unchanged background rate; it is not
a new allocation guarantee. Candidate Native measured/baseline/control sweep
samples exactly match those recorded above. All documented allocation budgets
passed.

## Validation

The focused checkpoint tests passed before and after the refactor. They cover
empty repeats, block edges, large counts, 1/13/64-bit element widths, each
representable count-prefix width, nonzero starts, exact bit bounds,
untouched spare slots, and failure-atomic one-bit truncation.

JVM and Native workload tests passed before timing. Each smoke report contains
five expected cases with finite positive `ns/op` samples. Measured methods
return an observable checksum/value or the allocated copy; caller-owned state
is retained between invocations. JMH's GC profiler and Native sweep controls
provide the bounded allocation evidence above; no assembly profile was taken.

The main comparison ran baseline then candidate. An independent `fixed`
comparison reversed that order: candidate first, then the exact runtime source
from `0d242ca`. Samples are retained under
[`candidate-fixed/`](../../benchmarks/results/candidate-fixed/) and
[`baseline-fixed/`](../../benchmarks/results/baseline-fixed/). JVM speedup
ratios were `3.08x`, `59.45x`, and `91.28x`; Native ratios were `2.80x`,
`38.46x`, and `50.89x`. All candidate cases again beat their baseline.

The repeated Native baseline's 65-element score moved from `51.305` to
`58.312 ns/op` (`+13.7%`) without a runtime change; the repeated Native
candidate's 4096-element score moved from `61.259` to `51.718 ns/op`
(`-15.6%`). This exposes run-to-run noise not captured by within-process
errors. It does not show a candidate regression, but it limits precision
of the ratios. The large-count improvement persisted in both orders.
The final runtime source was restored to the exact validated candidate,
verified by SHA-256
`49ec673f54239fceabff47c33438770e8e6b2c3e39ff5fbcd1b43377d2f70c9f`.

## Conclusion

These are host diagnostics, not Android/iOS device timing, an Instruments
allocation capture, or a portable latency guarantee. The Native GC test is
debug evidence; timing uses an optimized executable. Native's five samples
are not independent process forks. Shared-host drift must be investigated
before accepting a greater-than-10% regression against this baseline,
including unchanged control operations. The evidence supports retaining the
checkpoint-only loop, not promising an exact portable speedup. Toolchain
upgrades require a new comparable baseline rather than reuse of these ratios.
