# Benchmark: fixed-repeat checkpoint work

## Hypothesis and boundary

The baseline uses runtime revision `b068bf9`, from before the change to the
fixed-repeat checkpoint loop. The benchmark sources, profiles, report checker,
and retained baseline were committed together. The
[measurement guide](../how-to/benchmark-runtime.md) defines the workloads,
commands, comparison budgets, and allocation limits.

The hypothesis is that visiting checkpoints directly makes fixed indexing
faster without changing validation, workspace contents, or allocations. The
measured operation is a cursor reset plus complete indexing and its success
check. Fixture creation is outside measurement. A candidate timing regression
above 10% blocks the change, and the guide's allocation thresholds apply.
Scalar and variable-repeat operations are controls.

## Toolchain and environment

Measurements ran one after another on 2026-10-17 on an Apple M1 host with
eight CPU cores, 8 GiB RAM, macOS `27.0.1`, and AC power. The initial
environment check reported no thermal warning. No CPU affinity, frequency
lock, or exclusive host reservation was applied, and background load was not
isolated. The host is shared and is not an isolated performance laboratory.

The tools were Gradle `9.8.0`, Kotlin `2.4.21`, OpenJDK `25.0.4+7-LTS`, JVM
bytecode target 21, `kotlinx-benchmark` `0.5.0`, and JMH `1.37`. CI uses
JDK 21, so these JDK 25 measurements are not a comparison on the CI JDK. No
performance-specific JVM flags were set. The retained reports omit inherited
encoding and locale properties and executable paths.

## Profile

Each `main` profile record measures the average in `ns/op`: five warmups and
five 300 ms samples, with three JVM forks or one release-optimized macOS
Native process. Fixture preparation is outside the timed operation. The
measurement includes the cursor reset and the success check. Variable-repeat
validation stays two-pass to keep failure atomicity.

The tasks are `:benchmarks:jvmBenchmark`, `:benchmarks:macosArm64Benchmark`,
and the separate `:benchmarks:jvmAllocationBenchmark`. Repeat sizes are
`65`, `4096`, and `65536`. The guide defines the complete input distribution.
The `fixed` profile includes only `RepeatIndexBenchmark.fixedIndex` and
otherwise keeps the same measurement settings.

## Results

### Baseline

The raw samples and the aggregate and error metrics are in
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

The separate JVM GC-profiler run records `24.000 B/op` for the array-copy
positive control. Fixed indexing ranges from about `0.001` to `0.500 B/op`,
and variable indexing from `0.015` to `15.319 B/op`. Both show about
`0.022 MB/sec` background allocation and no measured collections. Scalar
read and write are below `0.001 B/op`. These small normalized values do not
prove an exact zero-allocation contract.

The debug Native repeat probe records fixed samples `[0, 0, 0]` against
primitive `[0, 0, 0]`, and variable samples `[0, 0, 0]` against primitive
`[1, 0, 0]`. Both positive controls report `[1026, 1026, 1026]` swept
objects. The retained sample log omits the host-identifying metadata from the
test report.

### Fixed-repeat candidate

The candidate changes only the checkpoint-writing loop in
`KompactCursorRepeats.readFixedRepeat`. It visits `ceil(count / 64)`
checkpoints instead of visiting every element and testing a modulus. Prefix
validation and the capacity and end checks still finish before any workspace
mutation. Offset arithmetic uses `Long` intermediates. Unused caller-owned
checkpoint slots stay untouched. The variable-repeat implementation did not
change.

The baseline benchmark code is at commit `0d242ca`. The candidate samples in
[`benchmarks/results/candidate/`](../../benchmarks/results/candidate/) were
collected on the same host, with the same profiles and toolchain, after this
loop-only runtime change. This note and those samples were committed with the
change. The aligned-count-prefix fix later changed `readFixedRepeat` again.
See [Aligned count prefixes and small generated holders](aligned-count-prefix-benchmark.md).

All scores, errors, and deltas below are in `ns/op`. The errors are the values
the runner reported. They are not confidence intervals for the ratios.

| Target / count | Baseline score/error | Candidate score/error | Delta | Relative change / ratio |
|---|---:|---:|---:|---:|
| JVM / 65 | 31.148 +/- 0.102 | 10.254 +/- 0.098 | -20.894 | -67.08% / 3.04x |
| JVM / 4096 | 1392.929 +/- 17.022 | 23.664 +/- 0.113 | -1369.265 | -98.30% / 58.86x |
| JVM / 65536 | 22116.589 +/- 328.979 | 247.290 +/- 6.141 | -21869.299 | -98.88% / 89.44x |
| Native / 65 | 51.305 +/- 0.607 | 22.201 +/- 1.216 | -29.104 | -56.73% / 2.31x |
| Native / 4096 | 1983.178 +/- 3.148 | 61.259 +/- 9.633 | -1921.919 | -96.91% / 32.37x |
| Native / 65536 | 31352.692 +/- 351.853 | 606.756 +/- 6.145 | -30745.936 | -98.06% / 51.67x |

These ratios describe only this measured workload. They do not promise a
portable speedup. The unchanged JVM variable, scalar, and copy controls ranged
from `-0.82%` to `+4.09%`, and the Native controls from `-6.30%` to `+0.79%`.
Every control stayed within the 10% regression budget. In the separate
GC-profiler run, the unchanged controls stayed between `-0.27%` and `+0.59%`.

Candidate JVM fixed indexing is below `0.006 B/op`, variable indexing below
`15.370 B/op`, and scalar read and write below `0.001 B/op`. The copy control
is at least `24 B/op`. The fixed path's smaller normalized value fits its
shorter operation and the profiler's unchanged background rate. It is not a
new allocation guarantee. The candidate's Native sweep samples for the
measured operations, the primitive baselines, and the controls exactly match
those recorded above. All documented allocation budgets passed.

## Validation

The focused checkpoint tests passed before and after the refactor. They cover
empty repeats, block edges, large counts, element widths of 1, 13, and 64
bits, each representable count-prefix width, nonzero starts, exact bit
bounds, untouched spare slots, and failure-atomic one-bit truncation.

The JVM and Native workload tests passed before timing. Each smoke report
contains five expected cases with finite positive `ns/op` samples. The
measured methods return an observable checksum, a value, or the allocated
copy. Caller-owned state persists between invocations. JMH's GC profiler and
the Native sweep controls give the bounded allocation evidence above. No
assembly profile was taken.

The main comparison ran the baseline first, then the candidate. A separate
`fixed` comparison reversed that order: the candidate first, then the exact
runtime source from `0d242ca`. The samples are in
[`candidate-fixed/`](../../benchmarks/results/candidate-fixed/) and
[`baseline-fixed/`](../../benchmarks/results/baseline-fixed/). JVM speedup
ratios were `3.08x`, `59.45x`, and `91.28x`. Native ratios were `2.80x`,
`38.46x`, and `50.89x`. Every candidate case again beat its baseline.

In the repeated Native baseline, the 65-element score moved from `51.305` to
`58.312 ns/op` (`+13.7%`) without a runtime change. In the repeated Native
candidate, the 4096-element score moved from `61.259` to `51.718 ns/op`
(`-15.6%`). These moves show run-to-run noise that the errors within one
process do not capture. They do not show a candidate regression, but they
limit how precise the ratios are. The large-count improvement held in both
orders. The final runtime source was restored to the exact validated
candidate and verified by SHA-256
`49ec673f54239fceabff47c33438770e8e6b2c3e39ff5fbcd1b43377d2f70c9f`.

## Conclusion

These are host diagnostics. They are not Android or iOS device timing, an
Instruments allocation capture, or a portable latency guarantee. The Native
GC test is debug evidence, and the timing uses an optimized executable.
Native's five samples come from one process, not from independent forks.
Before you accept a regression above 10% against this baseline, including in
an unchanged control operation, investigate shared-host drift. The evidence
supports keeping the checkpoint-only loop. It does not support promising an
exact portable speedup. After a toolchain upgrade, record a new comparable
baseline instead of reusing these ratios.
