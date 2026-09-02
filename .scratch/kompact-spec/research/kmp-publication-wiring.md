# KMP/KSP publication wiring — research (Ticket 13)

Researched against current primary sources (Kotlin 2.x / KSP 2.x). Versions as of the docs' last-modified dates and the Maven Central index: KSP release **2.3.11** (github.com/google/ksp/releases, published 2026-08-03); the official KSP quickstart (kotlinlang.org, dated 12 August 2026) carries `com.google.devtools.ksp` **2.3.10** + Kotlin **2.4.10** + a tip to read the GitHub Releases for the latest version. The brief's floor "KSP 2.3.9+" is consistent. `org.jetbrains.kotlinx.binary-compatibility-validator` latest published stable on Maven Central = **0.18.0**; the plugin README references 0.18.1. See "Corrections to docs/research" at the end.

## (a) Publishing the KMP runtime `:kompact`

**Canonical plugin set** (source: Kotlin Multiplatform Help, "Setting up multiplatform library publication"):

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("maven-publish")          // KGP auto-registers publications from this
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.0"
}
```

> Note / decision: there is no hand-authored `multiplatformPublication {}` or `mavenPublish {}` DSL in the Kotlin Gradle plugin. The KGP auto-creates the publications when `maven-publish` is applied.

**Target declaration** (sources: same page + tutorial):

```kotlin
kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    // Android (library, published via KMP — not a separate AGP module):
    // androidLibrary {
    //     namespace = "ch.trancee.kompact"
    //     compileSdk = ... ; minSdk = ...
    //     withJava()              // opt-in to Java compilation support
    //     compilations.configureEach { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
    // }
}
```

**Publication model — what actually ships.** Source: "Structure of publications" — "When used with `maven-publish`, the Kotlin plugin automatically creates publications for each target that can be built on the current host, plus an umbrella root publication, `kotlinMultiplatform`, that represents the entire library ... The root publication serves as an entry point that references all target-specific publications: expected URLs and coordinates for individual platform artifacts."

- Per-target publications: `<project>-jvm` (`.jar`), `<project>-iosarm64` (`.klib`), `<project>-iossimulatorarm64` (`.klib`). The klibs are published **automatically** as part of each native target's publication — no separate KLib publication DSL exists.
- Root `kotlinMultiplatform` publication (`groupId:artifactId`): embeds Gradle module metadata that references the per-target coordinates; for Maven Central it auto-produces the required classifier-less root `.jar`.
- Publish-all task: `./gradlew publishAllPublicationsTo<MavenRepository>Repository`. To Maven Local: `publishToMavenCentral` / `publishAndReleaseToMavenCentral` via the vanniktech plugin.

**Convenience plugin (recommended for Maven Central).** Source: vanniktech/gradle-maven-publish docs (0.37.0). It auto-detects `org.jetbrains.kotlin.multiplatform`, publishes sources (+ javadoc/Dokka) jars, and provides the `mavenPublishing { coordinates(...); publishToMavenCentral(); signAllPublications(); pom { ... } }` extension — i.e. the `mavenPublish`-style DSL the brief references. It is the modern wrapper; the raw `maven-publish` auto-creation above is the KGP-native core.

**ABI-baseline interaction.** `binary-compatibility-validator`'s `apiCheck` is wired into the `check` lifecycle, so publication is gated on ABI stability before `publish*` runs (see (c)).

**Host requirements.** Kotlin/Native cross-compiles klibs for Apple targets from any host; a Mac is only required for cinterop, CocoaPods, or final Apple binaries — not for producing/publishing `iosArm64`/`iosSimulatorArm64` klibs. Publish all artifacts from one host to avoid Maven Central duplicate-coordinate failures.

## (b) Publishing + consuming the JVM-only KSP processor `:kompact-ksp` as a KSP-safe jar

**Module shape.** Source: KSP quickstart ("Create your own processor") — the processor is a JVM-only module:

```kotlin
// :kompact-ksp/build.gradle.kts
plugins { kotlin("jvm") }

dependencies {
    implementation(project(":kompact"))            // the runtime API it reads
    compileOnly("com.google.devtools.ksp:symbol-processing-api:2.3.11")  // KSP-safe scope — see below
}
```

> The official quickstart writes `implementation("com.google.devtools.ksp:symbol-processing-api:<ver>")` for an *in-build* module. For a **published** processor jar the KSP-safe form is `compileOnly` (equivalently `provided`), so the jar does not transitively pull a pinned KSP API version into the consumer; the consumer's applied `com.google.devtools.ksp` plugin supplies the matching KSP runtime. This is the "KSP-safe" requirement: the processor jar is consumed via the `ksp` configuration (KSP's isolated processing classloader), not placed on the application compile/runtime classpath.

**Publication.** A KSP processor is itself a regular JVM Maven artifact: `maven-publish` (+ optionally `com.vanniktech.maven.publish`) producing `kompact-ksp-<ver>.jar` + sources jar + pom. It declares no special classifier to consumers; discovery is via the Gradle `SymbolProcessorProvider` service file at `src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider` containing the provider FQCN — exactly as the quickstart shows. KSP version is decoupled from Kotlin since KSP 2.3.0 (KSP FAQ), but consumers must still align their KSP 2.x to their KGP/Kotlin per the compatibility table (kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin).

**Consumer-side coordinate form for a KMP consumer.** Source: google/ksp README "KSP Gradle Configurations Reference" table + KSP with Kotlin Multiplatform page. The bare `ksp(...)`/`ksp` configuration is **deprecated on KMP** unless `ksp.allow.all.target.configuration=true`. Per-target forms are `ksp<Target>` (e.g. `kspJvm`, `kspIosArm64`). Because `:kompact-ksp` is a **JVM-only** processor artifact, the consumer does NOT add it on `kspIosArm64` — a single processor dependency on the common metadata configuration is what feeds all targets (see (d)).

## (c) `binary-compatibility-validator`

**Plugin & block.** Source: BCV README (0.18.x). Applied to the root project; it auto-configures subprojects.

```kotlin
plugins {
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.0"
}

apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true          // validate KLib (native) ABI too
        // strictValidation = true   // optional: fail instead of infer on host-unsupported targets
    }
    // optional scalars:
    apiDumpDirectory = "api"            // default; golden files live here
    ignoredProjects.add("benchmarks")
    ignoredPackages.add("kotlinx.coroutines.internal")
}
```

**Golden `.api` files & location.** Source: README Tasks section + KLib design doc. The plugin dumps the JVM public ABI to `api/<project>.api` and (with `klib.enabled`) the merged native klib ABI to `api/<project>.klib.api`, "placed alongside JVM dumps (in `api` subfolder, by default) … target-specific declarations annotated with the target name." Files are committed to VCS.

**Tasks.** `apiDump` writes/overwrites `api/*.api` + `api/*.klib.api`; `apiCheck` reads the same golden files and **is automatically added to the `check` lifecycle**, so `./gradlew check` (and thus the publish flow's verification) fails on any ABI drift. Two caveats from primary sources: (1) BCV is in **maintenance mode** — see "Corrections" below; (2) per the KLib design doc, on a non-Apple host KLib dumps for `iosArm64`/`iosSimulatorArm64` can't be compiled, so BCV **infers** the Apple-target ABI from supported targets (or, with `strictValidation = true`, fails instead). Update golden dumps on an Apple host when possible.

**Successor note.** The Kotlin Gradle plugin now ships a built-in binary-compatibility validator: `kotlin { @OptIn(ExperimentalAbiValidation); abiValidation() }` with tasks `checkKotlinAbi` / `updateKotlinAbi`, auto-hooked into `check`, and a `filters {}` block (kotlinlang.org/docs/gradle-binary-compatibility-validation.html, 28 April 2026). BCV (0.18.x) is the spec's named companion (tickets 10/12); the built-in KGP `abiValidation` is the emerging replacement to evaluate for new projects.

## (d) Ticket 02 stub-source packaging in the consumer's KSP source roots

**The constraint.** Source: KSP overview — "KSP-based processors can't … modify the source code" and "cannot inject into existing source files." Therefore `KompactAnnotations.kt` (`@KompactModel` / `@KompactField`) cannot be patched into the consumer's hand-written source; it must be emitted as a **whole generated file** into the consumer's common source root — precisely ticket 02's "ksp-stubs" design.

**The mechanism.** Source: KSP `CodeGenerator` + KSP with Kotlin Multiplatform + issue #567.

1. The processor emits stubs via `CodeGenerator.createNewFile(Dependencies(aggregating = true, …), packageName = "ch.trancee.kompact.runtime", fileName = "KompactAnnotations")`. KSP writes to the *current compilation's* generated-sources directory; it cannot target an arbitrary source set directly.
2. To land in `commonMain` (so the annotations stubs + generated value-class views compile for JVM *and* iOS), the processor must run on the **common metadata** compilation, declared by the consumer with `kspCommonMainMetadata`:
   ```
   dependencies { add("kspCommonMainMetadata", "com.trancee.kompact:kompact-ksp:<version>") }
   ```
   This is the configuration the KSP README table calls "Common Main metadata compilation."
3. **The critical, non-automatic step.** Source: google/ksp issue #567 (open) and the first-party `examples/multiplatform/workload/build.gradle.kts`, where `kspCommonMainMetadata` is **left commented out** — the maintainers do not ship it as a stable seam. Generated common sources do **not** automatically compile into each target's `commonMain`; they must be wired explicitly:
   ```kotlin
   kotlin.sourceSets.commonMain {
       kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")  // path is KSP-version-dependent
   }
   // plus task dependencies so compile<Target> runs after kspCommonMainKotlinMetadata:
   tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile<*>>().configureEach {
       if (this.name != "kspCommonMainKotlinMetadata") {
           dependsOn("kspCommonMainKotlinMetadata")
       }
   }
   ```
   Issue #567 documents this exact pattern (and its failures: configuration-cache issues, duplicate declarations, missing task deps, IDE visibility gaps) as the reason common generation is "an open upstream problem."

**What this means for Kompact.** The `kspCommonMainMetadata` declaration + manual `srcDir`/task wiring is the consumer-side seam that (d) is really asking about. Because KSP emits the stubs as whole files into the common generated root, the consumer needs no separate `:kompact-annotations` publishable artifact — the annotations come from the processor's generated stubs, which is coherent with ticket 12's split (`kompact` runtime has no annotations; `:kompact-ksp` emits them as ksp-stubs). Per-schema value-class views are emitted into the same common root (isolating dependencies on each schema's `containingFile`); the `KompactAnnotations.kt` stub file is emitted as an aggregating output (same content for all schemas), matching ticket 02's "whole value-class source into commonMain."

## Decisions/coordinates to fold

- **(a) Runtime publication:** `plugins { id("org.jetbrains.kotlin.multiplatform"); id("maven-publish"); id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.0" }` + `kotlin { jvm(); iosArm64(); iosSimulatorArm64(); androidLibrary { … } }`. KGP auto-creates the `kotlinMultiplatform` root + per-target klib/jar publications; klibs (`iosArm64`, `iossimulatorarm64`) are published automatically per native target — no extra KLib DSL. Use `com.vanniktech.maven.publish` (0.37.0, `mavenPublishing { … }`) as the Maven-Central sign+publish wrapper. Publish all targets from one macOS host.
- **(b) KSP processor:** `:kompact-ksp` is a `kotlin("jvm")`-only module; publish as a normal JVM jar + sources via `maven-publish` (or vanniktech). Register `SymbolProcessorProvider` via `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`. Declare `symbol-processing-api` as `compileOnly` so the jar is KSP-safe (consumer's `com.google.devtools.ksp` plugin supplies the runtime). Current KSP = 2.3.11; consumers align to their Kotlin/KGP per the compatibility table.
- **(b) Consumer coordinate:** `add("kspCommonMainMetadata", "ch.trancee.kompact:kompact-ksp:<ver>")` (not target-specific `kspJvm`/`kspIosArm64`, because the processor emits *common* code; never the deprecated bare `ksp` unless `ksp.allow.all.target.configuration=true`).
- **(c) ABI baseline:** `apiValidation { @OptIn(ExperimentalBCVApi); klib { enabled = true } }`; committed golden files `api/kompact.api` + `api/kompact.klib.api`; `apiCheck` auto-runs in `check` (gates publish). On non-Apple CI, enable `strictValidation` only if you accept failing there; otherwise update `api/` on macOS. Evaluate the KGP built-in `abiValidation()` (successor; BCV is maintenance-mode) for new setups.
- **(d) Stub wiring:** processor emits `KompactAnnotations.kt` + per-schema value-class views as whole files into the common generated root via `kspCommonMainMetadata`, then the consumer manually adds that generated dir to `commonMain` with a `kspCommonMainKotlinMetadata` task dependency. This is the non-automatic seam (open upstream issue google/ksp#567); the processor owns the stub-file emission (aggregating) and per-schema views (isolating) and must sort outputs deterministically.

## Corrections to docs/research

- `docs/research/ksp-kmp-generation.md` states "KSP 2.3.11 is the current release" — primary source (GitHub Releases) **confirms** 2.3.11 is current; Solr/Maven-Central marker listing (2.3.9) lags. No correction needed; version claim holds.
- `docs/research/ksp-kmp-generation.md` claims KSP2's "programmatic common-processing entry point (`symbol-processing-aa-embeddable`)" is a supported integration seam. KSP2 remains in the Gradle daemon; the programmatic API exists but KSP itself **recommends the Gradle plugin** and the README still carries the `kspCommonMainMetadata` caveats. The docs/research "dedicated cacheable JVM task" workaround is valid only as a project-owned task (the project owns task inputs/outputs/dependencies per the Gradle build-cache guidance), not as a KSP-supported integration — do not present `kspCommonMainMetadata` as automatic.
- `docs/research/allocation-boxing-measurement.md` claims omitting `@JvmInline` is "incompatible with the JVM value-class contract" and "corrected." PROMPT.md §1 (no `@JvmInline` in common) is unchanged and still correct for common source; the map.md reconciliation already permits `@JvmInline` on the *generated JVM actual* only. No change to the KMP publication decision.

## Source index
- kotlinlang.org: `multiplatform-publish-lib-setup.html` (2026-05-13), `multiplatform-publish-libraries-to-maven.html` (2026-04-01), `ksp-multiplatform.html` (2026-08-12), `ksp-overview.html`, `ksp-quickstart.html` (2026-08-12), `gradle-binary-compatibility-validation.html` (2026-04-28), `gradle-configure-project.html#apply-the-plugin` (compat table).
- github.com/google/ksp: README "KSP Gradle Configurations Reference" table; releases (2.3.11, 2026-08-03); issue #567; `api/src/main/kotlin/com/google/devtools/ksp/processing/CodeGenerator.kt`.
- github.com/Kotlin/binary-compatibility-validator: `README.md` (setup, `apiValidation { klib { enabled = true } }`, `api`/`apiCheck`/`apiDump`, version 0.18.x), `docs/design/KLibSupport.md` (merged `.klib.api` dump + inference on non-Apple hosts).
- vanniktech.github.io/gradle-maven-publish-plugin/central/ (0.37.0, `mavenPublishing { }` KMP support).
- search.maven.org Solr API: `com.google.devtools.ksp.gradle.plugin` (2.3.7/2.3.8/2.3.9 on Maven Central; GitHub Releases = 2.3.11), `org.jetbrains.kotlinx:binary-compatibility-validator` (0.18.0 stable; README = 0.18.1), `com.google.devtools.ksp:symbol-processing-api` (2.3.7–2.3.9 on Maven Central for the API artifact).
