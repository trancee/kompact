# Host repeat-index benchmark

## Method and environment

The baseline uses runtime revision `b068bf9`, before changing the fixed-repeat
checkpoint loop. Benchmark sources, profiles, report validator, and retained
baseline are committed together. The [measurement guide](../how-to/benchmark-runtime.md)
defines the workloads, commands, comparison budgets, and allocation limits.

Measurements ran sequentially on 2026-10-17 on an Apple M1 host with eight CPU
cores and 8 GiB RAM, macOS `27.0.1`, AC power, and no reported thermal warning.
The shared host is not an isolated performance laboratory. Toolchain:
Gradle `9.8.0`, Kotlin `2.4.21`, OpenJDK `25.0.4+7-LTS`, JVM bytecode target
21, `kotlinx-benchmark` `0.5.0`, JMH `1.37`. CI uses JDK 21; these JDK 25
measurements are not a CI-JDK comparison. No performance-specific JVM flags
were set; inherited encoding/locale properties and executable paths are
omitted from retained reports.

Each main-profile record measures average `ns/op`: five warmups and five
300 ms samples, three JVM forks or one release-optimized macOS Native process.
Fixture preparation is outside the timed operation. Measurements include the
cursor reset and success check. Variable-repeat validation remains two-pass
to preserve failure atomicity.

## Baseline

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

## Interpretation limits

These are host diagnostics, not Android/iOS device timing, an Instruments
allocation capture, or a portable latency guarantee. The Native GC test is
debug evidence; timing uses an optimized executable. Native's five samples
are not independent process forks. Shared-host drift must be investigated
before accepting a greater-than-10% regression against this baseline,
including unchanged control operations.
