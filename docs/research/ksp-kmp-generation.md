# KSP common-schema generation across Android and iOS

## Question

Can Kompact process each `commonMain` schema once, generate Kotlin consumed by Android/JVM, `iosArm64`, and `iosSimulatorArm64`, emit deterministic C99 headers, and retain sound diagnostics, incremental processing, build-cache reuse, IDE visibility, and publication?

## Conclusion

The standard KSP Gradle integration does not provide that contract. Its documented KMP model creates a processing task for every configured compilation, so target configurations process shared sources repeatedly. `kspCommonMainMetadata` exists, but common generated-source wiring remains an open upstream problem and depends on fragile manual task relationships.

Kompact should run KSP2 once through a dedicated cacheable JVM Gradle task, using KSP2's programmatic common-processing entry point. That task should own both generated common Kotlin and per-schema C99 headers. The normal KMP module should consume the Kotlin output through a task-backed source-directory provider. Do not apply KSP separately to Android and iOS targets for Kompact schemas.

## Verified facts

### Standard KMP processing is per compilation

KSP's official KMP guide requires a processor dependency for each target that needs processing. KSP then creates a symbol-processing task for every configured Kotlin compilation. The guide's example has at least ten processing tasks for its configured targets. Applying `kspAndroid`, `kspIosArm64`, and `kspIosSimulatorArm64` would therefore process declarations visible to each compilation more than once, not once globally.

Sources:

- [KSP with Kotlin Multiplatform](https://kotlinlang.org/docs/ksp-multiplatform.html)
- [KSP repository configuration reference](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/README.md#kotlin-multiplatform-kmp)

### Common metadata generation is not a stable integration seam

The KSP configuration reference lists `kspCommonMainMetadata`, but the first-party multiplatform example leaves that configuration commented out and demonstrates target-specific processing. The upstream request for first-class common generation remains open. The related iOS hierarchy request also remains open. Reported workarounds manually add `build/generated/ksp/metadata/commonMain/kotlin` and task dependencies; the upstream reports include missing task dependencies, duplicate declarations, IDE failures, configuration-cache failures, and publication failures across KSP and Gradle versions.

This evidence does not prove `kspCommonMainMetadata` can never work. It does show that Kompact cannot treat its manual wiring as a supported, stable interface for a published generator.

Sources:

- [First-party multiplatform example](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/examples/multiplatform/workload/build.gradle.kts)
- [Generating common code, open upstream issue](https://github.com/google/ksp/issues/567)
- [Generating into shared iOS source sets, open upstream issue](https://github.com/google/ksp/issues/929)
- [IDE and task dependency history](https://github.com/google/ksp/issues/963)

### KSP2 can run outside the compiler task

KSP2 is no longer a Kotlin compiler plugin. Its documented programmatic interface loads processor providers, constructs a `KSPConfig`, and calls `KotlinSymbolProcessing.execute()`. Its command-line distribution has separate JVM, JS, Native, and Common entry points. This gives Kompact a supported place to run one schema-processing operation independently of target compilation tasks.

KSP 2.3.0 also decoupled KSP's release version from Kotlin's compiler version. This reduces version lockstep but does not establish that every KSP release works with every Kotlin release.

Sources:

- [KSP2 architecture](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/docs/ksp2.md)
- [Calling KSP2 in programs](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/docs/ksp2entrypoints.md)
- [KSP2 command-line entry points](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/docs/ksp2cmdline.md)
- [KSP 2.3.0 release](https://github.com/google/ksp/releases/tag/2.3.0)

### Kotlin and C outputs can share KSP dependency tracking

`CodeGenerator.createNewFile` and `createNewFileByPath` accept an extension. Kotlin and Java outputs participate in subsequent compilation. Other extensions are still managed by KSP's incremental processing. Kompact can therefore emit `.kt` and `.h` files through the same processor without writing unmanaged files.

Each output must declare the source files that contribute to it. KSP distinguishes isolating outputs from aggregating outputs. Per-schema Kotlin and C files can be isolating. A registry or umbrella header that depends on every schema is aggregating and must declare that fact.

Sources:

- [KSP `CodeGenerator` interface](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/api/src/main/kotlin/com/google/devtools/ksp/processing/CodeGenerator.kt)
- [KSP incremental processing](https://kotlinlang.org/docs/ksp-incremental.html)

### Diagnostics can point at schema symbols

`KSPLogger.error`, `warn`, `info`, and `logging` accept an optional `KSNode`. Errors stop processing after the current round and cause `onError()` rather than `finish()` to run. Kompact should attach every schema diagnostic to the narrowest offending declaration and avoid throwing for expected validation failures.

Sources:

- [KSP `KSPLogger` interface](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/api/src/main/kotlin/com/google/devtools/ksp/processing/KSPLogger.kt)
- [KSP multiple-round error handling](https://kotlinlang.org/docs/ksp-multi-round.html#error-and-exception-handling)

### Build-cache and IDE correctness belong to Kompact's Gradle plugin

Gradle requires a cacheable task to declare complete inputs and outputs. File inputs need normalization for relocatable cache entries, and consumers should receive producer-backed `Provider` values so task dependencies are carried with the files. Raw build-directory strings create implicit dependencies and order-sensitive builds.

KSP 2.3.11 is the current release and includes a build-cache miss fix and Gradle isolated-project support. Those fixes do not make an external Kompact task cacheable automatically.

Sources:

- [Gradle build cache](https://docs.gradle.org/current/userguide/build_cache.html#sec:task_output_caching)
- [Gradle implicit dependency guidance](https://docs.gradle.org/current/userguide/validation_problems.html#implicit_dependency)
- [KSP 2.3.11 release](https://github.com/google/ksp/releases/tag/2.3.11)

### Generated code must be published through normal KMP artifacts

Kotlin Multiplatform publishes a root metadata artifact and target-specific artifacts. A common generated source registered before compilation becomes part of those compiled publications. Android publication still needs explicit configuration. C headers are not a KMP target artifact and need their own classified archive or consumable Gradle variant. All publications should come from one host to avoid duplicate coordinates.

Source: [Publish a Kotlin Multiplatform library](https://kotlinlang.org/docs/multiplatform-publish-lib.html)

## Recommended architecture

Use four modules and one generated output owner:

1. `kompact-runtime`: KMP runtime and public common types.
2. `kompact-annotations`: small KMP annotation artifact used by schema declarations.
3. `kompact-processor`: JVM processor artifact depending on the KSP API. It validates symbols and emits one Kotlin file and one C header per schema through `CodeGenerator`; global indexes are explicitly aggregating.
4. `kompact-gradle-plugin`: JVM Gradle plugin with a cacheable `GenerateKompactSchemas` task. The task invokes `symbol-processing-aa-embeddable` in common mode exactly once.

The generation task should declare:

- common schema source roots as relative-path-sensitive input files;
- the schema compile classpath as `@CompileClasspath`;
- processor and KSP artifacts as `@Classpath`;
- language/API versions, package policy, and generator options as scalar inputs;
- separate Kotlin, C-header, cache, and diagnostic output directories;
- no timestamps, machine paths, locale-sensitive ordering, or nondeterministic iteration in outputs.

Register the task's Kotlin output provider with `commonMain`. Register the C output as a separate archive or consumable variant. Compilation, source archives, IDE import, and publication must consume task providers rather than raw directory strings.

Do not also add the processor to `kspAndroid`, `kspIosArm64`, or `kspIosSimulatorArm64`; that would repeat common processing and risk duplicate generated declarations and concurrent header writes.

## Conservative version matrix

Start the integration prototype with this source-verified intersection:

| Tool | Baseline |
| --- | --- |
| Kotlin | 2.3.20 |
| KSP2 | 2.3.11 |
| Gradle | 9.3.0 |
| Android Gradle Plugin | 9.0.0 |
| Gradle runtime JDK | 17 |
| Apple targets | `iosArm64`, `iosSimulatorArm64` |

KSP's current source build uses Kotlin 2.3.20, while KSP 2.3.11 is the current published release. Kotlin's compatibility table fully supports Kotlin 2.3.20 with Gradle through 9.3.0 and AGP through 9.0.0. KSP's release number is now independent of Kotlin, but that is not evidence for an untested Kotlin 2.4.x pairing. Promote a newer tuple only after the integration matrix below passes unchanged.

Sources:

- [KSP version catalog](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/gradle/libs.versions.toml)
- [Kotlin, Gradle, and AGP compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin)
- [KSP 2.3.11 release](https://github.com/google/ksp/releases/tag/2.3.11)

## Required integration proof

Before treating this architecture as supported, a Gradle TestKit fixture must prove:

- one processing execution generates Kotlin used by Android/JVM, `iosArm64`, and `iosSimulatorArm64`;
- clean, up-to-date, and relocated `FROM-CACHE` builds produce byte-identical Kotlin and C outputs;
- schema add, change, rename, and removal invalidate the right outputs without leaving stale files;
- parallel target compilation cannot race generation;
- generated declarations resolve in Gradle-imported IDE models;
- the KMP root, Android, and both iOS publications resolve from real consumers;
- source artifacts contain generated public declarations when source publication is enabled;
- the C-header artifact is deterministic and attached to the intended publication or variant;
- invalid schemas fail with stable, symbol-located diagnostics.

## Unsupported assumptions

- Standard target-specific KSP tasks do not process `commonMain` once.
- `kspCommonMainMetadata` does not currently provide a documented, automatic, stable generated-source connection for every KMP target and publication.
- KSP does not make custom C outputs deterministic; the processor must sort and normalize them.
- KSP dependency metadata does not replace Gradle task input/output declarations.
- Adding a generated directory as a raw path does not establish the required task dependency.
- KMP publication does not publish C headers automatically.

## Remaining risks

The documented KSP2 programmatic example uses `KSPJvmConfig`; the common programmatic configuration needs a focused prototype against 2.3.11 before its exact Gradle interface is frozen. Upstream common-generation issues remain open, so Kompact owns more Gradle integration than a normal target-specific KSP processor. KSP2 runs in the Gradle daemon by default, which makes processor memory part of the daemon budget. Finally, macOS remains required to link and test final Apple binaries even though common generation itself is a JVM-hosted task.
