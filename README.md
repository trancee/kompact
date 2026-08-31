# Kompact

Kompact generates bit-packed Kotlin Multiplatform and C99 interfaces for fixed-size Bluetooth Low Energy payloads.

> [!IMPORTANT]
> Kompact `0.1.0-SNAPSHOT` is pre-release and has not been published externally. The repository builds and verifies disposable local publications. Physical iPhone performance and allocation evidence, pinned device identities, a selected production firmware compiler, and independent human review remain release blockers.

## What Kompact provides

- Bit-precise fixed layouts without byte alignment padding.
- Checked packet construction over caller-owned `ByteArray` storage.
- Generated common Kotlin facades, value-class views, and in-place writers.
- Direct scalar and indexed access over caller-owned buffers without copied views.
- Header-only C99 views, writers, constants, and portable byte-array helpers.
- Canonical schema descriptors, stable fingerprints, lifecycle registries, and compatibility checks.

Kompact packets start with a 16-bit envelope containing a 12-bit schema ID and a 4-bit layout version. The body follows at bit offset 16. Fields can cross byte boundaries; bit zero is the least-significant bit of byte zero.

Variable-length fields, direct Swift export, compiler-plugin generation, Intel iOS simulator support, and non-iOS Apple targets are outside v1.

## Start here

- [Generate and use your first Kompact packet](docs/tutorials/first-schema.md) — run the checked-in Kotlin and C99 consumer journey.
- [How to generate Kotlin and C99 interfaces from a schema](docs/how-to/generate-kotlin-and-c.md) — add a schema to an existing KMP module.
- [Gradle plugin reference](docs/reference/gradle-plugin.md) — configuration properties, tasks, outputs, variants, and publication behavior.
- [Schema authoring and generated API reference](docs/reference/schema-authoring.md) — annotations, supported types, and generated Kotlin/C99 declarations.
- [Runtime API reference](docs/reference/runtime-api.md) — bit operations, results, errors, and shared status assignments.
- [Implementation and release status](docs/reference/implementation-status.md) — verified targets, workflows, performance evidence, and remaining blockers.

Design decisions and tradeoffs are recorded in [`docs/adr/`](docs/adr/). Project terminology is defined in [`CONTEXT.md`](CONTEXT.md).

## Build the repository

Prerequisites: JDK 21 and an Android SDK containing platform 36.

```bash
./gradlew check
```

The aggregate check runs Kotlin tests and compilation, ABI validation, Kover, Spotless, detekt, Android lint and benchmark-APK assembly, C99 conformance and fuzz smoke, and the JVM benchmark smoke profile. Dedicated manifest-driven Android vector execution and physical iPhone evidence remain release work.

Other useful commands:

```bash
./gradlew publishToMavenLocal
./gradlew :kompact-benchmarks:jvmSmokeBenchmark
./gradlew :kompact-android-benchmark:assembleReleaseAndroidTest
```

The Gradle plugin ID and Maven group are `ch.trancee.kompact`. Stable build-tool entry points are `generateKompactSchemas`, `checkKompactSchemas`, and `packageKompactCHeaders`.
