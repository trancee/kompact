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

### Automate the timing comparison

After retaining comparable full reports, use `--baseline` to reject a mean
timing regression greater than 10%, including unchanged controls:

```bash
python3 benchmarks/check_results.py --profile main \
  --baseline benchmarks/results/baseline/jvm.json \
  benchmarks/results/candidate/jvm.json
```

Use `holders-main` for generated-holder reports, or `main` for the complete
release Native reports. Compare each JVM, Native, and GC-profiler pair
separately. The command requires matching runtime/profile metadata,
parameters, and profiler metric sets, five warmups and measurements, and
three five-sample JVM forks or one five-sample release Native run.
It rejects smoke reports, incomplete reports, and incompatible environments
with a nonzero exit status. Exactly 10% is permitted.

This check compares timing means, not confidence intervals or allocation
budgets. Inspect allocation metrics against the bounds below separately.
Retained reports omit JVM arguments and machine paths: you must still verify
identical host/power conditions, source workloads, build toolchains, and
meaningful VM flags. Do not use a passing comparison to excuse host drift or
a changed workload. Investigate a failure and repeat in reverse order rather
than raising the budget. Without `--baseline`, validation remains an
execution/shape check; shared CI does not enforce cross-machine timing.

## Measure generated holders

The standalone `benchmarks/holders` build consumes the runtime and real code
generator through a composite build. It does not copy generated decoders or
compile duplicate runtime classes. Its second target, `iosSimulatorArm64`,
provides the shared metadata compilation required by code generation and runs
the same correctness fixtures on macOS. Only JVM timing is registered.

```bash
./gradlew -p benchmarks/holders jvmTest jvmSmokeBenchmark --no-daemon
python3 benchmarks/check_results.py --profile holders-smoke \
  benchmarks/holders/build/reports/benchmarks/smoke/*/jvm.json
```

There are four smoke cases: scalar decoding, fixed-repeat decoding,
variable-repeat decoding, and an allocating control. The full profile has eight
cases: the two repeats use counts `65`, `4096`, and `65536`; scalar decoding and
the control have no count parameter.

Setup prepares two alternating messages, holders, cursors, ranges, and sparse
checkpoint storage. Each decoding invocation resets the cursor, calls the
generated `decodeInto`, checks success, and returns a primitive checksum.
The repeated schemas contain a scalar header, a 128-byte borrowed blob, and
either 16-bit scalar elements or blobs with 8-bit length prefixes and payload
lengths cycling from zero to eight. Complete frame validation and index binding
are timed; materializing every repeated element or copying the blob is not.
The separate control returns a new eight-byte array.

```bash
./gradlew -p benchmarks/holders jvmBenchmarkJar --no-daemon
./gradlew -p benchmarks/holders jvmAllocationBenchmark --no-daemon --console=plain
python3 benchmarks/check_results.py --profile holders-main \
  --retain-dir benchmarks/results/<comparison-run> \
  benchmarks/holders/build/reports/benchmarks/allocations.json
```

The GC-profiler command uses the main profile's five warmups, five 300 ms
measurements, and three forks. Its report validator requires all eight cases
and five raw samples from each fork. For unprofiled timing, use
`./gradlew -p benchmarks/holders jvmBenchmark --no-daemon` and validate that
run's `build/reports/benchmarks/main/<run>/jvm.json` with `holders-main`.

On macOS, run the shared fixtures separately:

```bash
./gradlew -p benchmarks/holders iosSimulatorArm64Test --no-daemon
```

CI runs the JVM fixtures/smoke and iOS Simulator fixtures, not a timing budget.
The [holder comparison](../research/generated-holder-benchmark.md) records
the initial baseline, candidate, allocation bounds, environment, and limitations.
The [aligned-prefix follow-up](../research/aligned-count-prefix-benchmark.md)
records the small-holder fix and its JVM/Native tradeoffs.

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
