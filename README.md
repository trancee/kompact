# Kompact

Bit-packed Kotlin Multiplatform serialization for Bluetooth Low Energy, designed for allocation-free reads and interoperable C firmware.

> [!IMPORTANT]
> Kompact v1 is a pre-release implementation. Runtime, code generation, compatibility, publication-consumer, conformance, benchmark, and CI gates build from source; no artifacts have been externally published. Physical iPhone performance evidence and production firmware compiler gates remain release prerequisites.

## Why Kompact

BLE payloads are small, and byte-aligned formats can spend more space on padding and metadata than the values require. Kompact defines each field at bit precision. A 5-bit value occupies 5 bits, including when it crosses a byte boundary.

The project has four goals:

- Pack fixed-size payloads without byte padding.
- Read scalar fields directly from caller-owned `ByteArray` storage without copying.
- Generate a typed Kotlin interface for shared Android and iOS code.
- Generate matching C99 constants and helpers for firmware.

## Implemented v1 foundation

Kompact v1 implements these constraints:

- Schemas have fixed, versioned layouts and an explicit envelope.
- Bit offset zero is the least-significant bit of byte zero.
- Kotlin runtime and generated code live in `commonMain` and target Android/JVM, `iosArm64`, and `iosSimulatorArm64`.
- Checked factories validate reserved and unknown identities, versions, exact lengths, transport-tail bits, and complete bodies in deterministic bit order.
- Generated views and writers expose scalar, byte, array, optional, nested, and nested-array access without copied slices.
- Writers update caller-owned buffers in place and validate every fallible input before mutation.
- Decode-only registry versions generate readers without encoder or writer APIs.
- One process-isolated KSP2 common pass produces Kotlin, header-only C99, canonical descriptors, fingerprints, and registry proposals.
- Compatibility checks retain registry history, lifecycle transitions, decoder sources, ABI dumps, and byte-identical relocated-cache outputs.
- Reviewed C vectors, deterministic property tests, fixed-seed sanitizer fuzzing, JVM workloads, and an AndroidX Microbenchmark APK cover the retained small, medium, and large workloads.
- Disposable Maven publication consumers compile generated code for JVM, Android, `iosArm64`, and `iosSimulatorArm64`, plus strict multi-translation-unit C99 consumers.

Variable-length fields, direct Swift export, compiler-plugin generation, and non-iOS Apple targets are outside the v1 scope.

## Build from source

```bash
./gradlew check
./gradlew publishToMavenLocal
./gradlew :kompact-benchmarks:jvmSmokeBenchmark
```

The Gradle plugin ID and Maven group are `ch.trancee.kompact`. Apply the plugin to an existing Kotlin Multiplatform module, configure one `kompact` protocol namespace and registry, and declare matching `kompact-runtime` and `kompact-annotations` dependencies.

The stable build-tool entry points are `generateKompactSchemas`, `checkKompactSchemas`, and `packageKompactCHeaders`.

The checked-in workflows run Linux checks, Android emulator conformance, macOS/iOS compilation and simulator tests, strict GCC/Clang consumers, sanitizer fuzzing, big-endian QEMU, disposable publication consumers, JVM measurements, and a generic `android-reference` self-hosted physical benchmark gate.

The maintainer selected generic `android-reference` and `ios-reference` self-hosted labels because concrete runner and device identities are not yet available. The Android workflow executes AndroidX Microbenchmark on a connected device. The iPhone workflow currently captures host/device metadata and compiles the `iosArm64` benchmark code; physical-device timing and Instruments allocation capture remain blocking before release. No production firmware compiler has been selected, so GCC and Clang are the only firmware-facing compiler gates. Migration: replace the generic labels with pinned runner/device metadata, add the signed iPhone execution and Instruments steps, and add each adopted firmware compiler without weakening the existing gates.

File-size deviations from Constitution D9's 300-line default remain below its 500-line hard ceiling. `DescriptorBuilder.kt` keeps one descriptor-validation transaction together; `CGenerator.kt` and `CNestedArrayGenerator.kt` keep one header-emission backend together; `GenerateKompactSchemas.kt` keeps one cacheable worker transaction together; and `GeneratedContractFunctionalTest.kt` plus `KompactPluginFunctionalTest.kt` each keep one registry-backed TestKit fixture with its fingerprints and generated-consumer assertions. Splitting those transactions would duplicate protocol state or create a second mutation/fixture seam.

## Example layout

A 16-bit telemetry payload can assign every bit without alignment padding:

```text
bits  0..3   battery status       4-bit enum
bits  4..13  speed               10-bit unsigned integer
bit      14  engine malfunction   1-bit boolean
bit      15  reserved             1 bit
```

The same schema will drive generated Kotlin accessors and C99 extraction helpers. Developers author annotated schema interfaces; generated checked facades expose value-class views and writers in Kotlin and header-only typed handles in C99.

## Specification status

The closed [Kompact v1 implementation-ready specification](https://github.com/trancee/kompact/issues/1) is the canonical decision map. Its child issues record:

- wire and envelope semantics;
- KSP and Gradle integration;
- generated Kotlin and C99 interfaces;
- validation and compatibility;
- cross-platform conformance; and
- performance budgets.

Project-specific terminology lives in [`CONTEXT.md`](CONTEXT.md).
