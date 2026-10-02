# KSP common-schema generation across Android and iOS

## Question

Can Kompact process each `commonMain` schema once, generate Kotlin consumed by Android/JVM, `iosArm64`, and `iosSimulatorArm64`, emit deterministic C99 headers, and retain sound diagnostics, incremental processing, build-cache reuse, IDE visibility, and publication?

## Conclusion

The standard KSP Gradle integration does not provide that contract. Its documented KMP model creates a processing task for every configured compilation, so target configurations process shared sources repeatedly. `kspCommonMainMetadata` exists, but common generated-source wiring remains an open upstream problem and depends on fragile manual task relationships.

Kompact's `0.5.0-SNAPSHOT` implementation addresses common Kotlin generation with the separate `:kompact-gradle-plugin`. Its cacheable task invokes KSP2's `KSPCommonConfig` path once, routes common and platform Kotlin outputs, and registers them through task-backed source directories. The plugin implementation and marker are configured for the existing Maven Central Portal pipeline. It does not generate C99 headers; that part of the original research question remains out of scope. Do not also apply standard target-specific KSP processing to the same Kompact schemas.

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

KSP 2.3.11 includes a build-cache miss fix and Gradle isolated-project support. Those fixes do not make an external Kompact task cacheable automatically.

Sources:

- [Gradle build cache](https://docs.gradle.org/current/userguide/build_cache.html#sec:task_output_caching)
- [Gradle implicit dependency guidance](https://docs.gradle.org/current/userguide/validation_problems.html#implicit_dependency)
- [KSP 2.3.11 release](https://github.com/google/ksp/releases/tag/2.3.11)

### Generated code must be published through normal KMP artifacts

Kotlin Multiplatform publishes a root metadata artifact and target-specific artifacts. A common generated source registered before compilation becomes part of those compiled publications. Android publication still needs explicit configuration. C headers are not a KMP target artifact and need their own classified archive or consumable Gradle variant. All publications should come from one host to avoid duplicate coordinates.

Source: [Publish a Kotlin Multiplatform library](https://kotlinlang.org/docs/multiplatform-publish-lib.html)

## Current Kompact implementation

The implemented generation path uses three published modules plus a JVM integration-test module:

1. `:kompact`: KMP runtime and public annotations.
2. `:kompact-ksp`: JVM KSP processor that validates schemas and generates Kotlin.
3. `:kompact-gradle-plugin`: JVM Gradle plugin with the cacheable `GenerateKompactSources` task. The task invokes `symbol-processing-aa-embeddable` in common mode once and routes output into `common`, `jvm`, `ios`, and `androidArm64` source directories.
4. `:kompact-ksp-integration`: generated-code compilation and runtime tests for the JVM processor path.

The task declares:

- common schema source roots as relative-path-sensitive input files;
- the common metadata compilation libraries as `@Classpath`;
- processor and KSP artifacts as `@Classpath`;
- language/API versions, module name, and generator options as scalar inputs;
- a generated Kotlin output directory and a local-state KSP work directory.

The plugin registers the generated common output with `commonMain` and platform outputs with their matching source sets using task-backed providers. Fixed-layout schemas retain their handwritten common `expect value class`; framed schemas generate common expect contracts and per-platform actuals. The task currently disables KSP incremental processing and relies on Gradle task/build caching.

Do not also add the processor to `kspAndroid`, `kspIosArm64`, or `kspIosSimulatorArm64`; that would repeat common processing and risk duplicate generated declarations.

## Research version baseline

This matrix records the conservative intersection used during the original
research; it is not the repository's current toolchain or an instruction to
downgrade. The repository currently uses Kotlin 2.4.20, KSP 2.3.12, Gradle
9.7.1, and AGP 9.4.1. Before implementing a custom integration, verify the
then-current official compatibility ranges and retain the repository's stable
toolchain unless a documented blocker requires a holdback.

The original source-verified intersection was:

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

## Integration coverage and remaining proof

The committed tests currently prove:

- the TestKit KMP consumer generates common and platform Kotlin and compiles JVM tests, Android JVM, `iosArm64`, `iosSimulatorArm64`, and `androidNativeArm64` sources;
- a handwritten fixed-layout common `expect` contract is paired with generated encoder/actual sources, while the framed common expect view and platform actuals are generated;
- a relocated consumer restores the generator output with `FROM-CACHE`, with byte-identical generated Kotlin;
- repeated generation reuses the configuration cache after the dependency classpath has stabilized;
- deleting the last common schema removes stale generated output;
- the JVM integration fixture compiles and round-trips nested, repeated, blob, and string fields.

Still not proved by these fixtures: final Apple binary linking/testing (requires macOS), Gradle IDE model import, resolution of published plugin artifacts from a clean external consumer, parallel-target race behavior, and C-header generation. The standard KSP path and the custom Kompact plugin are distinct: only the latter is covered by the KMP TestKit consumer.

## Unsupported assumptions

- Standard target-specific KSP tasks do not process `commonMain` once.
- `kspCommonMainMetadata` does not currently provide a documented, automatic, stable generated-source connection for every KMP target and publication.
- KSP does not make custom C outputs deterministic; the processor must sort and normalize them.
- KSP dependency metadata does not replace Gradle task input/output declarations.
- Adding a generated directory as a raw path does not establish the required task dependency.
- KMP publication does not publish C headers automatically.

## Remaining risks

Upstream common-generation issues remain open, so Kompact owns more Gradle integration than a normal target-specific KSP processor. The task uses KSP2's reflective `KSPLoader` entry point and obtains metadata libraries from KGP's `compileCommonMainKotlinMetadata` task; both seams need compatibility tests on every KSP/KGP upgrade. KSP 2.3.12's analysis engine embeds a Kotlin 2.4.20 development snapshot, so parsing parity with stable Kotlin remains a risk. Final Apple binary linking/testing still requires macOS even though common generation itself is JVM-hosted.

## Addendum: KSP 2.3.12 common-processing API (focused prototype findings)

This addendum answers the question the "Remaining risks" section above left open. It is
scoped to the repository's current tuple (Kotlin 2.4.20, KSP 2.3.12, Gradle 9.7.1, AGP 9.4.1,
JDK 21) and verified directly against the KSP 2.3.12 tag
([`a3c38590`](https://github.com/google/ksp/tree/a3c38590913b863cc6b73b41d54ff8afa625f642),
commit `a3c38590913b863cc6b73b41d54ff8afa625f642`), Maven Central artifact metadata, and current
kotlinlang.org compatibility pages. No codebase-memory MCP access was used or available for this
pass; all claims below come from primary sources (KSP source/releases/artifact metadata, Gradle's
version service, Google's Maven metadata, and kotlinlang.org).

### Conclusion

KSP2 does expose a real, reachable common-processing configuration type, `KSPCommonConfig`, and it
runs through the same analysis engine, `CodeGenerator`, and incremental machinery as every other
`KSPConfig` subtype — so a dedicated Kompact task can call it directly. **However, the one field
that would let a processor learn which concrete platforms (JVM/`iosArm64`/`iosSimulatorArm64`)
consume the common output — `KSPCommonConfig.targets` — is accepted by the type but is not read
anywhere in the KSP 2.3.12 execution engine, and KSP's own first-party Gradle plugin populates it
with a hard-coded empty list behind a `// FIXME: targets` comment.** This is unchanged on KSP's
`main` branch as of this research pass. Treat common-mode processing as viable for seeing `expect`
declarations and emitting one combined generated-Kotlin output, but treat `targets`/per-platform
awareness inside KSP2 itself as non-functional; Kompact's task must keep deriving per-platform
knowledge the way the "Recommended architecture" section already plans (scalar processor-option
inputs owned by Kompact's own task), not through this KSP2 field.

### Exact Maven coordinates

KSP's own Gradle plugin builds a detached, non-transitive classpath to run `KSPCommonConfig` (and
every other config type) out-of-process from its own plugin classpath. The same five coordinates
are the minimal, sufficient set for Kompact's task:

| Coordinate | Verified version | Role |
| --- | --- | --- |
| `com.google.devtools.ksp:symbol-processing-api` | `2.3.12` | `SymbolProcessorProvider`, `SymbolProcessor`, `CodeGenerator`, `KSPLogger` (compile-time API; already the `kompact-ksp` module's `compileOnly` dependency) |
| `com.google.devtools.ksp:symbol-processing-common-deps` | `2.3.12` | `KSPConfig`/`KSPJvmConfig`/`KSPCommonConfig`/`Target` builders. Pure Kotlin, only depends on `kotlin-stdlib:2.3.20` at compile scope |
| `com.google.devtools.ksp:symbol-processing-aa-embeddable` | `2.3.12` | Shaded engine jar: `KotlinSymbolProcessing`, `KSPLoader`, `KspGradleLogger`, the `KSPJvmMain`/`KSPCommonMain` cmdline classes |
| `org.jetbrains.kotlin:kotlin-stdlib` | track the consuming module's own Kotlin version (KSP's plugin uses `project.getKotlinPluginVersion()`, not its own build-time pin) | runtime |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | `1.10.2` | runtime (matches the `aa-coroutines` pin in KSP's version catalog) |

All five version strings and the "non-transitive detached configuration" pattern are taken
verbatim from KSP's own task registration, not inferred:

```kotlin
val kspAADepCfg = project.configurations.detachedConfiguration(
    project.dependencies.create("${KSP_GROUP_ID}:symbol-processing-api:$KSP_VERSION"),
    project.dependencies.create("${KSP_GROUP_ID}:symbol-processing-common-deps:$KSP_VERSION"),
    project.dependencies.create("${KSP_GROUP_ID}:symbol-processing-aa-embeddable:$KSP_VERSION"),
    project.dependencies.create("org.jetbrains.kotlin:kotlin-stdlib:${project.getKotlinPluginVersion()}"),
    project.dependencies.create("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$KSP_COROUTINES_VERSION"),
).apply { isTransitive = false }
```

`KSP_GROUP_ID` is the literal string `"com.google.devtools.ksp"`.

Sources:

- [`KspAATask.kt` detached classpath](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspAATask.kt#L206-L219)
- [`KspSubplugin.kt` group id constant](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspSubplugin.kt)
- [`symbol-processing-aa-embeddable:2.3.12` POM](https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-aa-embeddable/2.3.12/symbol-processing-aa-embeddable-2.3.12.pom)
- [`symbol-processing-common-deps:2.3.12` POM](https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-common-deps/2.3.12/symbol-processing-common-deps-2.3.12.pom)
- [Maven Central artifact listing, confirms `2.3.12` is `latest`/`release` for all three artifacts](https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-aa-embeddable/maven-metadata.xml)

### Public types and config fields (exact API)

`KSPConfig` and every platform-specific subtype live in package `com.google.devtools.ksp.processing`,
module `common-deps` (**not** `api` and **not** a standalone `cmdline` module — that module no
longer exists; its Maven artifact `symbol-processing-cmdline` stopped publishing after `2.2.21-2.0.5`,
the last version before KSP decoupled its release numbering at `2.3.0`). This package is **not**
relocated by the `-embeddable` shading (only third-party impl deps and KSP's own
`com.google.devtools.ksp.common.*` internal package are relocated under an `ksp.` prefix), so the
class names below are identical whether Kompact depends on `symbol-processing-aa` or
`symbol-processing-aa-embeddable`.

```kotlin
// package com.google.devtools.ksp.processing  (module: common-deps)
abstract class KSPConfig(
    val moduleName: String,
    val sourceRoots: List<File>,
    val commonSourceRoots: List<File>,   // present on every config type, not just common
    val libraries: List<File>,
    val friends: List<File>,
    val processorOptions: Map<String, String>,
    val projectBaseDir: File, val outputBaseDir: File, val cachesDir: File,
    val classOutputDir: File, val kotlinOutputDir: File, val resourceOutputDir: File,
    val incremental: Boolean, val incrementalContextLoggingOptions: IncrementalContextLoggingOptions,
    val modifiedSources: List<File>, val removedSources: List<File>, val changedClasses: List<String>,
    val languageVersion: String, val apiVersion: String,
    val allWarningsAsErrors: Boolean, val mapAnnotationArgumentsInJava: Boolean,
    val experimentalPsiResolution: Boolean,
) : Serializable {
    abstract class Builder { /* lateinit vars mirroring the fields above, plus
        incrementalLog / incrementalLogGraphOrigin that fold into IncrementalContextLoggingOptions */ }
}

data class Target(val platform: String, val args: Map<String, String>)

class KSPCommonConfig(
    val targets: List<Target>,   // the "common mode" marker field — see caveat below
    /* ...all KSPConfig base params... */
) : KSPConfig(/* ... */) {
    class Builder : KSPConfig.Builder(), Serializable {
        lateinit var targets: List<Target>   // required: build() throws if unset
        fun build(): KSPCommonConfig = KSPCommonConfig(targets, /* ... */)
    }
}
```

Sibling subtypes exist for every other platform the same way: `KSPJvmConfig` (+`javaSourceRoots`,
`javaOutputDir`, `jdkHome`, `jvmTarget`, `jvmDefaultMode`), `KSPNativeConfig` (+`targetName`),
`KSPJsConfig` (+`backend`). All four extend the same `KSPConfig` base and are `Serializable`.

Source: [`KSPConfig.kt`, full file](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/common-deps/src/main/kotlin/com/google/devtools/ksp/KSPConfig.kt) (base class line 26; `Target`/`KSPCommonConfig` lines 430-436; `Builder.targets` line 497; `build()` line 499)

### Processor loading and classloader contract

The documented four-step sequence (load processors, provide a logger, fill a `KSPConfig`, call
`KotlinSymbolProcessing(...).execute()`) is accurate for a self-contained program, but KSP's own
Gradle plugin uses a stricter, verified isolation contract that Kompact's task should match to
avoid classloader identity mismatches (`ClassCastException` between two different loads of
`SymbolProcessorProvider`):

1. An **isolated** `URLClassLoader` is built from exactly the 5-coordinate classpath above, parented
   to `ClassLoader.getPlatformClassLoader()` — deliberately *not* parented to the Gradle plugin's own
   classloader, so it cannot pick up a different Kotlin stdlib/compiler already on the build
   classpath. This loader is cached per classpath signature
   (`IsolatedClassLoaderCache`).
2. A **processor** `URLClassLoader` is built from the processor's own jar(s) and *parented to the
   isolated loader above* (not to the plugin or platform loader), so `SymbolProcessorProvider` resolves
   to the exact same class object on both sides of the `ServiceLoader` call.
3. `ServiceLoader.load(processorClassloader.loadClass("com.google.devtools.ksp.processing.SymbolProcessorProvider"), processorClassloader)` discovers providers via the standard `META-INF/services` contract.
4. The config is Java-serialized (`ObjectOutputStream`) and the engine is invoked **reflectively**
   across the isolation boundary: `isolatedClassLoader.loadClass("com.google.devtools.ksp.impl.KSPLoader").getMethod("loadAndRunKSP", ByteArray::class.java, List::class.java, Int::class.java)`,
   where `KSPLoader.loadAndRunKSP(bytes, providers, logLevel)` deserializes the config and calls
   `KotlinSymbolProcessing(kspConfig, providers, KspGradleLogger(logLevel)).execute().ordinal`.
   A simpler same-classloader caller can skip steps 1/2/4's isolation and call
   `KotlinSymbolProcessing(config, providers, logger).execute()` directly, as `ksp2entrypoints.md`
   shows — isolation only matters if Kompact's task classpath might otherwise clash with another
   Kotlin/KSP version in the same Gradle daemon (the Worker API `noIsolation()` path KSP itself uses
   does **not** rely on Gradle's own `classLoaderIsolation()` feature; it manages the URLClassLoaders
   manually).

Sources:

- [Calling KSP2 in programs (4-step doc)](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/docs/ksp2entrypoints.md)
- [`KspAATask.kt`, `KspAAWorkerAction.execute()`](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspAATask.kt) (isolated loader, processor loader, `ServiceLoader`, reflective `KSPLoader` call)
- [`KSPLoader.kt`, full file (40 lines)](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/kotlin-analysis-api/src/main/kotlin/com/google/devtools/ksp/impl/KSPLoader.kt)
- [`KSPCommonMain.kt`, full file — same `ServiceLoader`/execute pattern for the cmdline entry point](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/kotlin-analysis-api/src/main/kotlin/com/google/devtools/ksp/cmdline/KSPCommonMain.kt)

### Invocation mode for common sources — what actually happens

Reading `KotlinSymbolProcessing.kt`'s session-construction code directly (not inferred) shows:

- For every config type, exactly **one** Analysis-API `KtModule` (`buildKspSourceModule`) is built,
  whose source roots are `kspConfig.sourceRoots + kspConfig.commonSourceRoots` (plus
  `javaSourceRoots` for `KSPJvmConfig` only). There is no per-target module splitting inside KSP2
  itself — multi-target awareness, if any, has to come from the `platform` value attached to that
  one module.
- The `platform` for that module is selected by a `when (kspConfig)` dispatch:
  `KSPJvmConfig → JvmPlatforms.jvmPlatformByTargetVersion(...)`,
  `KSPNativeConfig → NativePlatforms.nativePlatformByTargetNames(listOf(kspConfig.targetName))`,
  `KSPJsConfig → JsPlatforms`/`WasmPlatforms`, and
  **`KSPCommonConfig → CommonPlatforms.defaultCommonPlatform`** — a single fixed platform value.
  `KSPCommonConfig.targets` is **never read** in this file (verified by exhaustive `grep` across the
  whole engine file for `.targets`/`KSPCommonConfig`: the only occurrence is this one dispatch arm).
- `SymbolProcessorEnvironment.platforms` is populated by `TargetPlatform.getPlatformInfo()`, which
  maps each `componentPlatforms` entry through `is JdkPlatform/JsPlatform/NativePlatform` branches,
  falling back to `UnknownPlatformInfoImpl(platform.toString())` for anything else. Because
  `CommonPlatforms.defaultCommonPlatform` is not one of the three typed branches, a processor running
  under `KSPCommonConfig` should not expect a typed `JvmPlatformInfo`/`NativePlatformInfo` entry
  identifying `iosArm64`/`iosSimulatorArm64`/JVM — **this specific sub-detail (whether
  `componentPlatforms` yields zero or one entry for the default common platform) was not traced into
  the Kotlin compiler's own source in this pass and is flagged as unverified**, but either way no
  typed per-target `PlatformInfo` is available.
- KSP's **own** Gradle plugin confirms the field is a known stub, not a Kompact misunderstanding —
  present identically on the released `2.3.12` tag and on `main` HEAD (commit `7697329239c660ff20cf2479f9b48f89e1e7e022`,
  fetched during this research pass) :

  ```kotlin
  // KspAATask.kt — building the per-compilation config, common branch:
  KotlinPlatformType.common -> {
      KSPCommonConfig.Builder().apply {
          this.setupSuper()
          // FIXME: targets
          targets = emptyList()
      }.build()
  }
  // ...and earlier, at config-registration time:
  // TODO: pass targets of common
  ```

- Despite that stub, `kspCommonMainMetadata` itself (the Gradle *configuration name* a processor
  dependency is added to) is real and exercised: KSP's own `integration-tests/` tree has nine
  `kmp/workload-*` fixtures (`jvm`, `js`, `wasm`, `android`, `linuxX64`, `androidNative`, plus the
  base `kmp/workload`) that all add a processor to `kspCommonMainMetadata` alongside per-target
  configurations and are run in CI. The first-party `examples/multiplatform/workload` sample still
  ships this line commented out, so the doc-facing example and the CI-exercised behavior disagree —
  the mechanism works, the convenience example is just stale/conservative.

Sources:

- [`KotlinSymbolProcessing.kt` platform dispatch, line 192](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/kotlin-analysis-api/src/main/kotlin/com/google/devtools/ksp/impl/KotlinSymbolProcessing.kt#L192)
- [same file, combined source roots, line 244](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/kotlin-analysis-api/src/main/kotlin/com/google/devtools/ksp/impl/KotlinSymbolProcessing.kt#L244)
- [same file, `getPlatformInfo`, lines 720-738](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/kotlin-analysis-api/src/main/kotlin/com/google/devtools/ksp/impl/KotlinSymbolProcessing.kt#L720-L738)
- [`KspAATask.kt`, common-config FIXME, lines 832-836](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspAATask.kt#L832-L836) and [TODO, line 473](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspAATask.kt#L473)
- Same two stubs, unchanged, confirmed directly against `main` HEAD `7697329239c660ff20cf2479f9b48f89e1e7e022` (2026-10-02) — not re-linked here since `main` is a moving ref, but reproducible by fetching the same paths at that SHA
- [KSP integration-test KMP fixtures actively using `kspCommonMainMetadata`](https://github.com/google/ksp/tree/a3c38590913b863cc6b73b41d54ff8afa625f642/integration-tests/src/test/resources/kmp) (e.g. `workload-android/build.gradle.kts`, `workload-wasm/build.gradle.kts`)
- [First-party example still disables it](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/examples/multiplatform/workload/build.gradle.kts)

### Generated output handling

No special-casing exists for common mode here either — it reuses the exact mechanism the base
"Verified facts" section above already documents (`CodeGenerator.createNewFile`, isolating vs.
aggregating outputs). What is now **source-confirmed rather than "reported"** is the exact output
path convention KSP's own plugin uses for the commonMain metadata compilation:

```kotlin
fun getKspOutputDir(project, sourceSetName, target) =
    project.layout.buildDirectory.dir("generated/ksp/$target/$sourceSetName")   // + "/kotlin", "/java", "/resources"
```

For the metadata/common compilation, `target` resolves to `"metadata"` and `sourceSetName` to
`"commonMain"`, giving `build/generated/ksp/metadata/commonMain/kotlin` — confirming the exact path
the base research above only had as a reported workaround. A Kompact-owned task is free to choose
its own output directory (it already must, per "Recommended architecture" above); this convention
only matters if Kompact wants its `kotlinOutputDir` registration to land in the same place IDEs
already expect from standard KSP usage.

Sources:

- [`getKspOutputDir`/`getKspKotlinOutputDir`](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspSubplugin.kt#L83-L96)

### Incompatibility with the current repository tuple

None of the following are hard failures; all are version-ceiling/provenance gaps worth tracking.
All version numbers below were re-verified live during this pass (not assumed from the base
research), against Maven Central, Google's Maven, Gradle's version service, and kotlinlang.org:

| Component | Repository tuple | What KSP 2.3.12 / Kotlin currently document |
| --- | --- | --- |
| Kotlin | `2.4.20` (stable, confirmed released) | KSP 2.3.12's own `gradle.properties`/`libs.versions.toml` pin `kotlinBaseVersion`/`kotlin-base = 2.3.20` for its Gradle-plugin-facing build+test matrix — one minor behind. Its Analysis-API engine (module `kotlin-analysis-api`) is separately pinned to `aa-kotlin-base = "2.4.20-dev-6138"` — a **pre-release development snapshot** of the repository's exact Kotlin line, not the final stable build. This snapshot version string is also literally embedded as a jar resource, `META-INF/ksp.compiler.version`, inside the published `symbol-processing-aa-embeddable:2.3.12` artifact. Net: plausible but unproven parsing/analysis parity with stable `2.4.20`. |
| Gradle | `9.7.1` (confirmed released, build `20260819`) | kotlinlang.org's live KGP compatibility table lists Kotlin `2.4.20`'s fully-supported Gradle range as `7.6.3–9.7.0`. `9.7.1` is one patch above that documented ceiling (Kotlin's own docs note newer Gradle/AGP "might" still work but can show deprecation warnings or miss new features). |
| AGP | `9.4.1` (confirmed released stable, not alpha) | Same table lists Kotlin `2.4.20`'s fully-supported AGP range as `8.5.2–9.3.1`. `9.4.1` is one minor above that ceiling. KSP 2.3.12's own integration matrix separately pins `agpBaseVersion`/`agp-base = "9.3.0-alpha01"` — also behind. Positive signal: KSP's gradle-plugin module already hard-codes recognition of the exact AGP plugin id the repository uses, `com.android.kotlin.multiplatform.library` (`KspSubplugin.kt`, `agpKmpPluginId`), and the `2.3.12` release notes list "Update minimum supported AGP version to 8.12.0" as a floor bump, not a ceiling — `9.4.1` clears that floor. |
| JDK | `21` | Not a KSP-specific constraint for a programmatic task: `symbol-processing-common-deps`/`symbol-processing-aa-embeddable` are plain JVM libraries with no Gradle-API dependency, so this only has to satisfy whatever JDK the Gradle 9.7.1 daemon itself requires (not re-verified in this pass beyond confirming no KSP artifact declares a higher `Bundle/Require` constraint in its POM). |
| Kotlin Gradle Plugin API | n/a | Not applicable to a Kompact-owned task: the KGP-API binary-compatibility risk only exists if Kompact reuses KSP's own `gradle-plugin` module/classes (`KotlinCompilation`, `KotlinPlatformType`, etc., which **are** compiled against `kotlin-base = 2.3.20`). Kompact's planned architecture (a bespoke task calling `KotlinSymbolProcessing` directly) never touches that module, so this specific skew does not apply to it. |

Sources (all fetched live during this pass):

- [KSP `2.3.12` release notes](https://github.com/google/ksp/releases/tag/2.3.12)
- [KSP `gradle.properties` at `2.3.12`](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle.properties)
- [KSP `gradle/libs.versions.toml` at `2.3.12`](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle/libs.versions.toml) (`kotlin-base`, `aa-kotlin-base`, `agp-base`, `aa-coroutines`)
- [`symbol-processing-aa-embeddable/build.gradle.kts`](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/symbol-processing-aa-embeddable/build.gradle.kts) (shading prefix list; `generateKSPVersions` task writing `META-INF/ksp.compiler.version` from `aaKotlinBaseVersion`)
- [Kotlin/Gradle/AGP compatibility table (live)](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Kotlin release-line table confirming `2.4.20` is the current stable release (live)](https://kotlinlang.org/docs/releases.html)
- [Gradle version service, confirms `9.7.1` released `2026-08-19`](https://services.gradle.org/versions/all)
- [Google Maven AGP metadata, confirms `9.4.1` is a released stable version](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml)
- [`KspSubplugin.kt`, `agpKmpPluginId` constant and `2.3.12` AGP floor bump](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspSubplugin.kt)

### What this changes in the base research above

The `:kompact-gradle-plugin` TestKit fixture now supplies the runnable behavioral proof that the source research alone could not: KSPCommonConfig processes a real `commonMain` schema; the generated common declarations are consumed by JVM, iOS Arm64, iOS Simulator Arm64, and Android Native Arm64 compilations; and cache/configuration-cache behavior is exercised. The exact contents of `CommonPlatforms.defaultCommonPlatform.componentPlatforms` remain unverified because the plugin does not depend on that field to select output targets. Parsing parity between the stable Kotlin compiler and KSP's embedded analysis snapshot remains a compatibility risk.

### Unverified claims in this addendum

- `CommonPlatforms.defaultCommonPlatform.componentPlatforms`'s exact contents (empty list vs. a
  single non-Jdk/Js/Native marker) — would require reading the Kotlin compiler's own source, out of
  scope for a KSP-focused pass.
- Whether `symbol-processing-aa` (non-embeddable) behaves identically for this purpose — only the
  `-embeddable` variant's shading config was inspected directly; `ksp2entrypoints.md` states they
  are otherwise equivalent, which this pass did not independently re-derive.
- The Gradle daemon's exact minimum/maximum JDK support window for Gradle `9.7.1` was not
  independently re-verified against Gradle's own documentation in this pass; JDK 21 is assumed
  compatible based on general Gradle 9.x JDK-support knowledge, not a fetched source.
