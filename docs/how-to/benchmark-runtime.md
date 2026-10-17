# Measure host runtime performance

Use the unpublished `:benchmarks` module to compare caller-owned scalar and
repeat operations on the host JVM and Apple Silicon macOS. It compiles the
maintained common runtime and its JVM/Native platform sources directly; it does
not add a macOS variant or benchmark dependencies to the published runtime.
These diagnostics do not prove Android ART, Android Native, or iOS timing.

The pinned tooling is `kotlinx-benchmark` `0.5.0` and JMH `1.37`.
`kotlinx-benchmark` remains Alpha tooling, isolated from production artifacts.
JMH annotations are kept open for generated benchmark subclasses.

## Check the harness

```bash
python3 -m unittest discover -s benchmarks -p 'test_*.py'
./gradlew :benchmarks:jvmTest :benchmarks:jvmSmokeBenchmark --no-daemon
python3 benchmarks/check_results.py --profile smoke benchmarks/build/reports/benchmarks/smoke/*/jvm.json
```

On an Apple Silicon macOS host, also run:

```bash
./gradlew :benchmarks:macosArm64Test :benchmarks:macosArm64SmokeBenchmark --no-daemon
python3 benchmarks/check_results.py --profile smoke benchmarks/build/reports/benchmarks/smoke/*/macosArm64.json
```

Both CI jobs run their host's fixture tests and finite-result smoke checks.
The benchmark module and its internal Python tools are explicitly excluded
from production coverage, not from correctness checks. Smoke is not a timing
budget: one warmup and one 100 ms sample merely check execution. Native needs
at least one warmup to calibrate a nonzero measured cycle count.

## Record a comparison

Keep the host, power state, toolchain, build configuration, profile, workloads,
and JVM flags unchanged. Avoid concurrent builds or measurements.
The main profile records average nanoseconds per operation with five warmups
and five 300 ms measurement iterations. JVM measurements use three independent
JMH forks; Native uses one release-optimized executable.

```bash
./gradlew :benchmarks:jvmBenchmark :benchmarks:macosArm64Benchmark \
  :benchmarks:jvmAllocationBenchmark --no-daemon --console=plain
```

Timing reports are under
`benchmarks/build/reports/benchmarks/main/<run>/`; the separate JMH GC-profiler
report is `benchmarks/build/reports/benchmarks/allocations.json`.
Validate the exact run and retain its samples before changing the runtime:

```bash
run=<run-directory>
python3 benchmarks/check_results.py --profile main \
  --retain-dir benchmarks/results/baseline \
  "benchmarks/build/reports/benchmarks/main/$run/jvm.json" \
  "benchmarks/build/reports/benchmarks/main/$run/macosArm64.json" \
  benchmarks/build/reports/benchmarks/allocations.json
```

Use a new retention directory for the candidate. The command refuses to
overwrite retained reports. It preserves measurement metadata, primary and
secondary metrics, and raw samples, but excludes local executable paths and
JVM system properties. Record meaningful JVM options separately without
machine-specific or sensitive values. Gradle success alone is insufficient:
the validator rejects missing/duplicate cases, wrong units, missing warmup,
empty samples, nonpositive scores, and non-finite scores or samples.

For a second comparison of only the optimized operation, run
`:benchmarks:jvmFixedBenchmark :benchmarks:macosArm64FixedBenchmark` and
validate with `--profile fixed`. Repeat baseline and candidate in the reverse
order when distinguishing a change from host drift.

## Interpret allocation evidence

Fixtures construct and retain the arrays, cursors, and checkpoint workspaces
outside the timed operation. Each repeat operation resets its cursor, indexes
the complete repeat, checks success, and returns an observable checksum.
Counts are `65`, `4096`, and `65536`; fixed elements use 13 bits. Variable
elements have an 8-bit byte-length prefix and cycle through payload lengths
zero to eight. Scalar controls measure a checked 64-bit read, an unchecked
64-bit write, and an intentional eight-byte array copy.

JMH's `gc.alloc.rate.norm` reports bytes per operation, not an object count or
Android ART behavior. Its background allocation rate can become visible as a
small normalized value for slow variable-repeat operations; retain the rate
and positive control rather than rounding every small value to zero.

The Native test compares three samples of 1024 fixed/variable indexing calls
against a primitive loop and a known-object positive control. It requires no
swept-object increase over that run's primitive baseline and at least 1024
additional swept objects from each positive-control sample. This is a
debug-test GC sweep signal, not a release per-operation allocation count,
an Instruments trace, or a universal allocation guarantee.

## Apply the comparison budget

The [retained host comparison](../research/repeat-index-benchmark.md) records
environment, revisions, samples, and results. For the same host/profile:

- Block a timing regression greater than 10% against the committed baseline,
  including unchanged scalar and variable-repeat controls; rerun or investigate
  noise rather than silently approving it.
- For this JVM workload and GC-profiler profile, require fixed indexing below
  `1 B/op`, variable indexing below `20 B/op`, scalar read/write below
  `0.01 B/op`, and the copy control at least `24 B/op`.
- Native allocation probes must meet their primitive-baseline and
  positive-control thresholds above.

These are bounded diagnostic budgets, not portable public latency guarantees.
Shared heterogeneous CI gates fixture correctness and finite reports, not
cross-machine timing thresholds. Physical-device acceptance remains separate;
see [allocation methodology](../research/allocation-boxing-measurement.md).
