# Allocation and boxing measurement on Android and iOS

## Question

How can Kompact detect allocations, Kotlin value-class boxing, and direct bit-operation cost for scalar reads and in-place writes on Android/JVM and Kotlin/Native iOS? Which harnesses, controls, and evidence can support numeric performance budgets?

## Conclusion

Kompact cannot promise that a value class is allocation-free in every call shape. Kotlin explicitly boxes value classes at generic, interface, and nullable boundaries. The performance contract must name direct, statically typed calls over a caller-owned `ByteArray` and test boxing-prone calls separately.

Use three measurement layers:

1. Android acceptance measurements with AndroidX Microbenchmark 1.4.1 on a dedicated physical device. It measures timing and allocation counts and writes machine-readable JSON.
2. Host-JVM diagnostics with kotlinx-benchmark and JMH's GC profiler. This catches JVM boxing and reports normalized allocated bytes per operation, but it does not prove Android ART behavior.
3. Kotlin/Native iOS acceptance with a release `iosArm64` harness that executes the measured loop inside Kotlin. Measure timing without Instruments, then run a separate Xcode Instruments Allocations capture for allocation count, bytes, and call stacks. On Android Native, a test-only Kotlin/Native GC sweep-statistics probe now detects a known allocation control and distinguishes the measured getter/writer loops from a primitive baseline. This is bounded debug-test evidence, not a release-optimized allocation guarantee.

The iOS simulator is useful for repeatable diagnostics and functional smoke runs. Physical iPhone measurements remain the acceptance evidence for latency and allocation budgets.

> **Status:** this is a measurement plan, not an executed timing benchmark
> report. The bounded Android Native allocation-probe result is recorded below;
> no Android or iOS timing results or numeric performance budgets are recorded
> here.

## Current proof status

The checked caller-owned cursor and generated-holder APIs now exist. An
Android Native test-only probe compares direct generated speed reads and writes,
cursor unsigned validation, and cursor byte writes with primitive baselines and
an intentional-allocation control. Physical device results are recorded below;
they are limited to the exact debug test binary, operations, iteration count,
and runtime. Compilation, functional tests, and 100% line/branch coverage do
not prove zero per-operation allocations.

On 2026-10-06, the available host is Apple Silicon macOS 27.0 with Xcode 27.0
and a connected Android 15 API 35 arm64 device. Android Native behavior tests
passed on that device using the Linux CI-built test executable; this is not
allocation evidence.

### iOS GC sweep-statistics probe

The allocation probes live in the shared `nativeTest` source set, so they run
on Android Native, iOS Simulator, and physical iOS. On 2026-10-07, the
`iosArm64` debug test binary was built locally from the working tree on top
of commit `5919dbe` (SHA-256
`621e32dddfc62a49e5f95322c02b472c183fc2e77d1e479f25801bfa999b6623`, Kotlin
`2.4.20`). `scripts/test-ios-device.sh` ran it three times on an iPhone SE
(2020) with iOS `18.7.8` (Xcode `27.0`, `27A266a`). Every run passed all 396
tests in 37 test cases. The known-object probe reported `keptCount=6906` and
`sweptCount=2289`. Three samples of 4,096 direct generated `speed` reads and
writes, cursor byte reads, unsigned validations, and cursor byte writes each
reported `[0, 0, 0]` swept objects. This matched the primitive baselines.
Every intentional-allocation control reported `[4098, 4098, 4098]`. The local
iOS Simulator run of the same probes reported identical measured and control
counts. The same limits as the Android result apply: this is a debug test
binary, GC statistics are testing/debugging data, and no Instruments
Allocations capture was made.

### Release-optimized Native GC sweep-statistics probe

`kompact/build.gradle.kts` also links release-optimized test executables
(`linkReleaseTestIosArm64`, `linkReleaseTestIosSimulatorArm64`,
`linkReleaseTestAndroidNativeArm64`, output `releaseTest/test.kexe`) so the same
`nativeTest` probes run with compiler optimizations enabled. On 2026-10-07 the
`iosArm64` release binary (SHA-256
`932cbb17b83bbe0ec0cf5227894cb4addeb2167d0f1f3a27582495ff1139bc66`) ran three
times through `scripts/test-ios-device.sh` on the iPhone SE (2020), iOS
`18.7.8`; each run passed all 396 tests. Every measured probe reported
`[0, 0, 0]` swept objects, matching the primitive baselines, and every
intentional-allocation control reported `[4098, 4098, 4098]`, so the optimizer
did not hide the controls' allocations. The `iosSimulatorArm64` release binary
reported identical counts.

The `androidNativeArm64` release binary from Linux CI run `37639871425`
(commit `50768be`, artifact `kompact-android-native-arm64-release-test`,
SHA-256 `f1df293a5b0b5a5017f09a312330eb57dc637bda4399028ea6ca933adbe931b3`)
ran three times through `scripts/test-android-native-device.sh` on the Android
15 arm64 device; each run passed all 396 tests and removed its device temporary
files. Measured probes reported `[0, 0, 0]` and controls `[4098, 4098, 4098]`,
matching the iOS release results.

These release results carry the same limits: GC statistics are
testing/debugging data, only the measured call shapes are covered, there is no
Instruments or other independent allocation trace, and no timing budget.

### Android Native allocation-counter feasibility

The Android 15 device is a production `user` build with Perfetto v46 and the
`android.heapprofd` data source available. Heapprofd is not a validated
per-operation counter for Kompact's Android Native target:

- Perfetto's [heapprofd documentation](https://perfetto.dev/docs/data-sources/native-heap-profiler)
  describes sampled `malloc`/`free` and `new`/`delete` activity. Kotlin
  Native's default allocator suballocates objects from pages, so per-object
  allocations need not call the system allocator and therefore need not
  appear as heap-profiler events.
- On Android `user` builds, Perfetto documents profiling only debuggable or
  profileable Java apps. The current `test.kexe` is a standalone Native test
  executable run as the ADB shell user, not such an app.
- Kotlin/Native [`GC.lastGCInfo()`](https://kotlinlang.org/docs/native-memory-manager.html#check-for-memory-leaks)
  reports statistics from the last completed collection and is intended for
  testing/debugging. Retained-heap values alone do not count temporary objects.
  Kotlin 2.4.20's [`GCInfo`](https://github.com/JetBrains/kotlin/blob/v2.4.20/kotlin-native/runtime/src/main/kotlin/kotlin/native/runtime/GCInfo.kt)
  also exposes per-collection `sweepStatistics` with `sweptCount` and
  `keptCount`. A physical-device test detected both retained and released
  known objects and distinguished the measured loops from a primitive
  baseline. This testing/debugging API remains a bounded signal, not a stable
  production allocation contract.
- [AndroidX Microbenchmark](https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview)
  reports Android runtime/ART behavior; it does not instrument the separate
  Kotlin/Native executable.

On 2026-10-07, the Linux CI artifact for commit `ef97610b39caf6380210c288dcce190d49abcb14`
(run `37596537995`, SHA-256
`f84d76040d63a4410bd393fbc3a68f2e9ea58f96df0ccf8bd2d616ba087b9c32`) ran
three times on the Android 15 arm64 device (model `A063`, API 35, build
`AQ3A.240929.001`). All runs passed 390 tests in 36 test cases. Each reported
`keptCount=6031` and `sweptCount=2288` for the known-object probe. For three
samples of 4,096 operations per run, direct generated `speed` reads and writes
each reported `[0, 0, 0]` swept objects, exactly matching the primitive
baseline `[0, 0, 0]`; the 4,096-instance intentional-allocation control
reported `[4098, 4098, 4098]` for both operations. The tests require measured
counts not to exceed the primitive baseline (no percentage-of-control
allowance). The runner verified the artifact checksum on-device and removed
its temporary files.

### Caller-owned cursor unsigned write investigation

The first Android Native cursor probe for commit `be87a93` found that 4,096
`writeUnsigned(ULong)` calls swept 4,096 objects per sample, while cursor byte
reads and `writeUnsigned(Long)` matched the primitive baseline. Further
isolation on `c245943` showed that `validateUnsigned(ULong)` alone swept one
object per validation; conversion, raw `Long` writes, and full-width
validation did not. The inline-validation attempt in `bf0026c` did not resolve
the allocation. A follow-up probe on `ab95a77` isolated the narrow-width
`ULong` right-shift expression as the allocation boundary: the range check and
precomputed `ULong` write each swept one object per operation.

Commit `5989ac2` changed the shared validator and writer to perform the same
range check through `value.toLong() ushr bitWidth`. Its Linux CI artifact
(run `37612656077`, SHA-256
`7d26ba4543dddbead1b72e77e761c59cd8bfb672d79f870bfa295f0426139551`) was
executed three times on the Android 15 arm64 device (model `A063`, API 35,
build `AQ3A.240929.001`; macOS `27.0.0` arm64; ADB `37.0.1-15733141`). Every
run passed all 396 tests. In all three runs, the 4,096-operation unsigned
validation and cursor byte-write probes each reported `[0, 0, 0]` swept
objects, matching their primitive baselines; the intentional-allocation
controls each reported `[4098, 4098, 4098]`. The device runner verified the
artifact checksum and removed its temporary files after each run.

This is bounded evidence for these operations in the exact Kotlin/Native debug
test binary and environment. It does not establish release-optimized behavior,
a general per-operation allocation guarantee, or a numeric performance budget.

The probes validate that the test binary's GC sweep statistics detect the
known allocation controls and that the measured direct getter/writer,
unsigned-validation, and cursor-byte-write loops did not produce a detectable
swept-object delta in the debug and release-optimized test binaries. They do
not establish a timing budget, behavior for other operations or
call shapes, or a universal zero-allocation guarantee. The counter is
explicitly testing/debugging data and may change across compiler or runtime
versions. Do not treat an empty Perfetto profile, unchanged retained heap, or
successful Native device test alone as zero-allocation evidence. Release-optimized
results for both Native targets are recorded above; an independent
Instruments or equivalent allocation trace remains optional and unperformed.

## Checked variable-repeat tradeoff

Generated framed-holder decoding preflights the complete operation before it
commits holder state. Variable-width repeat preflight validates element
boundaries and workspace capacity without binding the workspace; the decode
pass then traverses those elements again to populate the caller-owned sparse
index. This is an intentional failure-atomicity tradeoff, not a measured
performance result. Measure representative repeat sizes on each target before
setting or claiming a performance budget.

## Verified facts

### Value classes have conditional representation

Kotlin keeps a wrapper class for every value class and prefers the underlying representation where possible. It boxes a value when used as a generic type, interface, nullable value-class type, or another type. A direct parameter whose static type is the value class is the documented unboxed case.

The JVM backend requires `@JvmInline`. The annotation is an `expect` declaration in common Kotlin with a JVM `actual`, so common KMP source can use it. Adding or removing it is binary incompatible because value-class signatures are mangled. The original requirement to avoid `@JvmInline` while targeting Android/JVM is therefore not valid and must be corrected by the generated-interface decision.

A Kompact view backed by `ByteArray` does not eliminate the array allocation. It avoids allocating another wrapper only in unboxed call shapes; the caller must create, receive, or reuse the array outside the measured operation.

Sources:

- [Kotlin inline value classes and boxing rules](https://kotlinlang.org/docs/inline-classes.html#representation)
- [`JvmInline` common and JVM declarations](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-jvm-inline/)

### AndroidX Microbenchmark measures time and allocations on ART

AndroidX Microbenchmark runs Kotlin or Java hot paths on Android, performs warmup, measures execution time and allocation counts, and writes detailed JSON. Its Gradle plugin configures benchmark defaults. Current releases use ahead-of-time compilation by default with supported Android Gradle Plugin versions, detect thermal throttling, and can lock clocks on rooted devices.

Android's CI guidance strongly discourages emulators for performance numbers and recommends physical devices. It also treats benchmark results as noisy measurements rather than ordinary pass/fail tests and records device, CPU, clock-lock, sustained-performance, run, and thermal information in JSON.

Android Studio's allocation recorder captures Java/Kotlin allocation types, sizes, call stacks, threads, and lifetimes. Full recording can visibly slow allocation-heavy apps, so it is a diagnostic trace, not the timing source.

Sources:

- [Android Microbenchmark overview](https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview)
- [Write an Android Microbenchmark](https://developer.android.com/topic/performance/benchmarking/microbenchmark-write)
- [Profile an Android Microbenchmark](https://developer.android.com/topic/performance/benchmarking/microbenchmark-profile)
- [Benchmark in continuous integration](https://developer.android.com/topic/performance/benchmarking/benchmarking-in-ci)
- [Record Java and Kotlin allocations](https://developer.android.com/studio/profile/record-java-kotlin-allocations)
- [AndroidX Benchmark 1.4.1 release](https://developer.android.com/jetpack/androidx/releases/benchmark#1.4.1)

### JMH can detect JVM allocation and boxing

kotlinx-benchmark uses JMH for JVM targets and writes JMH-compatible reports. It supports common benchmark declarations and target-specific execution, but it remains an Alpha toolkit. Version 0.4.19 requires Kotlin 2.2.0 or newer and Gradle 8 or newer.

JMH's GC profiler snapshots HotSpot thread-allocation counters and reports allocation rate. When allocations occur, it computes `gc.alloc.rate.norm` in bytes per operation. If allocation profiling is unavailable, it reports `NaN`, which must fail the evidence check rather than count as zero.

This layer is valuable for inspecting JVM lowering and testing negative controls, but HotSpot results cannot substitute for ART measurements on Android.

Sources:

- [kotlinx-benchmark guide at the reviewed revision](https://github.com/Kotlin/kotlinx-benchmark/blob/73284a133f1c3546668764a48d4b57663786d04b/README.md)
- [JMH `GCProfiler` allocation implementation](https://github.com/openjdk/jmh/blob/a194eead0136bb66e5e59e4fdb2e18543e730929/jmh-core/src/main/java/org/openjdk/jmh/profile/GCProfiler.java)

### Kotlin/Native GC sweep statistics provide a bounded test-only signal

Kotlin/Native uses a tracing garbage collector and a page-based allocator. `GC.collect()` and `GC.lastGCInfo()` can compare retained heap size after completed collections. The official example uses this to detect leaks. GC logs and Apple signposts expose collection behavior and pauses.

The Kotlin 2.4.20 `GCInfo.sweepStatistics` contains per-pool `sweptCount` (objects freed) and `keptCount` (objects processed and retained). On the physical Android Native test binary, this detected a batch of retained and released known objects. Three runs of the same artifact each separated 4,096-operation direct speed getter/writer, unsigned-validation, and cursor-byte-write loops (zero swept objects) from intentional-allocation controls (4,098 swept objects), with the same zero-object primitive baselines. This validates the signal only for that test binary, runtime, and workload. The runtime source labels GC statistics as testing/debugging data; it is not a stable per-operation allocation API. Retained-heap values alone still cannot detect temporary allocations, and these results do not establish release-optimized or cross-platform zero-allocation behavior.

Sources:

- [Kotlin/Native memory management](https://kotlinlang.org/docs/native-memory-manager.html)
- [Kotlin 2.4.20 `GCInfo` runtime source](https://github.com/JetBrains/kotlin/blob/v2.4.20/kotlin-native/runtime/src/main/kotlin/kotlin/native/runtime/GCInfo.kt)

### Apple Instruments supplies the missing allocation trace

The Allocations instrument tracks the size and count of heap and anonymous VM allocations, allocation time, category, and responsible code. Generation marks isolate allocations made while a feature runs. Xcode can also record allocation stack traces in memory graphs.

Kotlin/Native's allocator reserves pages and tags its reserved memory for Instruments VM tracking. That makes total mapped memory different from per-object allocation count. Kompact must inspect the Allocations interval and call stacks around a large in-Kotlin loop, not infer zero allocation from a flat process-memory graph.

The iOS simulator does not reproduce device memory limits. It remains useful for investigation, but device evidence is required for a product claim.

Sources:

- [Apple: Gathering information about memory use](https://developer.apple.com/documentation/xcode/gathering-information-about-memory-use)
- [Kotlin/Native memory tracking on Apple platforms](https://kotlinlang.org/docs/native-memory-manager.html#track-memory-consumption-on-apple-platforms)

### kotlinx-benchmark does not provide the full iOS proof path

kotlinx-benchmark supports Kotlin/Native targets, but its documented runner executes Native benchmarks only for the host target. A macOS host benchmark can detect broad regressions in shared code, but it is not an `iosArm64` device result and does not provide a normalized Kotlin object-allocation count.

Source: [kotlinx-benchmark Kotlin/Native setup](https://github.com/Kotlin/kotlinx-benchmark/blob/73284a133f1c3546668764a48d4b57663786d04b/README.md#kotlinnative)

## Contract under measurement

The allocation-free claim should cover only these operations:

- a non-null Kompact view held in a local variable of its concrete generated type;
- a caller-owned, preallocated `ByteArray` created outside the measurement;
- a direct scalar property read returning a non-null primitive carrier;
- a direct in-place scalar write through a concrete generated writer;
- no generic, interface, reflection, collection, nullable-view, exception, logging, validation, or Swift/Objective-C bridge inside the measured operation.

Checked wrapping is a separate operation with its own timing and allocation result. Enum decoding needs separate known-code measurements because it returns an existing enum singleton rather than a primitive. Failure paths are correctness tests and diagnostics, not part of the allocation-free hot-path claim.

Measure these negative controls to prove each allocation detector is working:

- pass the view through a generic identity function;
- pass it through an implemented interface;
- store it as a nullable value-class value;
- intentionally allocate one small reference object per operation.

If the negative controls do not produce a measurable allocation delta, the run cannot support a zero-allocation conclusion.

## Workload matrix

Use the same deterministic payload corpus on every platform:

| Operation | Cases |
| --- | --- |
| Unsigned read/write | widths 1, 5, 8, 10, 16, 32, and 64 |
| Signed read/write | widths 2, 7, 10, 32, and 64; positive, negative, minimum, maximum |
| Position | byte-aligned and offsets 1, 4, and 7 crossing boundaries |
| Boolean | zero and one at aligned and unaligned positions |
| Enum | known low, high, and gapped codes |
| Float | finite, signed zero, infinity, and canonicalized NaN for 32 and 64 bits |
| Wrapper path | direct concrete, generic, interface, and nullable |

Prepare buffers and expected values outside the timed block. Each invocation returns or contributes to a primitive checksum so the compiler cannot remove the work. Writers rotate through preallocated buffers or input values to avoid measuring a constant-folded operation. Validate the checksum in ordinary tests before running any benchmark.

Separate benchmarks for the low-level bit runtime, generated property, checked wrapper, and generated writer. This preserves the seam needed to identify whether a regression belongs to bit arithmetic, generated code, or validation.

## Measurement matrix

### Host JVM diagnostic

Use kotlinx-benchmark 0.4.19 with JMH forks, warmups, repeated measurement iterations, JSON output, and the JMH GC profiler. Record nanoseconds per operation, throughput where useful, `gc.alloc.rate`, `gc.alloc.rate.norm`, GC counts, JDK, JVM flags, CPU, OS, Kotlin version, commit, and generated-source hash.

Require allocation profiler availability. Compare direct paths with all boxing controls. Inspect JVM bytecode for unexpected wrapper construction when a direct path allocates.

### Android acceptance

Use a dedicated Android Microbenchmark module and stable AndroidX Benchmark 1.4.1 for the Android/JVM target. Run a non-debuggable, AOT-compiled benchmark APK on one pinned physical device model and OS build. Prefer a rooted lab device with locked clocks; otherwise require sustained-performance mode, no thermal-throttle sleep, stable power, and repeated interleaved baseline and candidate runs. This does not measure the Android Native executable.

Store benchmark JSON and profiling traces. Gate allocations on the direct path only after a positive allocation control is detected in the same APK. Use the Java/Kotlin allocation profiler to identify unexpected classes and stacks, never to produce latency numbers.

### Kotlin/Native iOS acceptance

Build a release `iosArm64` benchmark harness on macOS. The harness calls one Kotlin function that performs the complete repeated loop so Swift/Objective-C bridge costs occur outside the measured region. Use fixed input arrays, a primitive checksum, and the same workload definitions as Android.

Run timing and allocation collection separately:

1. Timing run without Instruments on a pinned physical iPhone model, iOS build, power state, and thermal state. Capture repeated raw samples rather than one aggregate.
2. Allocation run with Instruments Allocations. Mark a generation before and after a large operation count. Record allocation count, allocated bytes, responsible stacks, and the operation count. Compare with an empty-loop control and the intentional-allocation control.
3. Leak sentinel using `GC.collect()` and `GC.lastGCInfo()` before and after the loop. This may catch retained objects but must not be reported as the allocation count.

Run the same harness on `iosSimulatorArm64` for fast smoke and diagnostic traces. Do not compare simulator timing to device budgets.

## Environment controls

Every retained measurement must record:

- repository commit and generated-source hash;
- Kotlin, KSP, Gradle, AGP, AndroidX Benchmark, kotlinx-benchmark, JDK, Xcode, and OS versions;
- device model, CPU, memory, power source, battery state, thermal state, and clock-lock status where available;
- build type, optimization, AOT/JIT state, compiler arguments, GC and allocator options;
- benchmark name, parameters, warmups, iterations, forks or process launches, operation count, and units;
- raw per-run values, not only a mean;
- whether a profiler was attached.

Do not compare results across device models, OS versions, compiler versions, debug/release modes, or profiler attachment states as if only Kompact changed.

## Evidence and enforcement

Keep ordinary correctness tests in the default CI suite. Run benchmark smoke profiles there only to prove discovery, execution, checksums, parameter coverage, and report generation.

Run budget measurements on dedicated workers:

- Linux or macOS HotSpot for JVM diagnostics;
- a pinned physical Android device for ART acceptance;
- a pinned physical iPhone connected to a macOS worker for Kotlin/Native acceptance.

Store raw Android JSON, JMH-compatible JSON, Instruments trace or exported allocation data, compiler and environment metadata, and a machine-readable comparison summary. Scheduled and release-candidate runs should compare candidate and committed baseline on the same worker in interleaved order. Shared hosted runners must not enforce latency thresholds.

The later performance-budget decision should set numeric thresholds. Until then, the only defensible zero-allocation rule is: the measured direct path shows no allocation delta, both positive boxing/allocation controls are detected, the allocation tool reports valid data, and no relevant allocation stack appears in the measured interval.

## Unsupported claims

- `value class` alone does not guarantee zero allocation.
- Omitting `@JvmInline` is incompatible with the JVM value-class contract.
- A HotSpot JMH result does not prove ART or Kotlin/Native behavior.
- AndroidX Microbenchmark/ART allocation results do not prove Android Native allocation behavior.
- A flat retained heap after `GC.collect()` does not prove that no temporary object was allocated.
- Simulator timing does not establish physical-device latency.
- Timing and allocation profiling should not be collected in one run and treated as unperturbed latency.
- One benchmark mean without raw samples, controls, and environment metadata cannot support a regression claim.

## Remaining risks

Kotlin/Native exposes no documented stable per-operation object-allocation counter comparable to JMH or AndroidX Microbenchmark. Perfetto heapprofd does not see allocations served from Kotlin/Native allocator pages, and user-build eligibility excludes the current standalone Android Native test process. Instruments provides the best available first-party evidence for iOS, but the exact automation and export path must be proven on the selected Xcode version and a correctly signed device host. Native optimizer behavior may differ between an isolated benchmark and real application call sites. The generated Kotlin interface can also introduce boxing through nullable results, common interfaces, or generic helpers; its prototype must retain direct concrete paths and run the negative-control matrix before the allocation contract is frozen.
