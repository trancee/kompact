# Allocation and boxing measurement on Android and iOS

## Question

How can Kompact detect allocations, Kotlin value-class boxing, and direct bit-operation cost for scalar reads and in-place writes on Android/JVM and Kotlin/Native iOS? Which harnesses, controls, and evidence can support numeric performance budgets?

## Conclusion

Kompact cannot promise that a value class is allocation-free in every call shape. Kotlin explicitly boxes value classes at generic, interface, and nullable boundaries. The performance contract must name direct, statically typed calls over a caller-owned `ByteArray` and test boxing-prone calls separately.

Use three measurement layers:

1. Android acceptance measurements with AndroidX Microbenchmark 1.4.1 on a dedicated physical device. It measures timing and allocation counts and writes machine-readable JSON.
2. Host-JVM diagnostics with kotlinx-benchmark and JMH's GC profiler. This catches JVM boxing and reports normalized allocated bytes per operation, but it does not prove Android ART behavior.
3. Kotlin/Native iOS acceptance with a release `iosArm64` harness that executes the measured loop inside Kotlin. Measure timing without Instruments, then run a separate Xcode Instruments Allocations capture for allocation count, bytes, and call stacks. Kotlin/Native GC statistics are a leak and retained-heap check, not a per-operation allocation counter.

The iOS simulator is useful for repeatable diagnostics and functional smoke runs. Physical iPhone measurements remain the acceptance evidence for latency and allocation budgets.

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

### Kotlin/Native exposes retained-heap and GC data, not an operation counter

Kotlin/Native uses a tracing garbage collector and a page-based allocator. `GC.collect()` and `GC.lastGCInfo()` can compare retained heap size after completed collections. The official example uses this to detect leaks. GC logs and Apple signposts expose collection behavior and pauses.

These metrics do not count every allocation performed by a benchmark operation. A temporary object can be allocated and collected while leaving the same retained heap size. `GC.lastGCInfo()` is therefore a secondary leak check, not proof that a getter allocated zero objects.

Source: [Kotlin/Native memory management](https://kotlinlang.org/docs/native-memory-manager.html)

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

Use a dedicated Android Microbenchmark module and stable AndroidX Benchmark 1.4.1. Run a non-debuggable, AOT-compiled benchmark APK on one pinned physical device model and OS build. Prefer a rooted lab device with locked clocks; otherwise require sustained-performance mode, no thermal-throttle sleep, stable power, and repeated interleaved baseline and candidate runs.

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
- A flat retained heap after `GC.collect()` does not prove that no temporary object was allocated.
- Simulator timing does not establish physical-device latency.
- Timing and allocation profiling should not be collected in one run and treated as unperturbed latency.
- One benchmark mean without raw samples, controls, and environment metadata cannot support a regression claim.

## Remaining risks

Kotlin/Native exposes no documented stable per-operation object-allocation counter comparable to JMH or AndroidX Microbenchmark. Instruments provides the best available first-party evidence, but the exact automation and export path must be proven on the selected Xcode version. Native optimizer behavior may differ between an isolated benchmark and real application call sites. The generated Kotlin interface can also introduce boxing through nullable results, common interfaces, or generic helpers; its prototype must retain direct concrete paths and run the negative-control matrix before the allocation contract is frozen.
