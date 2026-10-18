# How to measure runtime performance on a host

Use the unpublished `:benchmarks` module to time scalar and repeat operations
on the host JVM and on Apple Silicon macOS. The results do not show Android
ART, Android Native, or iOS timing. For what the module measures and why, see
[Design and trade-offs](../architecture.md) and the
[repeat index comparison](../research/repeat-index-benchmark.md).

The module uses `kotlinx-benchmark` `0.5.0`, which is Alpha, and JMH `1.37`.
Neither is a dependency of the published runtime.

## Check that the benchmarks run

Run the Python tests, the JVM fixture tests, and the JVM smoke run. Then check
the smoke report:

```bash
python3 -m unittest discover -s benchmarks -p 'test_*.py'
./gradlew :benchmarks:jvmTest :benchmarks:jvmSmokeBenchmark --no-daemon
python3 benchmarks/check_results.py --profile smoke benchmarks/build/reports/benchmarks/smoke/*/jvm.json
```

On an Apple Silicon Mac, also run the Native tests and smoke run:

```bash
./gradlew :benchmarks:macosArm64Test :benchmarks:macosArm64SmokeBenchmark --no-daemon
python3 benchmarks/check_results.py --profile smoke benchmarks/build/reports/benchmarks/smoke/*/macosArm64.json
```

The smoke profile runs one warmup and one 100 ms sample. It only checks that
each benchmark runs and reports finite numbers. Do not compare smoke timings.

## Record a comparison

1. Keep the host, power state, toolchain, build configuration, workloads, and
   JVM flags the same for both runs. Do not run other builds or measurements
   at the same time.

2. Run the main profile and the JVM allocation profile:

   ```bash
   ./gradlew :benchmarks:jvmBenchmark :benchmarks:macosArm64Benchmark \
     :benchmarks:jvmAllocationBenchmark --no-daemon --console=plain
   ```

   The main profile runs five warmups and five 300 ms measurements, and
   reports average nanoseconds per operation. The JVM uses three JMH forks.
   Native uses one release build. Timing reports go to
   `benchmarks/build/reports/benchmarks/main/<run>/`. The allocation report
   goes to `benchmarks/build/reports/benchmarks/allocations.json`.

3. Check the run and keep its samples before you change the runtime:

   ```bash
   run=<run-directory>
   python3 benchmarks/check_results.py --profile main \
     --retain-dir benchmarks/results/baseline \
     "benchmarks/build/reports/benchmarks/main/$run/jvm.json" \
     "benchmarks/build/reports/benchmarks/main/$run/macosArm64.json" \
     benchmarks/build/reports/benchmarks/allocations.json
   ```

   The checker fails on missing or duplicate cases, wrong units, missing
   warmups, empty samples, and scores that are not positive and finite. A
   successful Gradle run alone does not prove that the report is complete.

4. Change the runtime, repeat steps 2 and 3, and pass a new directory, such as
   `benchmarks/results/candidate`, to `--retain-dir`. The checker refuses to
   overwrite a directory.

The retained reports keep the metadata, metrics, and raw samples. They drop
local executable paths and JVM system properties. Write down any JVM options
that matter, leaving out machine-specific or sensitive values.

To separate a real change from host drift, run the baseline and candidate a
second time in reverse order.

To time only the fixed-width repeat index, run
`:benchmarks:jvmFixedBenchmark :benchmarks:macosArm64FixedBenchmark` and check
the reports with `--profile fixed`.

To time nested-region writes, run `:benchmarks:jvmWriterBenchmark`. The `main`
and `smoke` profiles leave out `WriterBenchmark`. See the
[writer nesting note](../research/writer-nested-benchmark.md).

### Automate the timing comparison

Pass the retained baseline with `--baseline`. The command exits non-zero if
any case's mean time grows by more than 10%. Exactly 10% passes.

```bash
python3 benchmarks/check_results.py --profile main \
  --baseline benchmarks/results/baseline/jvm.json \
  benchmarks/results/candidate/jvm.json
```

- Compare the JVM, Native, and allocation reports as separate pairs.
- Use `--profile main` for the `:benchmarks` reports and
  `--profile holders-main` for generated-holder reports.
- Both reports need matching metadata, parameters, and metric sets, five
  warmups, and five measurements. JVM reports need three five-sample forks.
  Native reports need one five-sample release run.
- The command rejects smoke reports and incomplete reports.

The command compares means only. It does not compare confidence intervals or
allocations, and it cannot see JVM arguments or the host's power state. Check
those yourself. If the comparison fails, investigate and repeat the runs in
reverse order. Do not raise the 10% budget.

## Measure generated holders

The separate `benchmarks/holders` build times the generated `decodeInto`
functions on the JVM. It builds the runtime and the code generator from this
repository.

1. Run the fixture tests and the smoke run, then check the smoke report:

   ```bash
   ./gradlew -p benchmarks/holders jvmTest jvmSmokeBenchmark --no-daemon
   python3 benchmarks/check_results.py --profile holders-smoke \
     benchmarks/holders/build/reports/benchmarks/smoke/*/jvm.json
   ```

   The smoke run has four cases: scalar decode, fixed-repeat decode,
   variable-repeat decode, and an allocating control.

2. Run the allocation profile and keep the report:

   ```bash
   ./gradlew -p benchmarks/holders jvmAllocationBenchmark --no-daemon --console=plain
   python3 benchmarks/check_results.py --profile holders-main \
     --retain-dir benchmarks/results/<comparison-run> \
     benchmarks/holders/build/reports/benchmarks/allocations.json
   ```

   This run uses the main profile settings and has eight cases. The two repeat
   benchmarks run with counts `65`, `4096`, and `65536`.

3. For timing without the allocation profiler, run
   `./gradlew -p benchmarks/holders jvmBenchmark --no-daemon`. Check
   `benchmarks/holders/build/reports/benchmarks/main/<run>/jvm.json` with
   `--profile holders-main`.

4. On a Mac, also run the fixture tests on the iOS Simulator:

   ```bash
   ./gradlew -p benchmarks/holders iosSimulatorArm64Test --no-daemon
   ```

For the workloads, baseline, and limitations, see the
[holder comparison](../research/generated-holder-benchmark.md) and the
[aligned-prefix follow-up](../research/aligned-count-prefix-benchmark.md).

## Check the allocation results

JMH reports `gc.alloc.rate.norm` in bytes per operation. That number is not an
object count and says nothing about Android ART. Slow variable-repeat cases can
show a small non-zero value from background allocation. Keep that value and
the control result instead of rounding it to zero.

For the `:benchmarks` JVM allocation profile on the same host, check these
limits:

| Case | Limit |
| --- | --- |
| Fixed-width repeat indexing | below `1 B/op` |
| Variable-width repeat indexing | below `20 B/op` |
| Scalar read and write | below `0.01 B/op` |
| Allocating copy control | at least `24 B/op` |

`:benchmarks:macosArm64Test` also runs a Native allocation probe. It takes
three samples of 1024 repeat-index calls and compares them with a primitive
loop and an allocating control. The test fails if the indexing calls sweep
more objects than the primitive loop, or if a control sample sweeps fewer than
1024 extra objects. This is a debug-test signal, not a per-operation count.

## Apply the comparison budget

For the same host and profile:

- Investigate any timing regression greater than 10% against the committed
  baseline, including the unchanged scalar and variable-repeat cases. Do not
  approve it without a rerun.
- Keep the JVM allocation results within the limits in the table above.
- Keep the Native allocation probe passing.

These limits are host diagnostics, not public latency guarantees. CI checks
fixture correctness and smoke reports only. It does not enforce timing across
machines. For device measurements, see the
[allocation methodology](../research/allocation-boxing-measurement.md).
