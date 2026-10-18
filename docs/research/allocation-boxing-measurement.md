# Allocation and boxing measurement on Android and iOS

## Question

How can Kompact detect allocations, Kotlin value-class boxing, and the cost of
direct bit operations for scalar reads and in-place writes on Android, the
JVM, and Kotlin/Native iOS? Which tools, controls, and evidence can support
numeric performance budgets?

## Conclusion

Kompact cannot promise that a value class is allocation-free in every call
shape. Kotlin boxes value classes at generic, interface, and nullable
boundaries. The performance contract must name direct, statically typed calls
over a caller-owned `ByteArray` and test the call shapes that box separately.

Use three measurement layers:

1. Android acceptance measurements with AndroidX Microbenchmark 1.4.1 on a
   dedicated physical device. It measures timing and allocation counts and
   writes machine-readable JSON.
2. Host JVM diagnostics with kotlinx-benchmark and the JMH GC profiler. This
   layer catches JVM boxing and reports normalized allocated bytes per
   operation. It does not prove Android ART behavior.
3. Kotlin/Native iOS acceptance with a release `iosArm64` benchmark executable
   that runs the measured loop inside Kotlin. Measure timing without
   Instruments. Then run a separate Xcode Instruments Allocations capture for
   allocation count, bytes, and call stacks.

Test-only probes based on Kotlin/Native GC sweep statistics now separate
selected measured loops from primitive baselines. On physical Android and iOS
devices, they also detect known allocation controls, in both debug and
release-optimized test binaries. This is bounded test evidence, not a general
allocation guarantee.

The iOS simulator is useful for repeatable diagnostics and functional smoke
runs. The recorded physical-device probes include no timing benchmarks and no
Instruments Allocations capture, and no physical-device timing budget is set.

Device timing is still a measurement plan. This note records the bounded
device allocation probes below. The separate
[host repeat and scalar measurements](repeat-index-benchmark.md) keep JVM and
macOS Native timing and allocation evidence with host-specific diagnostic
budgets. They do not establish Android or iOS timing.

## Current proof status

The checked caller-owned cursor (`KompactCursor`) and the generated holder
APIs exist. Test-only probes on Android Native and iOS Native compare these
operations with primitive baselines and with intentional-allocation controls:

- direct generated `speed` reads and writes;
- cursor byte reads and writes;
- cursor unsigned validation.

The physical-device results below cover debug and release-optimized test
binaries. They are limited to the exact operations, iteration count, and
runtime. Compilation, functional tests, and 100% line and branch coverage do
not prove zero allocations per operation.

The [2026-10-17 runtime validation](physical-runtime-validation.md) records
three debug and three release Android runs of merged `ed7218c`. Each run
passed 403 tests with the existing controlled probes. The aligned-prefix
follow-up adds six Android executions with 404 tests each. The
[2026-10-18 physical iOS follow-up](physical-runtime-validation.md#physical-ios-follow-up)
records three debug and three release executions of merged `465cb86`, with
404 tests each, run under LLDB after the dedicated provisioning profile was
renewed. All five scalar and cursor probes again reported zero swept objects
against zero primitive baselines, with `[4098, 4098, 4098]` positive controls.
The older iOS results below remain historical evidence. Separate
[generated-holder measurements](generated-holder-benchmark.md) cover complete
borrowed holder binding on the host JVM, not device timing.

On 2026-10-06, the available host was Apple Silicon macOS 27.0 with Xcode
27.0 and a connected Android 15 API 35 arm64 device. A plain Native behavior
test pass is not allocation evidence on its own. The sections below record the
controlled probe results separately.

### iOS GC sweep-statistics probe

The allocation probes live in the shared `nativeTest` source set
(`NativeAllocationCounterProbeTest` and `NativeAllocationCursorProbeTest`), so
they run on Android Native, the iOS Simulator, and physical iOS.

On 2026-10-07, the `iosArm64` debug test binary was built locally from the
working tree on top of commit `5919dbe` (SHA-256
`621e32dddfc62a49e5f95322c02b472c183fc2e77d1e479f25801bfa999b6623`, Kotlin
`2.4.20`). `scripts/test-ios-device.sh` ran it three times on an iPhone SE
(2020) with iOS `18.7.8` (Xcode `27.0`, `27A266a`). Every run passed all 396
tests in 37 test cases.

The known-object probe reported `keptCount=6906` and `sweptCount=2289`. Each
measured probe ran three samples of 4,096 operations: direct generated `speed`
reads and writes, cursor byte reads, unsigned validations, and cursor byte
writes. Each reported `[0, 0, 0]` swept objects, which matched the primitive
baselines. Every intentional-allocation control reported `[4098, 4098, 4098]`.
The local iOS Simulator run of the same probes reported identical measured and
control counts.

The limits of the Android result apply here too. This is a debug test binary,
GC statistics are testing and debugging data, and no Instruments Allocations
capture was made.

### Release-optimized Native GC sweep-statistics probe

`kompact/build.gradle.kts` also links release-optimized test executables, so
the same `nativeTest` probes run with compiler optimizations turned on. The
link tasks are `linkReleaseTestIosArm64`, `linkReleaseTestIosSimulatorArm64`,
and `linkReleaseTestAndroidNativeArm64`, and the output is
`releaseTest/test.kexe`. The build skips these binaries when you pass
`-PmutationTest.jvmOnly=true`.

On 2026-10-07, the `iosArm64` release binary (SHA-256
`932cbb17b83bbe0ec0cf5227894cb4addeb2167d0f1f3a27582495ff1139bc66`) ran three
times through `scripts/test-ios-device.sh` on the iPhone SE (2020) with iOS
`18.7.8`. Each run passed all 396 tests. Every measured probe reported
`[0, 0, 0]` swept objects, which matched the primitive baselines. Every
intentional-allocation control reported `[4098, 4098, 4098]`, so the optimizer
did not hide the controls' allocations. The `iosSimulatorArm64` release binary
reported identical counts.

The `androidNativeArm64` release binary came from Linux CI run `37639871425`
(commit `50768be`, artifact `kompact-android-native-arm64-release-test`,
SHA-256 `f1df293a5b0b5a5017f09a312330eb57dc637bda4399028ea6ca933adbe931b3`).
It ran three times through `scripts/test-android-native-device.sh` on the
Android 15 arm64 device. Each run passed all 396 tests and removed its
temporary files from the device. Measured probes reported `[0, 0, 0]` and
controls reported `[4098, 4098, 4098]`, which matched the iOS release results.

These release results have the same limits. GC statistics are testing and
debugging data. The results cover only the measured call shapes. No
Instruments trace or other independent allocation trace exists, and no timing
budget is set.

### Android Native allocation-counter feasibility

The Android 15 device is a production `user` build with Perfetto v46 and the
`android.heapprofd` data source available. Heapprofd is not a validated
per-operation counter for Kompact's Android Native target, for these reasons:

- Perfetto's [heapprofd documentation](https://perfetto.dev/docs/data-sources/native-heap-profiler)
  describes sampled `malloc` and `free` activity, and `new` and `delete`
  activity. Kotlin/Native's default allocator suballocates objects from
  pages. A single object allocation therefore need not call the system
  allocator and need not appear as a heap-profiler event.
- On Android `user` builds, Perfetto documents profiling only for debuggable
  or profileable Java apps. The current `test.kexe` is a standalone Native
  test executable that runs as the ADB shell user, not such an app.
- Kotlin/Native [`GC.lastGCInfo()`](https://kotlinlang.org/docs/native-memory-manager.html#check-for-memory-leaks)
  reports statistics from the last completed collection and is intended for
  testing and debugging. Retained-heap values alone do not count temporary
  objects. Kotlin 2.4.20's
  [`GCInfo`](https://github.com/JetBrains/kotlin/blob/v2.4.20/kotlin-native/runtime/src/main/kotlin/kotlin/native/runtime/GCInfo.kt)
  also exposes per-collection `sweepStatistics` with `sweptCount` and
  `keptCount`. A physical-device test detected both retained and released
  known objects and separated the measured loops from a primitive baseline.
  This testing and debugging API is still a bounded signal, not a stable
  production allocation contract.
- [AndroidX Microbenchmark](https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview)
  reports Android runtime (ART) behavior. It does not instrument the separate
  Kotlin/Native executable.

On 2026-10-07, the Linux CI artifact for commit
`ef97610b39caf6380210c288dcce190d49abcb14` (run `37596537995`, SHA-256
`f84d76040d63a4410bd393fbc3a68f2e9ea58f96df0ccf8bd2d616ba087b9c32`) ran three
times on the Android 15 arm64 device (model `A063`, API 35, build
`AQ3A.240929.001`). All runs passed 390 tests in 36 test cases. Each run
reported `keptCount=6031` and `sweptCount=2288` for the known-object probe.

Each run took three samples of 4,096 operations. Direct generated `speed`
reads and writes each reported `[0, 0, 0]` swept objects, exactly matching the
primitive baseline `[0, 0, 0]`. The 4,096-instance intentional-allocation
control reported `[4098, 4098, 4098]` for both operations. The tests require
that measured counts do not exceed the primitive baseline, with no allowance
expressed as a percentage of the control. The runner verified the artifact
checksum on the device and removed its temporary files.

### Caller-owned cursor unsigned write investigation

The first Android Native cursor probe for commit `be87a93` found that 4,096
`writeUnsigned(ULong)` calls swept 4,096 objects per sample. Cursor byte reads
and `writeUnsigned(Long)` matched the primitive baseline.

Further isolation on `c245943` showed that `validateUnsigned(ULong)` alone
swept one object per validation. Conversion, raw `Long` writes, and full-width
validation did not. The attempt to inline validation in `bf0026c` did not
remove the allocation. A follow-up probe on `ab95a77` traced the allocation to
the narrow-width `ULong` right-shift expression. The range check and the
precomputed `ULong` write each swept one object per operation.

Commit `5989ac2` changed the shared validator and writer to run the same range
check through `value.toLong() ushr bitWidth`. `KompactCursor` still uses that
expression. The Linux CI artifact for that commit (run `37612656077`, SHA-256
`7d26ba4543dddbead1b72e77e761c59cd8bfb672d79f870bfa295f0426139551`) ran three
times on the Android 15 arm64 device (model `A063`, API 35, build
`AQ3A.240929.001`; macOS `27.0.0` arm64; ADB `37.0.1-15733141`). Every run
passed all 396 tests. In all three runs, the 4,096-operation unsigned
validation probe and the cursor byte-write probe each reported `[0, 0, 0]`
swept objects, which matched their primitive baselines. The
intentional-allocation controls each reported `[4098, 4098, 4098]`. The device
runner verified the artifact checksum and removed its temporary files after
each run.

This is bounded evidence for these operations in the exact Kotlin/Native debug
test binary and environment. The debug result alone does not establish
release-optimized behavior, a general per-operation allocation guarantee, or a
numeric performance budget. The release results are recorded separately above.

The probes show two things. The GC sweep statistics in the test binary detect
the known allocation controls. The measured loops for the direct getter and
writer, unsigned validation, and cursor byte writes produced no detectable
swept-object delta in the debug and release-optimized test binaries.

The probes do not establish a timing budget, behavior for other operations or
call shapes, or a universal zero-allocation guarantee. The counter is
testing and debugging data and may change across compiler or runtime versions.
Do not treat an empty Perfetto profile, an unchanged retained heap, or a
passing Native device test on its own as zero-allocation evidence. The
release-optimized results for both Native targets are recorded above. An
independent Instruments trace, or an equivalent allocation trace, remains
optional and has not been made.

## Checked variable-repeat tradeoff

Generated framed-holder decoding preflights the complete operation before it
changes holder state. For variable-width repeats, the preflight validates
element boundaries and workspace capacity without binding the workspace. The
decode pass then walks those elements again to fill the caller-owned sparse
index. This is a deliberate tradeoff for failure atomicity, not a measured
performance result.

Measure representative repeat sizes on each target before you set or claim a
performance budget. The [host benchmark report](repeat-index-benchmark.md)
keeps the first JVM and macOS Native repeat and scalar baselines, and the
[measurement guide](../how-to/benchmark-runtime.md) has the commands to
reproduce them.

## Verified facts

### Value classes have conditional representation

Kotlin keeps a wrapper class for every value class and uses the underlying
representation where it can. It boxes a value used as a generic type, an
interface, a nullable value-class type, or another type. A direct parameter
whose static type is the value class is the documented unboxed case.

The JVM backend requires `@JvmInline`. The annotation is an `expect`
declaration in common Kotlin with a JVM `actual`, so common KMP source can use
it. Adding or removing the annotation is binary incompatible, because the
compiler mangles value-class signatures. The original requirement to avoid
`@JvmInline` while targeting Android and the JVM was therefore not valid, and
the generated-interface decision had to correct it.

[ADR-0009](../adr/0009-common-only-code-generation.md) (2026-10-18) made that
correction. The runtime and generated fixed-layout views now use common
`@JvmInline value class` declarations.

A Kompact view backed by `ByteArray` does not remove the array allocation. It
avoids another wrapper allocation only in unboxed call shapes. The caller must
create, receive, or reuse the array outside the measured operation.

Sources:

- [Kotlin inline value classes and boxing rules](https://kotlinlang.org/docs/inline-classes.html#representation)
- [`JvmInline` common and JVM declarations](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-jvm-inline/)

### AndroidX Microbenchmark measures time and allocations on ART

AndroidX Microbenchmark runs Kotlin or Java hot paths on Android, warms them
up, measures execution time and allocation counts, and writes detailed JSON.
Its Gradle plugin configures benchmark defaults. Current releases compile
ahead of time by default with supported Android Gradle Plugin versions, detect
thermal throttling, and can lock clocks on rooted devices.

Android's CI guidance strongly discourages emulators for performance numbers
and recommends physical devices. It treats benchmark results as noisy
measurements, not as ordinary pass or fail tests. The JSON output records the
device, CPU, clock lock, sustained-performance mode, run, and thermal
information.

Android Studio's allocation recorder captures Java and Kotlin allocation
types, sizes, call stacks, threads, and lifetimes. Full recording can visibly
slow apps that allocate heavily, so it is a diagnostic trace, not a source of
timing numbers.

Sources:

- [Android Microbenchmark overview](https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview)
- [Write an Android Microbenchmark](https://developer.android.com/topic/performance/benchmarking/microbenchmark-write)
- [Profile an Android Microbenchmark](https://developer.android.com/topic/performance/benchmarking/microbenchmark-profile)
- [Benchmark in continuous integration](https://developer.android.com/topic/performance/benchmarking/benchmarking-in-ci)
- [Record Java and Kotlin allocations](https://developer.android.com/studio/profile/record-java-kotlin-allocations)
- [AndroidX Benchmark 1.4.1 release](https://developer.android.com/jetpack/androidx/releases/benchmark#1.4.1)

### JMH can detect JVM allocation and boxing

kotlinx-benchmark uses JMH for JVM targets and writes JMH-compatible reports.
It supports common benchmark declarations and target-specific execution, but
it is still an Alpha toolkit. Version 0.4.19, the version reviewed for this
note, requires Kotlin 2.2.0 or newer and Gradle 8 or newer. The repository now
uses kotlinx-benchmark 0.5.0 (`gradle/libs.versions.toml`).

JMH's GC profiler takes snapshots of HotSpot thread-allocation counters and
reports the allocation rate. When allocations occur, it computes
`gc.alloc.rate.norm` in bytes per operation. If allocation profiling is
unavailable, it reports `NaN`. The evidence check must treat `NaN` as a
failure, not as zero.

This layer is useful for inspecting JVM lowering and testing negative
controls. HotSpot results cannot stand in for ART measurements on Android.

Sources:

- [kotlinx-benchmark guide at the reviewed revision](https://github.com/Kotlin/kotlinx-benchmark/blob/73284a133f1c3546668764a48d4b57663786d04b/README.md)
- [JMH `GCProfiler` allocation implementation](https://github.com/openjdk/jmh/blob/a194eead0136bb66e5e59e4fdb2e18543e730929/jmh-core/src/main/java/org/openjdk/jmh/profile/GCProfiler.java)

### Kotlin/Native GC sweep statistics give a bounded test-only signal

Kotlin/Native uses a tracing garbage collector and a page-based allocator.
`GC.collect()` and `GC.lastGCInfo()` can compare retained heap size after
completed collections. The official example uses them to detect leaks. GC logs
and Apple signposts show collection behavior and pauses.

In Kotlin 2.4.20, `GCInfo.sweepStatistics` holds a per-pool `sweptCount`
(objects freed) and `keptCount` (objects processed and retained). On the
physical Android Native test binary, these counts detected a batch of retained
and released known objects. Three runs of the same artifact each separated
two groups:

- the 4,096-operation loops for the direct `speed` getter and writer,
  unsigned validation, and cursor byte writes, with zero swept objects;
- the intentional-allocation controls, with 4,098 swept objects.

The primitive baselines also reported zero swept objects. This validates the
signal only for that test binary, runtime, and workload. The matching iOS
debug and release-optimized results are recorded above.

The runtime source labels GC statistics as testing and debugging data. They
are not a stable per-operation allocation API. Retained-heap values alone
still cannot detect temporary allocations. These results do not establish a
general zero-allocation guarantee or any behavior on unmeasured targets and
call shapes.

Sources:

- [Kotlin/Native memory management](https://kotlinlang.org/docs/native-memory-manager.html)
- [Kotlin 2.4.20 `GCInfo` runtime source](https://github.com/JetBrains/kotlin/blob/v2.4.20/kotlin-native/runtime/src/main/kotlin/kotlin/native/runtime/GCInfo.kt)

### Apple Instruments supplies the missing allocation trace

The Allocations instrument tracks the size and count of heap and anonymous VM
allocations, the allocation time, the category, and the responsible code.
Generation marks isolate the allocations made while a feature runs. Xcode can
also record allocation stack traces in memory graphs.

Kotlin/Native's allocator reserves pages and tags its reserved memory for
Instruments VM tracking. Total mapped memory therefore differs from the
per-object allocation count. Kompact must inspect the Allocations interval and
the call stacks around a large in-Kotlin loop. A flat process-memory graph
does not show zero allocation.

The iOS simulator does not reproduce device memory limits. It is still useful
for investigation, but a product claim requires device evidence.

Sources:

- [Apple: Gathering information about memory use](https://developer.apple.com/documentation/xcode/gathering-information-about-memory-use)
- [Kotlin/Native memory tracking on Apple platforms](https://kotlinlang.org/docs/native-memory-manager.html#track-memory-consumption-on-apple-platforms)

### kotlinx-benchmark does not cover the full iOS proof

kotlinx-benchmark supports Kotlin/Native targets, but its documented runner
runs Native benchmarks only for the host target. A macOS host benchmark can
detect broad regressions in shared code. It is not an `iosArm64` device result
and does not give a normalized Kotlin object-allocation count.

Source: [kotlinx-benchmark Kotlin/Native setup](https://github.com/Kotlin/kotlinx-benchmark/blob/73284a133f1c3546668764a48d4b57663786d04b/README.md#kotlinnative)

## Contract under measurement

The allocation-free claim should cover only these operations:

- a non-null Kompact view held in a local variable of its concrete generated
  type;
- a caller-owned, preallocated `ByteArray` created outside the measurement;
- a direct scalar property read that returns a non-null primitive carrier;
- a direct in-place scalar write through a concrete generated writer;
- no generic, interface, reflection, collection, nullable-view, exception,
  logging, validation, or Swift and Objective-C bridge work inside the
  measured operation.

Checked wrapping is a separate operation with its own timing and allocation
result. Failure paths are correctness tests and diagnostics, not part of the
allocation-free hot-path claim.

The original plan also called for separate known-code measurements of enum
decoding, because it returns an existing enum singleton instead of a
primitive. Kompact has no enum field type now
([ADR-0010](../adr/0010-minimal-checked-result-surface.md) and
[ADR-0011](../adr/0011-inferred-nested-fields-and-checked-fixed-decode.md)),
so that measurement does not apply to the current code.

Measure these negative controls to prove that each allocation detector works:

- pass the view through a generic identity function;
- pass it through an implemented interface;
- store it as a nullable value-class value;
- allocate one small reference object per operation on purpose.

If the negative controls do not produce a measurable allocation delta, the run
cannot support a zero-allocation conclusion.

## Workload matrix

Use the same deterministic payload corpus on every platform. The Enum row
comes from the original plan and has no current Kompact field type to
measure.

| Operation | Cases |
| --- | --- |
| Unsigned read/write | widths 1, 5, 8, 10, 16, 32, and 64 |
| Signed read/write | widths 2, 7, 10, 32, and 64; positive, negative, minimum, maximum |
| Position | byte-aligned and offsets 1, 4, and 7 crossing boundaries |
| Boolean | zero and one at aligned and unaligned positions |
| Enum | known low, high, and gapped codes |
| Float | finite, signed zero, infinity, and canonicalized NaN for 32 and 64 bits |
| Wrapper path | direct concrete, generic, interface, and nullable |

Prepare buffers and expected values outside the timed block. Each invocation
returns, or adds to, a primitive checksum so that the compiler cannot remove
the work. Writers rotate through preallocated buffers or input values so that
the benchmark does not measure a constant-folded operation. Check the checksum
in ordinary tests before you run any benchmark.

Write separate benchmarks for the low-level bit runtime, the generated
property, the checked wrapper, and the generated writer. Separate benchmarks
show whether a regression belongs to bit arithmetic, generated code, or
validation.

## Measurement matrix

### Host JVM diagnostic

Use kotlinx-benchmark with JMH forks, warmups, repeated measurement
iterations, JSON output, and the JMH GC profiler. The original plan named
version 0.4.19. The repository now uses 0.5.0. Record these values:

- nanoseconds per operation, and throughput where useful;
- `gc.alloc.rate`, `gc.alloc.rate.norm`, and GC counts;
- the JDK, JVM flags, CPU, OS, Kotlin version, commit, and generated-source
  hash.

Require that the allocation profiler is available. Compare direct paths with
all boxing controls. If a direct path allocates, inspect the JVM bytecode for
unexpected wrapper construction.

### Android acceptance

Use a dedicated Android Microbenchmark module and stable AndroidX Benchmark
1.4.1 for the Android JVM target. Run a non-debuggable, AOT-compiled benchmark
APK on one pinned physical device model and OS build. Prefer a rooted lab
device with locked clocks. Otherwise, require sustained-performance mode, no
thermal-throttle sleep, stable power, and repeated interleaved baseline and
candidate runs. This setup does not measure the Android Native executable.

Store the benchmark JSON and the profiling traces. Gate allocations on the
direct path only after the same APK detects a positive allocation control. Use
the Java and Kotlin allocation profiler to find unexpected classes and stacks.
Never use it to produce latency numbers.

### Kotlin/Native iOS acceptance

Build a release `iosArm64` benchmark executable on macOS. The executable calls
one Kotlin function that runs the complete repeated loop, so Swift and
Objective-C bridge costs fall outside the measured region. Use fixed input
arrays, a primitive checksum, and the same workload definitions as Android.

Run timing and allocation collection separately:

1. Run timing without Instruments on a pinned physical iPhone model, iOS
   build, power state, and thermal state. Capture repeated raw samples, not
   one aggregate.
2. Run allocation collection with Instruments Allocations. Mark a generation
   before and after a large operation count. Record the allocation count,
   allocated bytes, responsible stacks, and the operation count. Compare the
   result with an empty-loop control and the intentional-allocation control.
3. Run a leak check with `GC.collect()` and `GC.lastGCInfo()` before and after
   the loop. This check may catch retained objects, but do not report it as
   the allocation count.

Run the same executable on `iosSimulatorArm64` for fast smoke runs and
diagnostic traces. Do not compare simulator timing with device budgets.

## Environment controls

Every retained measurement must record the following:

- the repository commit and the generated-source hash;
- the Kotlin, KSP, Gradle, AGP, AndroidX Benchmark, kotlinx-benchmark, JDK,
  Xcode, and OS versions;
- the device model, CPU, memory, power source, battery state, thermal state,
  and clock-lock status where available;
- the build type, optimization level, AOT or JIT state, compiler arguments,
  and GC and allocator options;
- the benchmark name, parameters, warmups, iterations, forks or process
  launches, operation count, and units;
- the raw value of each run, not only a mean;
- whether a profiler was attached.

Do not compare results across device models, OS versions, compiler versions,
debug and release modes, or profiler attachment states as if only Kompact had
changed.

## Evidence and enforcement

Keep ordinary correctness tests in the default CI suite. Run benchmark smoke
profiles there only to prove discovery, execution, checksums, parameter
coverage, and report generation.

Run budget measurements on dedicated workers:

- Linux or macOS HotSpot for JVM diagnostics;
- a pinned physical Android device for ART acceptance;
- a pinned physical iPhone connected to a macOS worker for Kotlin/Native
  acceptance.

Store the raw Android JSON, the JMH-compatible JSON, the Instruments trace or
exported allocation data, the compiler and environment metadata, and a
machine-readable comparison summary. Scheduled and release-candidate runs
should compare the candidate with the committed baseline on the same worker,
in interleaved order. Shared hosted runners must not enforce latency
thresholds.

The later performance-budget decision should set numeric thresholds. Until
then, a zero-allocation claim is defensible only if all of these hold:

- the measured direct path shows no allocation delta;
- both the positive boxing control and the positive allocation control are
  detected;
- the allocation tool reports valid data;
- no relevant allocation stack appears in the measured interval.

## Unsupported claims

The evidence does not support any of these claims:

- that `value class` alone guarantees zero allocation;
- that omitting `@JvmInline` is compatible with the JVM value-class contract;
- that a HotSpot JMH result proves ART or Kotlin/Native behavior;
- that AndroidX Microbenchmark allocation results on ART prove Android Native
  allocation behavior;
- that a flat retained heap after `GC.collect()` proves that no temporary
  object was allocated;
- that simulator timing establishes physical-device latency;
- that timing and allocation profiling collected in one run give unperturbed
  latency;
- that one benchmark mean, without raw samples, controls, and environment
  metadata, supports a regression claim.

## Remaining risks

Kotlin/Native has no documented, stable per-operation object-allocation
counter comparable to JMH or AndroidX Microbenchmark. Perfetto heapprofd does
not see allocations served from Kotlin/Native allocator pages, and the
eligibility rules for `user` builds exclude the current standalone Android
Native test process.

Instruments gives the best available first-party evidence for iOS. The exact
automation and export path still has to be proven on the selected Xcode
version and on a correctly signed device host. Native optimizer behavior may
differ between an isolated benchmark and real application call sites.

Generated code can also introduce boxing through nullable results, common
interfaces, or generic helpers. Generated code must keep direct concrete
paths, and the negative-control matrix must run before the allocation
contract is frozen.
