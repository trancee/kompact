# KSP common-schema generation across Android and iOS

## Question

Can Kompact process each `commonMain` schema once and generate Kotlin that the
Android, JVM, `iosArm64`, and `iosSimulatorArm64` compilations all consume? Can
it also emit deterministic C99 headers and keep sound diagnostics, incremental
processing, build-cache reuse, IDE visibility, and publication?

## Conclusion

The standard KSP Gradle integration does not provide that contract. Its
documented KMP model creates a processing task for every configured
compilation, so each target configuration processes the shared sources again.
`kspCommonMainMetadata` exists, but wiring its generated common sources into
the build is an open upstream problem and depends on fragile manual task
relationships.

Kompact solves common Kotlin generation with its own Gradle plugin,
`:kompact-gradle-plugin`. The latest release is `0.10.0`, and this checkout is
`0.11.0-SNAPSHOT`. The plugin's cacheable task calls KSP2 once through the
`KSPCommonConfig` path. Since
[ADR-0009](../adr/0009-common-only-code-generation.md), the task writes one
output directory and registers it only with `commonMain` through a task-backed
provider. The plugin implementation and its marker are published with the
runtime and the processor.

The plugin does not generate C99 headers. That part of the original question
remains out of scope. Do not also apply standard target-specific KSP
processing to the same Kompact schemas.

## Verified facts

### Supported Kotlin and KSP versions

The consumer TestKit fixture in
`kompact-gradle-plugin/src/test/resources/kmp-consumer` runs Kotlin Gradle
Plugin `2.4.21` with the bundled KSP2 engine `2.3.12`. That pair is the tested
pair. During configuration, the plugin reads the Kotlin Gradle Plugin's
implementation version and compares it with the tested version:

- A different patch release of the same Kotlin minor line logs a warning.
- A different minor line fails the build.

Both messages name the detected and the tested versions.
[ADR-0012](../adr/0012-kotlin-minor-line-compatibility.md) records this
decision.

Before generation, the task also looks up the bundled KSP2 entry point
`KSPLoader.loadAndRunKSP(byte[], List, int)`. If the entry point is missing or
incompatible, the task fails and reports the version pair, the broken entry
point, and the reflection cause.

During test-resource processing, the build expands the fixture's plugin and
dependency versions from the repository version catalog and the plugin
metadata. A toolchain update therefore also updates the consumer under test.

The processor reports an error for an annotated model that has no
`@KompactField` fields and for a resolved field that is invalid. It defers
field types that are not resolved yet and keeps processing independent valid
models. An invalid model produces no generated files. Properties without
`@KompactField` stay outside the wire schema on purpose.

### Standard KMP processing runs once per compilation

KSP's official KMP guide requires a processor dependency for each target that
needs processing. KSP then creates a symbol-processing task for every
configured Kotlin compilation. The guide's example has at least ten processing
tasks for its configured targets. Applying `kspAndroid`, `kspIosArm64`, and
`kspIosSimulatorArm64` would therefore process the declarations visible to
each compilation more than once, not once for the whole build.

Sources:

- [KSP with Kotlin Multiplatform](https://kotlinlang.org/docs/ksp-multiplatform.html)
- [KSP repository configuration reference](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/README.md#kotlin-multiplatform-kmp)

### Common metadata generation is not a stable integration point

The KSP configuration reference lists `kspCommonMainMetadata`. The first-party
multiplatform example leaves that configuration commented out and shows
target-specific processing instead.

On 2026-10-07, two upstream requests were still open: first-class common
generation ([`google/ksp#567`](https://github.com/google/ksp/issues/567)) and
iOS hierarchy support ([`google/ksp#929`](https://github.com/google/ksp/issues/929)).
The linked issue about IDE and task dependencies
([`google/ksp#963`](https://github.com/google/ksp/issues/963)) is closed. The
other linked workaround reports describe failures with particular KSP and
Gradle versions, and those failures may no longer occur. The workarounds
manually add `build/generated/ksp/metadata/commonMain/kotlin` and task
dependencies.

This evidence does not prove that `kspCommonMainMetadata` can never work. It
does show that Kompact cannot treat manual wiring as a supported, stable
interface for a published generator.

Sources:

- [First-party multiplatform example](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/examples/multiplatform/workload/build.gradle.kts)
- [Generating common code, open upstream issue](https://github.com/google/ksp/issues/567)
- [Generating into shared iOS source sets, open upstream issue](https://github.com/google/ksp/issues/929)
- [IDE and task dependency history](https://github.com/google/ksp/issues/963)

### KSP2 can run outside the compiler task

KSP2 is no longer a Kotlin compiler plugin. Its documented programmatic
interface loads processor providers, builds a `KSPConfig`, and calls
`KotlinSymbolProcessing.execute()`. Its command-line distribution has separate
JVM, JS, Native, and Common entry points. Kompact can therefore run one
schema-processing step independently of the target compilation tasks.

KSP 2.3.0 also decoupled KSP's release version from Kotlin's compiler version.
The decoupling reduces version lockstep. It does not show that every KSP
release works with every Kotlin release.

Sources:

- [KSP2 architecture](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/docs/ksp2.md)
- [Calling KSP2 in programs](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/docs/ksp2entrypoints.md)
- [KSP2 command-line entry points](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/docs/ksp2cmdline.md)
- [KSP 2.3.0 release](https://github.com/google/ksp/releases/tag/2.3.0)

### Kotlin and C outputs can share KSP dependency tracking

`CodeGenerator.createNewFile` and `createNewFileByPath` accept a file
extension. Kotlin and Java outputs take part in later compilation. KSP's
incremental processing still manages files with other extensions. Kompact can
therefore emit `.kt` and `.h` files through the same processor without
writing unmanaged files.

Each output must declare the source files that contribute to it. KSP separates
isolating outputs from aggregating outputs. Per-schema Kotlin and C files can
be isolating. A registry or umbrella header that depends on every schema is
aggregating and must declare that it is.

Sources:

- [KSP `CodeGenerator` interface](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/api/src/main/kotlin/com/google/devtools/ksp/processing/CodeGenerator.kt)
- [KSP incremental processing](https://kotlinlang.org/docs/ksp-incremental.html)

### Diagnostics can point at schema symbols

`KSPLogger.error`, `warn`, `info`, and `logging` accept an optional `KSNode`.
An error stops processing after the current round, and KSP then calls
`onError()` instead of `finish()`. Kompact should attach every schema
diagnostic to the narrowest offending declaration and should not throw for
expected validation failures.

Sources:

- [KSP `KSPLogger` interface](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/api/src/main/kotlin/com/google/devtools/ksp/processing/KSPLogger.kt)
- [KSP multiple-round error handling](https://kotlinlang.org/docs/ksp-multi-round.html#error-and-exception-handling)

### Build-cache and IDE correctness belong to Kompact's Gradle plugin

Gradle requires a cacheable task to declare all of its inputs and outputs.
File inputs need path normalization so that cache entries stay valid when the
project moves. Consumers should receive `Provider` values backed by the
producing task, so that the task dependency travels with the files. Raw
build-directory strings create implicit dependencies, and the build then
depends on task order.

KSP 2.3.11 includes a build-cache miss fix and Gradle isolated-project
support. Those fixes do not make an external Kompact task cacheable on their
own.

Sources:

- [Gradle build cache](https://docs.gradle.org/current/userguide/build_cache.html#sec:task_output_caching)
- [Gradle implicit dependency guidance](https://docs.gradle.org/current/userguide/validation_problems.html#implicit_dependency)
- [KSP 2.3.11 release](https://github.com/google/ksp/releases/tag/2.3.11)

### Generated code ships in normal KMP artifacts

Kotlin Multiplatform publishes a root metadata artifact and target-specific
artifacts. A common generated source that the build registers before
compilation becomes part of those compiled publications. Android publication
still needs explicit configuration. C headers are not a KMP target artifact
and need their own classified archive or consumable Gradle variant. Publish
all publications from one host to avoid duplicate coordinates.

Source: [Publish a Kotlin Multiplatform library](https://kotlinlang.org/docs/multiplatform-publish-lib.html)

## Current Kompact implementation

This section describes the build as of 2026-10-18, after
[ADR-0009](../adr/0009-common-only-code-generation.md). Before ADR-0009, the
task routed output into `common`, `jvm`, `ios`, and `androidArm64` source
directories, fixed-layout schemas kept a handwritten common
`expect value class`, and framed schemas generated a common `expect` view with
per-platform actuals. None of that remains.

Generation uses three published modules and one JVM integration-test module:

1. `:kompact` is the KMP runtime and holds the public annotations.
2. `:kompact-ksp` is the JVM KSP processor. It validates schemas and generates
   Kotlin.
3. `:kompact-gradle-plugin` is the JVM Gradle plugin
   `ch.trancee.kompact.codegen`. It registers the cacheable
   `generateKompactSources` task, implemented by `GenerateKompactSources`. The
   task runs `symbol-processing-aa-embeddable` once in common mode and writes
   one `<Name>View.kt` file per model to `build/generated/kompact/main`.
4. `:kompact-ksp-integration` compiles generated code and runs runtime tests
   for the JVM processor path.

The task declares these inputs and outputs:

- the `commonMain` Kotlin source roots, minus the generated directory, as
  `@InputFiles` with relative path sensitivity and normalized line endings;
- the common metadata compilation libraries, read from
  `compileCommonMainKotlinMetadata`, as `@Classpath`;
- the KSP engine artifacts and the processor artifact as `@Classpath`;
- the module name, the Kotlin and KSP versions, the language and API versions,
  and `allWarningsAsErrors` as `@Input` values;
- `build/generated/kompact/main` as `@OutputDirectory`, and
  `build/kspCaches/kompact/commonMain` as `@LocalState`.

The plugin registers the output directory only with `commonMain`, through the
task's provider. It does not inspect Kotlin targets, add compiler arguments,
or pass processor options. Fixed-layout and framed schemas are both regular
annotated classes, and each one generates ordinary common declarations with no
`expect` or `actual`. The task runs with KSP incremental processing turned off
(`incremental = false`) and relies on Gradle up-to-date checks and the build
cache instead.

The task uses the five engine coordinates and the class loader layout that the
addendum below describes. The engine loader's parent is the platform class
loader, and the processor loader is its child. The task passes
`targets = emptyList()` to `KSPCommonConfig.Builder`. Unlike KSP's own
plugin, Kompact pins `kotlin-stdlib` to its tested Kotlin version from
`gradle/libs.versions.toml`, not to the consuming project's Kotlin version.

Do not also add the processor to `kspAndroid`, `kspIosArm64`, or
`kspIosSimulatorArm64`. That would repeat common processing and can produce
duplicate generated declarations.

## Research version baseline

The table below records the conservative intersection used during the original
research. It is not the repository's current toolchain and is not an
instruction to downgrade. The repository now uses Kotlin 2.4.21, KSP 2.3.12,
Gradle 9.8.0, and AGP 9.4.1. Before you build a custom integration, check the
official compatibility ranges in force at that time. Keep the repository's
stable toolchain unless a documented blocker requires holding a version back.

The original source-verified intersection was:

| Tool | Baseline |
| --- | --- |
| Kotlin | 2.3.20 |
| KSP2 | 2.3.11 |
| Gradle | 9.3.0 |
| Android Gradle Plugin | 9.0.0 |
| Gradle runtime JDK | 17 |
| Apple targets | `iosArm64`, `iosSimulatorArm64` |

At research time, KSP's source build used Kotlin 2.3.20, and KSP 2.3.11 was
the newest published release. Kotlin's compatibility table fully supported
Kotlin 2.3.20 with Gradle through 9.3.0 and AGP through 9.0.0. KSP's release
number is independent of Kotlin, but that independence is not evidence for an
untested Kotlin 2.4.x pairing. Promote a newer tuple only after the
integration matrix below passes unchanged.

Sources:

- [KSP version catalog](https://github.com/google/ksp/blob/a2738285ab7835fb0738ac45645c4d3365f753f9/gradle/libs.versions.toml)
- [Kotlin, Gradle, and AGP compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin)
- [KSP 2.3.11 release](https://github.com/google/ksp/releases/tag/2.3.11)

## Integration coverage and remaining proof

The committed tests in `kompact-gradle-plugin` and `kompact-ksp-integration`
prove the following:

- The TestKit KMP consumer generates exactly one common `<Name>View.kt` per
  schema. The same consumer runs `:jvmTest` and compiles `:compileAndroidMain`,
  `iosArm64`, `iosSimulatorArm64`, and `androidNativeArm64` sources.
- The consumer's common tests use generated framed and fixed-layout views and
  holders.
- A relocated consumer restores the generator output `FROM-CACHE`, and the
  restored Kotlin is byte-identical.
- A repeated generation run reuses the configuration cache after the
  dependency classpath settles.
- Deleting the last common schema removes stale generated output.
- The JVM integration fixture compiles and round-trips nested, repeated, blob,
  and string fields.

These fixtures do not prove the following:

- final Apple binary linking and testing, which requires macOS;
- Gradle IDE model import;
- resolution of the published plugin artifacts from a clean external consumer;
- behavior under parallel-target races;
- C-header generation.

The standard KSP path and the Kompact plugin are different paths. The KMP
TestKit consumer covers only the Kompact plugin.

## Unsupported assumptions

The evidence above rules out each of these assumptions:

- Standard target-specific KSP tasks do not process `commonMain` once.
- `kspCommonMainMetadata` does not currently provide a documented, automatic,
  stable generated-source connection for every KMP target and publication.
- KSP does not make custom C outputs deterministic. The processor must sort
  and normalize them.
- KSP dependency metadata does not replace Gradle task input and output
  declarations.
- Adding a generated directory as a raw path does not create the required
  task dependency.
- KMP publication does not publish C headers automatically.

## Remaining risks

Upstream common-generation issues remain open, so Kompact owns more Gradle
integration than a normal target-specific KSP processor does. The task uses
KSP2's reflective `KSPLoader` entry point and reads metadata libraries from
KGP's `compileCommonMainKotlinMetadata` task. Both points need compatibility
tests on every KSP or KGP upgrade. KSP 2.3.12's analysis engine embeds a
Kotlin 2.4.20 development snapshot, so parsing parity with stable Kotlin
remains a risk. Final Apple binary linking and testing still require macOS,
even though common generation itself runs on the JVM.

## Addendum: KSP 2.3.12 common-processing API (focused prototype findings)

This addendum examines the KSP2 common-processing API that a Kompact-owned
task calls, which "Remaining risks" above left open. It covers the repository tuple
at the time of the pass: Kotlin 2.4.20, KSP 2.3.12, Gradle 9.8.0, AGP 9.4.1,
and JDK 21. The repository has since moved to Kotlin 2.4.21.

The pass checked every claim against the KSP 2.3.12 tag
([`a3c38590`](https://github.com/google/ksp/tree/a3c38590913b863cc6b73b41d54ff8afa625f642),
commit `a3c38590913b863cc6b73b41d54ff8afa625f642`), Maven Central artifact
metadata, and the kotlinlang.org compatibility pages in force at the time. No
codebase-memory MCP access was used or available for this pass. All claims
below come from primary sources: KSP source, releases, and artifact metadata,
Gradle's version service, Google's Maven metadata, and kotlinlang.org.

### Addendum conclusion

KSP2 exposes a real, reachable common-processing configuration type,
`KSPCommonConfig`. It runs through the same analysis engine, `CodeGenerator`,
and incremental machinery as every other `KSPConfig` subtype, so a dedicated
Kompact task can call it directly.

One field does not work. `KSPCommonConfig.targets` would tell a processor
which concrete platforms (JVM, `iosArm64`, and `iosSimulatorArm64`) consume
the common output. The type accepts the field, but the KSP 2.3.12 execution
engine never reads it. KSP's own first-party Gradle plugin fills it with a
hard-coded empty list behind a `// FIXME: targets` comment. KSP's `main`
branch was unchanged at the time of this pass.

Treat common-mode processing as viable for seeing `expect` declarations and
emitting one combined generated-Kotlin output. Treat `targets`, and any
per-platform awareness inside KSP2 itself, as non-functional. At the time of
this pass, the plan was for Kompact's task to derive per-platform knowledge
itself, through scalar processor-option inputs that the task owns, and not
through this KSP2 field.

Since [ADR-0009](../adr/0009-common-only-code-generation.md) (2026-10-18),
Kompact needs no per-platform knowledge. The task passes no processor options
and generates common code only.

### Exact Maven coordinates

KSP's own Gradle plugin builds a detached, non-transitive classpath. It runs
`KSPCommonConfig`, and every other config type, from that classpath instead of
its own plugin classpath. The same five coordinates are the minimal set that
Kompact's task needs, and `:kompact-gradle-plugin` uses exactly these five.

| Coordinate | Verified version | Role |
| --- | --- | --- |
| `com.google.devtools.ksp:symbol-processing-api` | `2.3.12` | `SymbolProcessorProvider`, `SymbolProcessor`, `CodeGenerator`, `KSPLogger` (compile-time API; already the `kompact-ksp` module's `compileOnly` dependency) |
| `com.google.devtools.ksp:symbol-processing-common-deps` | `2.3.12` | `KSPConfig`, `KSPJvmConfig`, `KSPCommonConfig`, and `Target` builders. Pure Kotlin. Depends only on `kotlin-stdlib:2.3.20` at compile scope |
| `com.google.devtools.ksp:symbol-processing-aa-embeddable` | `2.3.12` | Shaded engine jar: `KotlinSymbolProcessing`, `KSPLoader`, `KspGradleLogger`, and the `KSPJvmMain` and `KSPCommonMain` command-line classes |
| `org.jetbrains.kotlin:kotlin-stdlib` | track the consuming module's own Kotlin version (KSP's plugin uses `project.getKotlinPluginVersion()`, not its own build-time pin) | runtime |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | `1.10.2` | runtime (matches the `aa-coroutines` pin in KSP's version catalog) |

All five version strings and the "non-transitive detached configuration"
pattern come verbatim from KSP's own task registration, not from inference:

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
- [Maven Central artifact listing, which confirms that `2.3.12` is `latest` and `release` for all three artifacts](https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-aa-embeddable/maven-metadata.xml)

### Public types and config fields

`KSPConfig` and every platform-specific subtype live in package
`com.google.devtools.ksp.processing`, in module `common-deps`. They are not in
`api`, and they are not in a standalone `cmdline` module. That module no longer
exists. Its Maven artifact `symbol-processing-cmdline` stopped publishing after
`2.2.21-2.0.5`, the last version before KSP decoupled its release numbering at
`2.3.0`.

The `-embeddable` shading does not relocate this package. Shading relocates
only third-party implementation dependencies and KSP's own internal
`com.google.devtools.ksp.common.*` package, under a `ksp.` prefix. The class
names below are therefore identical whether Kompact depends on
`symbol-processing-aa` or on `symbol-processing-aa-embeddable`.

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

Each other platform has a sibling subtype built the same way:

- `KSPJvmConfig` adds `javaSourceRoots`, `javaOutputDir`, `jdkHome`,
  `jvmTarget`, and `jvmDefaultMode`.
- `KSPNativeConfig` adds `targetName`.
- `KSPJsConfig` adds `backend`.

All four subtypes extend the same `KSPConfig` base and are `Serializable`.

Source: [`KSPConfig.kt`, full file](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/common-deps/src/main/kotlin/com/google/devtools/ksp/KSPConfig.kt)
(base class line 26; `Target` and `KSPCommonConfig` lines 430-436;
`Builder.targets` line 497; `build()` line 499)

### Processor loading and the class loader contract

The documented four-step sequence is accurate for a self-contained program:
load processors, provide a logger, fill a `KSPConfig`, and call
`KotlinSymbolProcessing(...).execute()`. KSP's own Gradle plugin uses a
stricter, verified isolation contract. Kompact's task should match it to avoid
class identity mismatches, which show up as a `ClassCastException` between two
different loads of `SymbolProcessorProvider`. The contract has four steps:

1. Build an isolated `URLClassLoader` from exactly the five-coordinate
   classpath above. Its parent is `ClassLoader.getPlatformClassLoader()`, not
   the Gradle plugin's own class loader, so it cannot pick up a different
   Kotlin stdlib or compiler that is already on the build classpath. KSP
   caches this loader per classpath signature (`IsolatedClassLoaderCache`).
2. Build a processor `URLClassLoader` from the processor's own jar files. Its
   parent is the isolated loader from step 1, not the plugin or platform
   loader, so `SymbolProcessorProvider` resolves to the same class object on
   both sides of the `ServiceLoader` call.
3. Discover providers through the standard `META-INF/services` contract with
   `ServiceLoader.load(processorClassloader.loadClass("com.google.devtools.ksp.processing.SymbolProcessorProvider"), processorClassloader)`.
4. Java-serialize the config with `ObjectOutputStream` and invoke the engine
   reflectively across the isolation boundary:
   `isolatedClassLoader.loadClass("com.google.devtools.ksp.impl.KSPLoader").getMethod("loadAndRunKSP", ByteArray::class.java, List::class.java, Int::class.java)`.
   `KSPLoader.loadAndRunKSP(bytes, providers, logLevel)` deserializes the
   config and calls
   `KotlinSymbolProcessing(kspConfig, providers, KspGradleLogger(logLevel)).execute().ordinal`.

A simpler caller that uses one class loader can skip the isolation in steps 1,
2, and 4. It can call `KotlinSymbolProcessing(config, providers, logger).execute()`
directly, as `ksp2entrypoints.md` shows. Isolation matters only if Kompact's
task classpath might clash with another Kotlin or KSP version in the same
Gradle daemon. KSP itself runs the Worker API with `noIsolation()`. It does
not rely on Gradle's own `classLoaderIsolation()` feature and manages the
`URLClassLoader` instances by hand.

Sources:

- [Calling KSP2 in programs (4-step doc)](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/docs/ksp2entrypoints.md)
- [`KspAATask.kt`, `KspAAWorkerAction.execute()`](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspAATask.kt)
  (isolated loader, processor loader, `ServiceLoader`, reflective `KSPLoader` call)
- [`KSPLoader.kt`, full file (40 lines)](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/kotlin-analysis-api/src/main/kotlin/com/google/devtools/ksp/impl/KSPLoader.kt)
- [`KSPCommonMain.kt`, full file, which uses the same `ServiceLoader` and execute pattern for the command-line entry point](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/kotlin-analysis-api/src/main/kotlin/com/google/devtools/ksp/cmdline/KSPCommonMain.kt)

### What common mode does with common sources

The pass read the session-construction code in `KotlinSymbolProcessing.kt`
directly, without inference. It found the following:

- For every config type, KSP2 builds exactly one Analysis API `KtModule`
  (`buildKspSourceModule`). Its source roots are
  `kspConfig.sourceRoots + kspConfig.commonSourceRoots`, plus
  `javaSourceRoots` for `KSPJvmConfig` only. KSP2 does not split modules per
  target. Any multi-target awareness has to come from the `platform` value
  attached to that one module.
- A `when (kspConfig)` dispatch selects the `platform` for that module:
  - `KSPJvmConfig` maps to `JvmPlatforms.jvmPlatformByTargetVersion(...)`.
  - `KSPNativeConfig` maps to
    `NativePlatforms.nativePlatformByTargetNames(listOf(kspConfig.targetName))`.
  - `KSPJsConfig` maps to `JsPlatforms` or `WasmPlatforms`.
  - `KSPCommonConfig` maps to `CommonPlatforms.defaultCommonPlatform`, a
    single fixed platform value.

  The file never reads `KSPCommonConfig.targets`. An exhaustive `grep` of the
  whole engine file for `.targets` and `KSPCommonConfig` found only this one
  dispatch arm.
- `TargetPlatform.getPlatformInfo()` fills
  `SymbolProcessorEnvironment.platforms`. It maps each `componentPlatforms`
  entry through `is JdkPlatform`, `is JsPlatform`, and `is NativePlatform`
  branches, and falls back to `UnknownPlatformInfoImpl(platform.toString())`
  for anything else. `CommonPlatforms.defaultCommonPlatform` matches none of
  the three typed branches. A processor running under `KSPCommonConfig`
  should therefore not expect a typed `JvmPlatformInfo` or `NativePlatformInfo`
  entry that identifies `iosArm64`, `iosSimulatorArm64`, or the JVM. This pass
  did not trace whether `componentPlatforms` yields zero entries or one entry
  for the default common platform into the Kotlin compiler's own source, so
  that sub-detail is unverified. In either case, no typed per-target
  `PlatformInfo` is available.
- KSP's own Gradle plugin confirms that the field is a known stub and not a
  Kompact misunderstanding. The stub is identical on the released `2.3.12` tag
  and on `main` HEAD (commit `7697329239c660ff20cf2479f9b48f89e1e7e022`,
  fetched during this pass):

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

- Despite that stub, `kspCommonMainMetadata` itself is real and exercised.
  `kspCommonMainMetadata` is the Gradle configuration name that a processor
  dependency is added to. KSP's own `integration-tests/` tree has nine
  `kmp/workload-*` fixtures, including `jvm`, `js`, `wasm`, `android`,
  `linuxX64`, `androidNative`, and the base `kmp/workload`. All of them add a
  processor to `kspCommonMainMetadata` alongside per-target configurations,
  and KSP's CI runs them. The first-party `examples/multiplatform/workload`
  sample still ships that line commented out. The documented example and the
  behavior that CI exercises therefore disagree. The mechanism works. The
  example is stale or conservative.

Sources:

- [`KotlinSymbolProcessing.kt` platform dispatch, line 192](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/kotlin-analysis-api/src/main/kotlin/com/google/devtools/ksp/impl/KotlinSymbolProcessing.kt#L192)
- [same file, combined source roots, line 244](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/kotlin-analysis-api/src/main/kotlin/com/google/devtools/ksp/impl/KotlinSymbolProcessing.kt#L244)
- [same file, `getPlatformInfo`, lines 720-738](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/kotlin-analysis-api/src/main/kotlin/com/google/devtools/ksp/impl/KotlinSymbolProcessing.kt#L720-L738)
- [`KspAATask.kt`, common-config FIXME, lines 832-836](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspAATask.kt#L832-L836)
  and [TODO, line 473](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspAATask.kt#L473)
- The same two stubs, unchanged, confirmed directly against `main` HEAD
  `7697329239c660ff20cf2479f9b48f89e1e7e022` (2026-10-02). They are not linked
  here because `main` is a moving ref. Fetch the same paths at that SHA to
  reproduce the check.
- [KSP integration-test KMP fixtures that use `kspCommonMainMetadata`](https://github.com/google/ksp/tree/a3c38590913b863cc6b73b41d54ff8afa625f642/integration-tests/src/test/resources/kmp)
  (for example `workload-android/build.gradle.kts` and
  `workload-wasm/build.gradle.kts`)
- [First-party example still disables it](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/examples/multiplatform/workload/build.gradle.kts)

### Generated output handling

Common mode has no special handling for outputs either. It reuses the
mechanism that "Verified facts" already documents: `CodeGenerator.createNewFile`
with isolating and aggregating outputs. The source now confirms, rather than
reports, the output path convention that KSP's own plugin uses for the
`commonMain` metadata compilation:

```kotlin
fun getKspOutputDir(project, sourceSetName, target) =
    project.layout.buildDirectory.dir("generated/ksp/$target/$sourceSetName")   // + "/kotlin", "/java", "/resources"
```

For the metadata compilation, `target` resolves to `"metadata"` and
`sourceSetName` to `"commonMain"`. The result is
`build/generated/ksp/metadata/commonMain/kotlin`, the exact path that the
base research had only from a reported workaround. A Kompact-owned task
chooses its own output directory. This convention matters only if Kompact
wants its `kotlinOutputDir` registration to land where IDEs already expect
standard KSP output. Kompact's plugin uses `build/generated/kompact/main`.

Sources:

- [`getKspOutputDir` and `getKspKotlinOutputDir`](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspSubplugin.kt#L83-L96)

### Incompatibility with the repository tuple on 2026-10-04

None of the gaps below is a hard failure. All of them are version-ceiling or
provenance gaps worth tracking. The pass checked the repository tuple against
its version catalog, wrapper, and CI configuration on 2026-10-04, and checked
Kotlin's compatibility bounds against the live documentation on the same
date. The repository has since moved from Kotlin `2.4.20` to `2.4.21`. The
table keeps the tuple as checked.

| Component | Repository tuple | What KSP 2.3.12 and Kotlin documented |
| --- | --- | --- |
| Kotlin | `2.4.20` (stable, confirmed released) | KSP 2.3.12's own `gradle.properties` and `libs.versions.toml` pin `kotlinBaseVersion` and `kotlin-base` to `2.3.20` for the build and test matrix of its Gradle plugin, one minor behind. Its Analysis API engine (module `kotlin-analysis-api`) is pinned separately to `aa-kotlin-base = "2.4.20-dev-6138"`. That version is a pre-release development snapshot of the repository's exact Kotlin line, not the final stable build. The published `symbol-processing-aa-embeddable:2.3.12` artifact also embeds this snapshot version string as the jar resource `META-INF/ksp.compiler.version`. Parsing and analysis parity with stable `2.4.20` is plausible but unproven. |
| Gradle | `9.8.0` (wrapper; released 2026-09-24) | The kotlinlang.org KGP compatibility table lists `7.6.3–9.7.0` as Kotlin `2.4.20`'s fully supported Gradle range. The wrapper is one minor version above that documented ceiling. Kotlin's docs note that newer Gradle and AGP versions "might" still work but can show deprecation warnings or miss new features. |
| AGP | `9.4.1` (confirmed released stable, not alpha) | The same table lists `8.5.2–9.3.1` as Kotlin `2.4.20`'s fully supported AGP range. `9.4.1` is one minor above that ceiling. KSP 2.3.12's own integration matrix separately pins `agpBaseVersion` and `agp-base` to `"9.3.0-alpha01"`, which is also behind. One positive signal: KSP's Gradle plugin module already recognizes the exact AGP plugin ID that the repository uses, `com.android.kotlin.multiplatform.library` (`KspSubplugin.kt`, `agpKmpPluginId`). The `2.3.12` release notes list "Update minimum supported AGP version to 8.12.0" as a floor bump, not a ceiling, and `9.4.1` clears that floor. |
| JDK | `21` | CI runs Gradle 9.8.0 on JDK 21. The pass did not check the exact Gradle runtime JDK range for this tuple. No KSP artifact declares a higher `Bundle/Require` constraint in its POM. |
| Kotlin Gradle Plugin API | n/a | Does not apply to a Kompact-owned task. The KGP API binary-compatibility risk exists only if Kompact reuses KSP's own `gradle-plugin` module or its classes (`KotlinCompilation`, `KotlinPlatformType`, and others), which are compiled against `kotlin-base = 2.3.20`. Kompact's task calls the KSP engine without touching that module, so this skew does not apply. |

Sources for this version comparison:

- [KSP `2.3.12` release notes](https://github.com/google/ksp/releases/tag/2.3.12)
- [KSP `gradle.properties` at `2.3.12`](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle.properties)
- [KSP `gradle/libs.versions.toml` at `2.3.12`](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle/libs.versions.toml)
  (`kotlin-base`, `aa-kotlin-base`, `agp-base`, `aa-coroutines`)
- [`symbol-processing-aa-embeddable/build.gradle.kts`](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/symbol-processing-aa-embeddable/build.gradle.kts)
  (shading prefix list; the `generateKSPVersions` task writes
  `META-INF/ksp.compiler.version` from `aaKotlinBaseVersion`)
- [Kotlin/Gradle/AGP compatibility table (live)](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Kotlin release-line table, which confirmed `2.4.20` as the stable release at the time (live)](https://kotlinlang.org/docs/releases.html)
- [Gradle version service, which confirms that `9.8.0` was released on `2026-09-24`](https://services.gradle.org/versions/all)
- [Google Maven AGP metadata, which confirms that `9.4.1` is a released stable version](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml)
- [`KspSubplugin.kt`, `agpKmpPluginId` constant and the `2.3.12` AGP floor bump](https://github.com/google/ksp/blob/a3c38590913b863cc6b73b41d54ff8afa625f642/gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspSubplugin.kt)

### What the addendum changes in the base research

The `:kompact-gradle-plugin` TestKit fixture now supplies the runnable
behavioral proof that source reading alone could not. `KSPCommonConfig`
processes a real `commonMain` schema. The JVM, iOS Arm64, iOS Simulator Arm64,
and Android Native Arm64 compilations consume the generated common
declarations. The fixture also exercises build-cache and configuration-cache
behavior.

The exact contents of `CommonPlatforms.defaultCommonPlatform.componentPlatforms`
remain unverified, because the plugin does not depend on that field. Parsing
parity between the stable Kotlin compiler and KSP's embedded analysis snapshot
remains a compatibility risk.

### Unverified claims in this addendum

The following claims remain unverified:

- The exact contents of
  `CommonPlatforms.defaultCommonPlatform.componentPlatforms`: an empty list or
  a single marker that is not a JDK, JS, or Native platform. Checking this
  requires reading the Kotlin compiler's own source, which was out of scope
  for a KSP-focused pass.
- Whether `symbol-processing-aa`, the non-embeddable variant, behaves
  identically for this purpose. The pass inspected only the shading
  configuration of the `-embeddable` variant. `ksp2entrypoints.md` states that
  the two are otherwise equivalent, and this pass did not re-derive that
  independently.
- The exact minimum and maximum JDK support window for the Gradle `9.8.0`
  daemon. The pass did not check it against Gradle's own documentation. JDK 21
  is assumed compatible based on general knowledge of Gradle 9.x JDK support,
  not on a fetched source.
