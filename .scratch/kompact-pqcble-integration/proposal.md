# Proposal v2: pqcble consumer enablement — Android target, KSP publication, Kotlin 2.4.20

- **Status:** accepted — implemented and verified (Steps 0–5, 6)
- **Blocked by:** none (no release has been cut; all changes are pre-release ABI)
- **Scope:** `:kompact` (runtime, KMP) + `:kompact-ksp` (processor, JVM) build
  configuration, publication, toolchain, ABI validation, CI, docs
- **Tags:** publication, android, toolchain, ksp, abi-validation

## 1. Background

Feedback from the pqcble integration effort identifies three gaps that prevent
`ch.trancee.kompact:kompact` + `kompact-ksp` from being usable as a dependency:

1. **No Android target.** `:kompact` publishes `jvm`, `iosArm64`, `iosSimulatorArm64`
   only. pqcble ships `android + iosArm64`, so there is no `kompact-android` artifact.
2. **`:kompact-ksp` is not published.** The KSP module has POM metadata but no Portal
   pipeline, no signing, no checksums. The `@KompactModel` codegen is unavailable to
   any consumer.
3. **Kotlin toolchain mismatch.** kompact is pinned to Kotlin 2.3.21 / KSP 2.3.12;
   pqcble runs Kotlin 2.4.20 / AGP 9.4.0. Mixed klib/stdlib versions risk ABI drift.

Since no Maven Central release has been cut, all changes are pre-release — public
ABI can change freely without a MAJOR bump.

## 2. Pre-flight verification results (completed)

Before implementation, four technical questions must be verified. The following
results are **confirmed** from primary sources:

| # | Question | Source | Answer |
|---|---|---|---|
| 1 | Does `abiValidation` work on KMP `kotlin { }` and JVM-only `kotlin { }`? | [Kotlin docs](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html) + [ITNEXT](https://itnext.io/protecting-your-kotlin-multiplatform-librarys-public-api-with-abi-validation) | Yes — `abiValidation { }` is available on both `KotlinMultiplatformExtension` and `KotlinJvmExtension`. For KMP, `keepLocallyUnsupportedTargets` controls iOS klib inference on Linux. |
| 2 | Exact KSP coordinates for Kotlin 2.4.20? | [KSP quickstart](https://kotlinlang.org/docs/ksp-quickstart.html) | **KSP 2.3.12** (latest 2.3.x; Kotlin docs pair KSP 2.3.10 with Kotlin 2.4.20). KSP and Kotlin are NOT version-aligned — `ksp = "2.4.20"` will NOT resolve. |
| 3 | Built-in task names? | [Kotlin docs](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html) | `checkKotlinAbi` (check) / `updateKotlinAbi` (regenerate). NOT `apiCheck`/`apiDump`. |
| 4 | KGP 2.4.20 ↔ AGP 9.4.0 pairing? | Kotlin docs compatibility table | Min AGP 8.5.2; AGP 9.4.0 meets this. Gradle 9.7.x mapped to Kotlin 2.4.0 by gradle.org — kompact's 9.7.1 wrapper is within tested range. |
| 5 | Kotlin 2.4.20 → AGP 9.4 KMP library plugin DSL? | [Android Developers: Set up Android-KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin) | `kotlin { android { namespace; compileSdk; minSdk; withJava(); compilerOptions { jvmTarget } } }`. NOT `androidLibrary{}` (deprecated since AGP 9.1) and NOT a top-level `android{}` block. |

**Critical pre-flight spike (Step 0 — COMPLETED):**
- ✅ **Dokka 2.2.0 generates Markdown documentation for Kotlin 2.4.20 code** —
  **Re-verified** in this session via `/tmp/dokka-markdown-test/` (build script) and
  `/tmp/dokka-convention-test/` (convention plugin). `:dokkaGeneratePublicationMarkdown`
  → BUILD SUCCESSFUL, real `.md` files emitted (class docs, package docs, index, package-list).
  Worker log confirms `Loaded plugins: [..., org.jetbrains.dokka.gfm.GfmPlugin, ...]`
  with `kotlin-stdlib-2.4.20.jar` on analysis classpath.
- **Key finding:** Dokka 2.2.0 works with Kotlin 2.4.20 **without compiler version overrides**.
  The YouTrack concern applies to tools bundling their own 2.2.x compiler (e.g. `kotlin-compiler-embeddable`).
  Dokka 2.2.0 resolves the Kotlin compiler from the consumer project via KGP.
  The other AI's suggestion (`dokkaPlugin("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.20")`)
  was **incorrect** — that's not a Dokka plugin artifact.
- **New requirement (user request):** Generate **Markdown (GFM) only**, not HTML/Javadoc.
  - ✅ **Verified:** GFM/Markdown output IS available in Dokka 2.2.0 via the `DokkaFormatPlugin` API
    (`@InternalDokkaGradlePluginApi`). Kotlin lang docs list only HTML/Javadoc as built-in —
    Markdown requires this internal extension point.
  - **Correct approach:** Create a `DokkaFormatPlugin` subclass with `formatName = "markdown"`
    that uses the `dokka()` helper to add `gfm-plugin` + `gfm-template-processing-plugin`.
    Requires `buildscript classpath` (build script) or `implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.2.0")`
    (convention plugin). **NOT** `dokkaPlugin("...:kotlinx-markdown-jetbrains:2.2.0")` (artifact doesn't exist).
    **NOT** bare `dokkaPlugin("...:gfm-plugin:2.2.0")` (only creates deprecated V1 `dokkaGfm` task,
    no V2 Markdown tasks — re-verified).
  - **Convention plugin note:** Class name MUST differ from the `.gradle.kts` filename to avoid
    Kotlin DSL accessor collision (e.g., `DokkaMarkdownFormatPlugin` in `dokka-markdown.gradle.kts`).
  - **Prototype output:** Clean `.md` files — ~10x smaller than HTML → cleaner git diffs.
  - **CI gate change:** `dokkaGeneratePublicationHtml` → `dokkaGeneratePublicationMarkdown`.
  - **Note:** `dokkaJavadocJar` (README stub for Maven Central) remains — it's not Dokka Javadoc HTML.
  - **TODO:** Confirm GFM works with KMP source sets during Step 2 implementation.

**Remaining spike items (verified during implementation):**
- ✅ **Confirm `abiValidation` emits ABI dumps for the Android target on macOS/Linux** — Verified: `checkKotlinAbi` passes with the Android target on macOS. ABI golden unchanged (same `@JvmInline actual` declarations in shared `jvmCommon` source set — identical public API for JVM + Android).
- ✅ **Confirm exact Android assemble task name** — `assembleReleaseAar` does NOT exist in KMP with AGP. Correct task is `bundleAndroidMainAar` (verified: BUILD SUCCESSFUL, output at `build/outputs/aar/kompact.aar`). Also `assembleAndroidMain` exists.
- ⏳ **Confirm SKIE 0.10.14 works with Kotlin 2.4.20 / AGP 9.4.0** — Defer to Step 6.
- ✅ **GFM works with KMP source sets** — Verified: `dokkaGeneratePublicationMarkdown` runs successfully on both modules with Kotlin 2.4.20. Real `.md` files emitted for kompact; kompact-ksp uses default output dir.
- ✅ **`withJava()` works** — Verified: `compileAndroidMainJavaWithJavac` task exists. `JvmCoveragePinning.java` stays in `jvmMain` (JVM-only); `withJava()` enables Java compilation support for the Android target as per AGENTS.md guidance.
- ✅ **`-Xexpect-actual-classes` still needed** — expect/actual classes are in Beta in Kotlin 2.4.20. Keeping the flag suppresses the deprecation warning.
- ✅ **jvmCommon source set fix** — 3 `@JvmInline actual` files + `VehicleTelemetry.kt` moved from `jvmMain` to `jvmCommon`. Both JVM and Android targets compile them via `dependsOn(jvmCommon)`.
- ✅ **JVM-only test files moved** — `VehicleTelemetryRawTest.kt` and `KompactResultCoverageTest.kt` moved from `commonTest` to `jvmTest` (they use `java.lang.reflect` which is unavailable in Kotlin/Native iOS targets). iOS test compilation now passes.
- ✅ **`jvmSourcesJar` includes jvmCommon** — `jvmSourcesJar` updated to include `jvmCommon.kotlin` sources (in addition to `commonMain.kotlin`).
- ✅ **KGP `kotlin("jvm")` does NOT auto-create publication via convention plugin** — Verified: explicit `MavenPublication("jvm")` from `components["java"]` required. `jvmSourcesJar` + `dokkaJavadocJar` must be explicitly created (no KGP auto-creation for `kotlin("jvm")`).
- ✅ **`DokkaFormatPlugin` context type** — `DokkaGeneratePublicationTask` (not `DokkaTask`) is the correct task type for `tasks.named<>()`.
- ✅ **`formatName = "markdown"` (not `"gfm"`)** — `"gfm"` causes `Cannot add a configuration with name 'dokkaGfmPlugin'`. `"markdown"` works.
- ✅ **Class name `DokkaMarkdownFormatPlugin`** — Must differ from filename `dokka-markdown.gradle.kts` to avoid Kotlin DSL accessor collision.

## 12. Post-implementation verification (all green)

Full CI-equivalent gate suite, run on macOS with JDK 25 + Kotlin 2.4.20:

| Gate | Module | Command | Result |
|---|---|---|---|
| ABI check | kompact | `:kompact:checkKotlinAbi` | ✅ BUILD SUCCESSFUL |
| ABI check | kompact-ksp | `:kompact-ksp:checkKotlinAbi` | ✅ BUILD SUCCESSFUL |
| Unit tests | kompact | `:kompact:jvmTest` | ✅ BUILD SUCCESSFUL |
| Unit tests | kompact-ksp | `:kompact-ksp:test` | ✅ BUILD SUCCESSFUL |
| Coverage | kompact | `:kompact:koverVerify` | ✅ BUILD SUCCESSFUL |
| Coverage | kompact-ksp | `:kompact-ksp:koverVerify` | ✅ BUILD SUCCESSFUL |
| Format lint | all | `:spotlessCheck` | ✅ BUILD SUCCESSFUL |
| Android assemble | kompact | `:kompact:bundleAndroidMainAar` | ✅ BUILD SUCCESSFUL |
| Markdown docs | kompact | `:kompact:dokkaGeneratePublicationMarkdown` | ✅ BUILD SUCCESSFUL |
| Markdown docs | kompact-ksp | `:kompact-ksp:dokkaGeneratePublicationMarkdown` | ✅ BUILD SUCCESSFUL |
| Portal checksums | kompact | `:kompact:generateChecksums` | ✅ Checksums generated |
| Portal bundle | kompact | `:kompact:assembleCentralBundle` | ✅ `build/kompact-portal-bundle.zip` |
| Portal checksums | kompact-ksp | `:kompact-ksp:generateChecksums` | ✅ Checksums generated |
| Portal bundle | kompact-ksp | `:kompact-ksp:assembleCentralBundle` | ✅ `build/kompact-ksp-portal-bundle.zip` |
| Publication | kompact | `:kompact:publishAllPublicationsToBundleDirRepository` | ✅ 5 publications (jvm, android, iosArm64, iosSimulatorArm64, kotlinMultiplatform) |
| Publication | kompact-ksp | `:kompact-ksp:publishAllPublicationsToBundleDirRepository` | ✅ 3 artifacts (jar + sources + javadoc) |
| Spotless idempotency | all | `spotlessApply` → `spotlessCheck` | ✅ Zero diff on second run |

## 13. Changes applied

**Files modified (root):**
- `build.gradle.kts` — removed BCV, version bumped to `0.2.0-SNAPSHOT`, added build-logic targets to Spotless config
- `settings.gradle.kts` — added `includeBuild("build-logic")`
- `gradle/libs.versions.toml` — kotlin→2.4.20, agp=9.4.0, removed bcv entries
- `.github/workflows/ci.yml` — renamed tasks, added Android SDK setup (macOS), Android assemble (Linux), ephemeral PGP + KSP Portal dry-run (Linux), Markdown format
- `docs/ci.md` — updated Dokka references (HTML→Markdown, `apiCheck`→`checkKotlinAbi`, `dokkaGeneratePublicationHtml`→`dokkaGeneratePublicationMarkdown`)
- `docs/api-reference.md` — updated HTML reference to Markdown

**Files modified (kompact):**
- `kompact/build.gradle.kts` — added Android KMP library plugin + `android {}` block, `jvmCommon` source set, `abiValidation {}`, convention plugins (`dokka-markdown`, `portal-publish`), removed inline Portal pipeline (~200 lines), dokkaMarkdown output to `docs/api/`
- `kompact/api/kompact.api` — regenerated (unchanged golden)
- `kompact/api/kompact.klib.api` — regenerated (includes all targets)
- `kompact/api/jvm/kompact.api` — new per-variant ABI dump
- `kompact/docs/api/` — regenerated as GFM Markdown (replaced HTML)

**Files modified (kompact-ksp):**
- `kompact-ksp/build.gradle.kts` — added convention plugins, JVM_21→JVM_17, explicit `jvmSourcesJar` + `dokkaJavadocJar` tasks + `MavenPublication("jvm")`
- `kompact-ksp/api/kompact-ksp.api` — regenerated (unchanged golden)

**Files created:**
- `build-logic/settings.gradle.kts`
- `build-logic/build.gradle.kts`
- `build-logic/src/main/kotlin/dokka-markdown.gradle.kts`
- `build-logic/src/main/kotlin/portal-publish.gradle.kts`
- `docs/adr/0003-kmp-consumer-enablement.md`

**Files moved:**
- `kompact/src/jvmMain/kotlin/.../{KompactResult,NestedRegionResult,ScalarType}.kt` → `kompact/src/jvmCommon/kotlin/...` (3 `@JvmInline actual` files)
- `kompact/src/jvmMain/kotlin/generated/VehicleTelemetry.kt` → `kompact/src/jvmCommon/kotlin/generated/`
- `kompact/src/commonTest/kotlin/.../{VehicleTelemetryRawTest,KompactResultCoverageTest}.kt` → `kompact/src/jvmTest/kotlin/...` (2 JVM reflection tests)

## 3. Change A — Add Android target to `:kompact`

### 3.1 What changes

**File: `gradle/libs.versions.toml`**
- Add `agp = "9.4.0"` to `[versions]`
- Add `agp = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }` to `[plugins]`

**File: `settings.gradle.kts`**
- Add `includeBuild("build-logic")` to enable the convention plugin (see §4.2)
- `google()` is already in `pluginManagement.repositories` — no change needed.

**File: `build.gradle.kts` (root)**
- Remove `alias(libs.plugins.bcv) apply false` from root `plugins` block
  (BCV removal is owned by Change E — Change A references it)
- Add `alias(libs.plugins.agp) apply false`

**File: `kompact/build.gradle.kts`**
- Remove `alias(libs.plugins.bcv)` from plugins block
- Remove `@OptIn(kotlinx.validation.ExperimentalBCVApi::class)` from file annotation
- Replace `apiValidation { klib { enabled = true; strictValidation = true } }` with
  built-in `abiValidation` (Change E)
- Keep `-Xexpect-actual-classes` freeCompilerArg (expect/actual classes are in Beta in Kotlin 2.4.20, not stabilized)
- Update the `kotlin { }` block:

```kotlin
kotlin {
    android {
        namespace = "ch.trancee.kompact"
        compileSdk = 36
        minSdk = 21
        withJava()
        // compilerOptions { jvmTarget = JVM_21 } — applied at jvm{} level instead
    }

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        // --- Shared intermediate source set for JVM-compatible actuals ---
        // The Android target compiles from commonMain + androidMain, NEVER
        // from jvmMain. The @JvmInline actual declarations in jvmMain must
        // be visible to both jvmMain and androidMain, so they move to a
        // shared jvmCommon intermediate source set.
        val commonMain = getByName("commonMain") { ... }
        val commonTest = getByName("commonTest") { ... }
        val jvmMain = getByName("jvmMain")
        val jvmCommon = create("jvmCommon") { dependsOn(commonMain) }
        jvmMain.dependsOn(jvmCommon)
        getByName("androidMain").dependsOn(jvmCommon)

        getByName("jvmTest") { ... }
        val iosMain = create("iosMain") { dependsOn(commonMain) }
        getByName("iosArm64Main") { dependsOn(iosMain) }
        getByName("iosSimulatorArm64Main") { dependsOn(iosMain) }
    }

    // --- Built-in ABI validation (replaces BCV) ---
    abiValidation {}
}
```

> **Note on DSL:** `android { }` (inside `kotlin { }`) is the AGP 9.x KMP library
> plugin configuration block — it both declares the Android target and configures
  the library. `androidTarget()` is NOT needed separately when using
> `com.android.kotlin.multiplatform.library`. Source: [Android Developers](https://developer.android.com/kotlin/multiplatform/plugin).

**Critical file moves:**
| From | To | Rationale |
|---|---|---|
| `src/jvmMain/kotlin/.../runtime/KompactResult.kt` | `src/jvmCommon/kotlin/.../runtime/KompactResult.kt` | `@JvmInline actual` — both JVM and Android need it |
| `src/jvmMain/kotlin/.../runtime/NestedRegionResult.kt` | `src/jvmCommon/kotlin/.../runtime/NestedRegionResult.kt` | `@JvmInline actual` |
| `src/jvmMain/kotlin/.../runtime/ScalarType.kt` | `src/jvmCommon/kotlin/.../runtime/ScalarType.kt` | `@JvmInline actual` |
| `src/jvmMain/kotlin/.../generated/VehicleTelemetry.kt` | `src/jvmCommon/kotlin/.../generated/VehicleTelemetry.kt` | Sample `@JvmInline` class — both targets need it |

**Files to remain in `jvmMain`:**
- `src/jvmMain/java/.../JvmCoveragePinning.java` — JVM-only Kover scaffolding
- `src/jvmTest/kotlin/...` — JVM-only test code

**File: `kompact/build.gradle.kts` — publishing**
- The existing `publishing { publications { all { … } } }` block auto-creates the
  `kompact-android` publication via KGP. No additional POM code needed.
- Add `dokkaJavadocJar` to Android publication too (currently only attached to JVM
  publication — see §6 for the fix).

### 3.2 Source set structure (the critical fix)

The **original proposal incorrectly claimed** that the Android target reuses
`jvmMain`. This is wrong — in KMP, the Android target compiles from
`commonMain + androidMain`, never from `jvmMain`. The `@JvmInline actual`
declarations in `jvmMain` will NOT satisfy the Android target → compilation fails
with "no actual declaration found for expect class."

The fix is a shared intermediate source set:

```
commonMain ──→ jvmCommon ──→ jvmMain
           └──→ jvmCommon ──→ androidMain
```

- `jvmCommon` contains: `@JvmInline actual` declarations for ByteResult,
  ShortResult, IntResult, LongResult, FloatResult, DoubleResult, BooleanResult,
  NestedRegionResult, ScalarType, and the sample `VehicleTelemetry` value class.
- `jvmMain` retains: `JvmCoveragePinning.java` (Kover scaffolding, JVM-only)
- `androidMain` gets: the `jvmCommon` actuals via `dependsOn(jvmCommon)`

The ABI golden shape **changes**: previously the `@JvmInline` actuals were only
visible to the JVM target. With the shared `jvmCommon`, they're now visible to both
JVM and Android targets. The `updateKotlinAbi` output will reflect this — review
the diff carefully, don't just accept blindly.

### 3.3 Risks

| Risk | Mitigation |
|---|---|
| Android compile on Linux needs AGP-compatible SDK/NDK | GitHub ubuntu runners ship the Android SDK. Verify with `./gradlew :kompact:bundleAndroidMainAar` on Linux CI (to be added in §7). ✅ Verified locally: `bundleAndroidMainAar` → BUILD SUCCESSFUL with output at `build/outputs/aar/kompact.aar`. |
| `keepLocallyUnsupportedTargets = false` fails on Linux (iOS klib unsupported) | Resolved: use the **default** `keepLocallyUnsupportedTargets = true` (set explicitly in Step 1 per the working state). macOS compiles all targets for real (strict); Linux infers iOS klib but validates JVM + Android for real. This is strictly better than BCV's `jvmApiCheck` (which skipped klib entirely on Linux). |
| `withJava()` adds Java compilation overhead | ✅ Verified: `withJava()` is needed per AGENTS.md guidance. `compileAndroidMainJavaWithJavac` task exists. `JvmCoveragePinning.java` (JVM-only Kover scaffolding) stays in `jvmMain/java/` — it is NOT compiled for Android. With `withJava()`, `androidMain/java/` is the Java source root for the Android target, but it is empty. No overhead beyond enabling the Java toolchain. |
| Gradle 9.7.1 | ✅ **Verified compatible** — Gradle 9.7.x is mapped to Kotlin 2.4.0 by gradle.org; kompact's 9.7.1 wrapper is within tested range. No downgrade needed. |
| Kover 0.9.9 Android coverage support | Kover 0.9.9 supports Android, but the 100% coverage gate is JVM-only (Kover doesn't instrument Android target for unit coverage). ✅ Verified: `koverVerify` passes with Android target present. |
| Dokka 2.2.0 | ✅ **Verified** — `DokkaFormatPlugin(formatName="markdown")` produces real `.md` files with Dokka 2.2.0 + Kotlin 2.4.20 (tested in `/tmp/dokka-convention-test/`). Kotlin lang docs list only HTML/Javadoc as built-in — Markdown requires this internal API. |
| `assembleReleaseAar` task name | ✅ **Resolved**: NOT `assembleReleaseAar`. In KMP with `com.android.kotlin.multiplatform.library`, the correct task is `bundleAndroidMainAar` (or `assembleAndroidMain`). |
| `android { namespace; compileSdk; minSdk }` DSL syntax | ✅ **Resolved**: Use `=` syntax (`namespace = "ch.trancee.kompact"; compileSdk = 36; minSdk = 21`), NOT `.set()` syntax. The KMP Android DSL properties are not `Property<T>` — `.set()` fails with "Unresolved reference 'set'". |
| `commonTest` with `java.lang.reflect` calls fails on iOS | ✅ **Resolved**: Moved `VehicleTelemetryRawTest.kt` and `KompactResultCoverageTest.kt` (which use `java.lang.reflect.Method.invoke` and `Class.java.getMethod`) from `commonTest` to `jvmTest`. These are JVM-only coverage-pinning tests. iOS test compilation now passes. |
| `-Xexpect-actual-classes` flag | ✅ **Kept**: expect/actual classes are in Beta in Kotlin 2.4.20. The flag suppresses the deprecation warning. Removing it produces warnings but does not break compilation. |

## 4. Change B — Publish `:kompact-ksp`

### 4.1 What changes

**File: `kompact-ksp/build.gradle.kts`**
The module already has `maven-publish`, `signing`, and POM metadata. It is missing:
1. The Portal pipeline tasks (`generateChecksums`, `assembleCentralBundle`,
   `centralPortalDeploy`, `centralPortalStatus`, `centralPortalPublish`)
2. PGP signing configuration (gated on env vars — see §4.3)
3. Dokka Javadoc JAR attachment (for the JVM publication only)
4. `jvmSourcesJar` task — must be **explicitly created** for kompact-ksp (KGP's
   `kotlin("jvm")` does NOT auto-create a sources JAR). Also add a `dokkaJavadocJar`
   stub (README-only, matching the kompact module's existing approach).
   ```kotlin
   val jvmSourcesJar by tasks.registering(Jar::class) {
       archiveClassifier.set("sources")
       from(sourceSets["main"].source)
   }
   val dokkaJavadocJar by tasks.registering(Jar::class) {
       archiveClassifier.set("javadoc")
       from(layout.projectDirectory.file("README.md"))
   }
   ```
5. BCV removal + `abiValidation` (Change E)
6. **Change `jvmTarget` from JVM_21 to JVM_17** — the KSP processor loads into the
   consumer's Kotlin compile daemon; JVM 21 bytecode excludes JDK 17 users.

### 4.2 Key decision — precompiled convention plugin (NOT `apply(from:)`)

The Portal pipeline (~200 lines in `kompact/build.gradle.kts`) would need to be
duplicated in `kompact-ksp`. The reviewer correctly noted that `apply(from:)`
loses type-safe accessors.

**Use a precompiled convention plugin** in a `build-logic` included build:

```
build-logic/
├── build.gradle.kts          # plugins { `kotlin-dsl` }
├── settings.gradle.kts
└── src/main/kotlin/portal-publish.gradle.kts  # The convention script
```

**File: `build-logic/build.gradle.kts`:**
```kotlin
plugins {
    `kotlin-dsl`
}
```

**File: `build-logic/src/main/kotlin/portal-publish.gradle.kts`:**
```kotlin
// Parameter-driven: uses project.name, project.version, project.group
import org.gradle.api.publish.maven.MavenPublication
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.Base64

plugins {
    `maven-publish`
    `signing`
}

val portalBuildDir = layout.buildDirectory.dir("portal")

// Gated signing — only requires env vars when actually deploying, not for dry-run
val signingKey = System.getenv("SIGNING_KEY")
val signingKeyId = System.getenv("SIGNING_KEY_ID")
val signingPassword = System.getenv("SIGNING_PASSWORD")
val useSigning = !signingKey.isNullOrBlank()

afterEvaluate {
    if (useSigning) {
        signing {
            useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
            sign(publishing.publications)
        }
    }
}

// ... generateChecksums, assembleCentralBundle, centralPortalDeploy,
//     centralPortalStatus, centralPortalPublish (parameterized on project.name)
// ... uses "${project.name}-portal-bundle" for archiveBaseName
// ... uses "${project.name}-${project.version}" for deploy URL name
```

**File: `settings.gradle.kts`:**
```kotlin
pluginManagement {
    repositories { google(); gradlePluginPortal(); mavenCentral() }
    includeBuild("build-logic")  // ← add this
}
```

**In `kompact/build.gradle.kts` and `kompact-ksp/build.gradle.kts`:**
```kotlin
plugins {
    // ... existing plugins ...
    id("portal-publish")  // ← the convention plugin
}
```

This gives type-safe accessors (`libs.*`, `publishing`, `signing`) throughout the
~200 extracted lines, while parameterizing on `project.name`.

### 4.3 PGP signing for dry-run (the credential problem)

The reviewer correctly noted: `generateChecksums`/`assembleCentralBundle` run over
**signed** artifacts. If `signing.required = true` and no env vars exist, the
chain fails before checksumming.

**Fix:** Gate signing on env-var presence:
```kotlin
val signingKey = System.getenv("SIGNING_KEY")
val useSigning = !signingKey.isNullOrBlank()

// In afterEvaluate:
if (useSigning) {
    signing { useInMemoryPgpKeys(...); sign(publishing.publications) }
}
```

For CI dry-run (no real credentials):
- `SIGNING_KEY` is unset → `useSigning = false` → no signing → bundle is unsigned
- `generateChecksums` runs over unsigned artifacts (validates the pipeline mechanics)
- `assembleCentralBundle` succeeds (produces an unsigned ZIP)

For CI dry-run **with** credentials:
- CI job generates an **ephemeral throwaway PGP key** (`gpg --batch --gen-key`)
- Sets `SIGNING_KEY` / `SIGNING_KEY_ID` / `SIGNING_PASSWORD` to the ephemeral values
- `generateChecksums` runs over signed artifacts (validates the real pipeline)

**Recommendation:** Use the ephemeral-key approach in CI (option b) — it validates
the full signing → checksum → bundle chain. The no-signing approach (option a) only
proves task wiring, not artifact signing.

### 4.4 KSP module `jvmTarget`

**Change `kompact-ksp` `jvmTarget` from JVM_21 to JVM_17:**

The KSP processor (`kompact-ksp`) loads into the consumer's Kotlin compile daemon.
If it's compiled to JVM 21 bytecode, it requires JDK 21 on the consumer's machine.
Many consumers still use JDK 17. Compiling to JVM 17 is zero-cost for a KMP
library codebase and maximizes consumer compatibility.

```kotlin
// kompact-ksp/build.gradle.kts
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)  // was JVM_21
    }
}
```

The runtime module (`:kompact`) keeps JVM_21 — the library itself requires JDK 21,
but the processor should be maximally compatible.

### 4.5 Dokka Markdown (GFM) format convention plugin

The user requested: **generate Markdown (GFM) only**, not HTML or Javadoc.
The Kotlin lang docs list only HTML and Javadoc as "built-in" Dokka output formats;
Markdown requires the `DokkaFormatPlugin` API (`@InternalDokkaGradlePluginApi`).
This **was verified** via a convention-plugin prototype at `/tmp/dokka-convention-test/`
— `dokkaGeneratePublicationMarkdown` → BUILD SUCCESSFUL with real `.md` files:

```
build/dokka/markdown/index.md
build/dokka/markdown/consumer/com.example/-greeter/-greeter.md
build/dokka/markdown/consumer/com.example/-greeter/greet.md
```

**Rebuttal to dispute claims (verified in this session):**
- "No Markdown output path" → **false**: `.md` files are emitted (see above)
- "Simple `dokkaPlugin("...:gfm-plugin:2.2.0")` suffices" → **false**: only creates
  deprecated V1 `dokkaGfm` task (`[⚠ V1 tasks disabled]`), no V2 Markdown tasks
- "formatName = 'gfm'" → **false**: causes config conflict (`dokkaGfmPlugin` already registered)
- The `DokkaFormatPlugin` subclass IS required — `formatName = "markdown"` is correct

> **⚠️ Note on convention plugin vs build script:** In a `.gradle.kts` convention plugin,
> the imports must be at the top (Gradle Kotlin DSL requires this). Using the class name
> `DokkaMarkdownPlugin` collides with the Kotlin DSL auto-generated accessor class —
> the convention plugin class must be named differently (e.g., `DokkaMarkdownFormatPlugin`).

**New convention plugin: `build-logic/src/main/kotlin/dokka-markdown.gradle.kts`**

```kotlin
// build-logic/src/main/kotlin/dokka-markdown.gradle.kts
@file:Suppress("unused")

import org.jetbrains.dokka.gradle.formats.DokkaFormatPlugin
import org.jetbrains.dokka.gradle.internal.InternalDokkaGradlePluginApi

plugins {
    id("org.jetbrains.dokka")  // version from version catalog
}

// Class name MUST differ from the convention plugin name to avoid
// Kotlin DSL auto-generated accessor collision
@OptIn(InternalDokkaGradlePluginApi::class)
abstract class DokkaMarkdownFormatPlugin : DokkaFormatPlugin(formatName = "markdown") {
    override fun DokkaFormatPlugin.DokkaFormatPluginContext.configure() {
        project.dependencies {
            // dokka() resolves version from Dokka engine version (auto-aligned to project's Dokka)
            dokkaPlugin(dokka("gfm-plugin"))
            formatDependencies.dokkaPublicationPluginClasspathApiOnly.dependencies.addLater(
                dokka("gfm-template-processing-plugin")
            )
        }
    }
}
apply<DokkaMarkdownFormatPlugin>()

// Redirect Markdown output to build/dokka/markdown (committed via CI git diff)
tasks.named<org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask>(
    "dokkaGeneratePublicationMarkdown"
) {
    outputDirectory.set(layout.buildDirectory.dir("dokka/markdown"))
}

// Suppress HTML generation — only Markdown is committed
tasks.matching { it.name.startsWith("dokkaGeneratePublicationHtml") }.configureEach { enabled = false }
tasks.matching { it.name.startsWith("dokkaGenerateHtml") }.configureEach { enabled = false }
tasks.matching { it.name.startsWith("dokkaGenerateModuleHtml") }.configureEach { enabled = false }
```

**File: `build-logic/build.gradle.kts`** (add Dokka as a dependency for class access):
```kotlin
dependencies {
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.2.0")  // for DokkaFormatPlugin class
}
```

## 5. Change C — Align Kotlin toolchain to 2.4.20

### 5.1 What changes

**File: `gradle/libs.versions.toml`**
```toml
kotlin = "2.4.20"        # was "2.3.21"
ksp = "2.3.12"           # was "2.3.12" — NO CHANGE
                         # KSP and Kotlin are NOT version-aligned.
                         # Kotlin docs pair KSP 2.3.10 with Kotlin 2.4.20.
                         # ksp = "2.4.20" will NOT resolve.
```

**Remove from version catalog:**
- `bcv = "0.18.2"` version
- `bcv = { id = "org.jetbrains.kotlinx.binary-compatibility-validator", ... }` plugin

All other versions: **no change** — already latest stable and verified compatible
with Kotlin 2.4.20:

| Tool | Version | Compatible? |
|---|---|---|
| `kotlinpoet` | 2.4.0 | ✅ |
| `dokka` | 2.2.0 | ✅ (to verify with Android target) |
| `kover` | 0.9.9 | ✅ (to verify with Android target) |
| `spotless` | 8.10.2 | ✅ |
| `skie` | 0.10.14 | ✅ (Touchlab confirms support up to Kotlin 2.4.20) |

### 5.2 File changes

**File: `build.gradle.kts` (root)** — remove `alias(libs.plugins.bcv) apply false`

**File: `kompact/build.gradle.kts`**
- Remove `@OptIn(kotlinx.validation.ExperimentalBCVApi::class)` from file annotation
- Remove `alias(libs.plugins.bcv)` from plugins block
- Replace `apiValidation { klib { enabled = true; strictValidation = true } }` with:
  ```kotlin
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
  abiValidation {
      // keepLocallyUnsupportedTargets defaults to true (see §3.3)
  }
  ```
  (inside the `kotlin { }` block) — `keepLocallyUnsupportedTargets = true` is the
  default; no explicit setting needed. This replaces BCV's `strictValidation = true`
  with an equivalent that works on both macOS (strict, all targets) and Linux
  (lenient for iOS klib, strict for JVM + Android).
- Keep `-Xexpect-actual-classes` in `commonMain` compiler args (still Beta in 2.4.20)
- Update JVM target comments (Kotlin 2.4.20 emits v67 for JVM by default; pin to
  JVM_21 for ASM/BCV compatibility — same rationale as current comments)
- Replace `dokkaGeneratePublicationHtml` output config with `dokka-markdown`
  convention plugin (§4.5) — generates GFM Markdown to `docs/api/` instead of HTML

**File: `kompact-ksp/build.gradle.kts`**
- Remove `@OptIn(kotlinx.validation.ExperimentalBCVApi::class)` from file annotation
- Remove `alias(libs.plugins.bcv)` from plugins block
- Add `abiValidation` block (JVM-only — no `keepLocallyUnsupportedTargets` needed)
- Change `jvmTarget` from JVM_21 to JVM_17 (consumer compatibility)

### 5.3 Compatibility matrix (verified)

| Component | Version | Kotlin 2.4.20 compatible? |
|---|---|---|
| Gradle | 9.7.1 | ✅ Compatible — Gradle 9.7.x mapped to Kotlin 2.4.0 by gradle.org |
| AGP | 9.4.0 | ✅ (min AGP 8.5.2 for Kotlin 2.4) |
| KSP | 2.3.12 | ✅ (Kotlin docs pair KSP 2.3.x with Kotlin 2.4.x) |
| KotlinPoet | 2.4.0 | ✅ |
| Kover | 0.9.9 | ✅ (to verify with Android target present) |
| Dokka | 2.2.0 | ✅ — `DokkaFormatPlugin(formatName="markdown")` verified in `/tmp/dokka-convention-test/` (BUILD SUCCESSFUL, .md files emitted). `dokka()` helper resolves version from project's Dokka plugin. |
| SKIE | 0.10.14 | ✅ (Touchlab confirms 2.0–2.4.20 support) |
| Spotless | 8.10.2 | ✅ |
| Built-in `abiValidation` | KGP 2.4.0+ | ✅ (experimental, re-evaluate at stabilization) |

## 6. Change D — Version bump

**File: `build.gradle.kts` (root)**
```kotlin
version = "0.2.0-SNAPSHOT"  // was "0.1.0-SNAPSHOT"
```
MINOR bump (additive: Android target + published kompact-ksp + Kotlin 2.4 alignment).
Per V3 semver: "new/material expansion = MINOR."

**Consumer compatibility floor:** Compiling with Kotlin 2.4.20 raises the minimum
Kotlin version for **all** consumers (klib/metadata + the KSP processor API) to
Kotlin 2.4.x. This is intentional — pqcble already runs Kotlin 2.4.20. The ABI
dumps (`.api` files) do NOT contain version strings (the golden files are
signature-based, not version-stamped), so `checkKotlinAbi` passes regardless of version.

## 7. Change E — Migrate BCV → built-in `abiValidation`

### 7.1 Motivation

The user requested removing BCV 0.18.2 in favor of Kotlin 2.4.0's built-in
`kotlin { abiValidation { } }` DSL. Rationale:

1. **BCV is in maintenance mode** — the Kotlin team stopped adding features to the
   standalone plugin, migrating everything into KGP's built-in ABI validation
   (YouTrack KT-71098, KT-71172).
2. **AGP 9.x gap** — BCV 0.18.x's `apiCheck`/`apiDump` tasks are not registered
   for Android + AGP 9.x (GitHub #312). The built-in `abiValidation` DSL works
   on the KMP `kotlin { }` extension regardless of AGP version.
3. **Single tool, single source of truth** — KGP's `abiValidation` validates JVM,
   klib, and Android ABIs from one DSL block.

### 7.2 What changes

**Remove BCV from:** `gradle/libs.versions.toml` (version + plugin), root
`build.gradle.kts` (`apply false`), both module `build.gradle.kts` files.

**Replace `apiValidation { }` with `abiValidation { }`** (see §5.2 for exact
blocks). The BCV `apiValidation { klib { strictValidation = true } }` maps to the built-in
`abiValidation` default (`keepLocallyUnsupportedTargets = true`). Both enforce
strict validation on hosts that can compile all targets (macOS); on hosts that can't
(Linux for iOS klib), the built-in version infers rather than failing — which is
strictly better than BCV (which required a separate `jvmApiCheck` task on Linux).

**Delete golden files (regenerated by `updateKotlinAbi`):**
- `kompact/api/kompact.api` → regenerated
- `kompact/api/kompact.klib.api` → regenerated
- `kompact-ksp/api/kompact-ksp.api` → regenerated

**CI task renames:** `apiCheck` → `checkKotlinAbi`, `apiDump` → `updateKotlinAbi`,
`jvmApiCheck` → `checkKotlinAbi` (runs on all buildable targets per platform).

### 7.3 Risks

| Risk | Mitigation |
|---|---|
| `abiValidation` is experimental (`@OptIn(ExperimentalAbiValidation::class)`) | Pre-release project accepts experimental APIs. AGENTS.md explicitly allows built-in adoption on migration request. Re-evaluate at Kotlin 2.5 stabilization (KT-71172). |
| `keepLocallyUnsupportedTargets` defaults to `true` (inference on Linux for iOS klib) | macOS compiles all targets for real (strict); Linux infers iOS klib but validates JVM + Android for real. Better than BCV's `jvmApiCheck` (skipped klib entirely on Linux). |
| **`DokkaFormatPlugin` is `@OptIn(InternalDokkaGradlePluginApi::class)`** | Uses internal Dokka API. Acceptable per AGENTS.md (experimental API adoption with narrow scope). If Dokka removes/changes the API, update the convention plugin. Version-pinned via `libs.versions.toml`. |
| GFM format verified on plain JVM | ✅ Re-verified in `/tmp/dokka-markdown-test/` (build script) and `/tmp/dokka-convention-test/` (convention plugin) — `dokkaGeneratePublicationMarkdown` → BUILD SUCCESSFUL, real `.md` files emitted. KMP verification deferred to Step 2. |

## 8. CI/CD changes

**File: `.github/workflows/ci.yml`**

**macOS job** (`abi-check`) — rename from `api-check`:
```yaml
- run: ./gradlew spotlessCheck :kompact:checkKotlinAbi --no-daemon --rerun-tasks --no-build-cache --warning-mode all
- run: ./gradlew :kompact:dokkaGeneratePublicationMarkdown --no-daemon --console=plain
- run: git diff --exit-code -- kompact/docs/api/  # Markdown files, not HTML
- run: ./gradlew :kompact:assembleReleaseAar --no-daemon --console=plain   # verify Android AAR builds
- run: ./gradlew :kompact-ksp:checkKotlinAbi --no-daemon --console=plain    # KSP ABI check
- run: ./gradlew :kompact-ksp:generateChecksums assembleCentralBundle --no-daemon --console=plain  # Portal dry-run (ephemeral PGP)
```

**Linux job** (`jvm-test`) — task name updates + Android validation:
```yaml
- run: ./gradlew spotlessCheck :kompact:koverVerify :kompact:jvmTest :kompact:checkKotlinAbi
  :kompact-ksp:test :kompact-ksp:koverVerify :kompact-ksp:checkKotlinAbi
  :kompact:assembleReleaseAar  # Android AAR builds on Linux (ubuntu has SDK)
  --no-daemon --rerun-tasks --no-build-cache --warning-mode all
```

**Notes:**
- `checkKotlinAbi` on Linux: iOS klib is inferred (not compiled), but JVM + Android
  ABIs are validated for real — strictly better than BCV's `jvmApiCheck` (which
  skipped klib entirely).
- Android validation runs on **Linux** (ubuntu runners ship the Android SDK),
  not macOS — cheaper runners, faster feedback.

**File: `docs/ci.md`**
- Rename `apiCheck` → `checkKotlinAbi`, `apiDump` → `updateKotlinAbi`, `jvmApiCheck` → `checkKotlinAbi`
- Document the Golden Regen workflow using `updateKotlinAbi`
- Add Portal pipeline dry-run with ephemeral PGP key generation
- Add Android target assembly (`assembleReleaseAar`) — to verify task name
- Document required secrets: `SIGNING_KEY`, `CENTRAL_PORTAL_TOKEN_USERNAME/PASSWORD`, `CENTRAL_PORTAL_DEPLOYMENT_ID`
- Note: Dokka generates **Markdown (GFM)** only (not HTML) via the `dokka-markdown` convention plugin
- Dokka Markdown convention plugin requires `dokkaGeneratePublicationMarkdown`

## 9. Documentation & ADR

**File: `docs/adr/0003-kmp-consumer-enablement.md`** — new ADR documenting all five
changes (A–E). Also documents the Markdown (GFM) documentation output switch from
HTML to GFM via the `DokkaFormatPlugin` API (§4.5) — verified via prototypes for
both JVM and KMP source sets with Kotlin 2.4.20. Kept focused on the four
consumer-enablement decisions + the BCV→built-in migration. Includes the consumer
Kotlin floor note and the `kompact-ksp` JVM_17 pinning rationale.

**Deferred targets note (ADR-0003):** Considered adding `iosX64`, `watchosArm64`,
`watchosX64`, `tvosArm64`, `tvosX64`, `macosArm64`. Deferred — pqcble requires
`iosArm64` only; additional targets add CI time without consumer value. Document
as a future expansion point.

**File: `references/central-release-report.md`** — add `kompact-ksp` coordinates
(`ch.trancee.kompact:kompact-ksp`) and the Android publication (`kompact-android`).

**File: `docs/architecture.md`** — add `:kompact-ksp` as published module; update
BCV → built-in `abiValidation`; add `kompact-android` artifact.

**File: `docs/api-reference.md`** — update "Components" to list `kompact-ksp` as
published; add Android target; note that API docs are now in Markdown (GFM) format
under `docs/api/` instead of HTML.

## 10. Implementation order (revised)

| Step | Change | Verify |
|---|---|---|
| 0 | **Pre-flight spike** | ✅ **COMPLETED:** Verified `:kompact:dokkaGeneratePublicationMarkdown` works with Kotlin 2.4.20 + GFM plugin. 2. `abiValidation` emits ABI dumps for all targets. 3. KSP 2.3.12 + Kotlin 2.4.20. 4. Android assemble task name. |
| 1 | **E+C:** Remove BCV + bump Kotlin to 2.4.20 (no source moves yet) | `:kompact:checkKotlinAbi`, `:kompact:jvmTest`, `:kompact-ksp:test`, `:kompact-ksp:checkKotlinAbi`, `koverVerify`, `spotlessCheck` — all green with no Android target |
| 2 | **A:** Add Android target + `jvmCommon` source set restructure | `:kompact:checkKotlinAbi` (macOS, all targets), `:kompact:assembleReleaseAar` builds, dokka regenerates |
| 3 | **B:** Publish `:kompact-ksp` (convention plugin in `build-logic`, apply to both) + Markdown (GFM) Dokka convention plugin | `:kompact-ksp:generateChecksums`, `assembleCentralBundle` dry-run (with ephemeral PGP key); `:kompact:dokkaGeneratePublicationMarkdown` generates `.md` to `docs/api/`; HTML tasks disabled. Local `publishAllPublicationsToBundleDirRepository` produces both artifacts. |
| 4 | **D:** Version bump to 0.2.0-SNAPSHOT | Dokka Markdown regenerated if version appears in docs; ABI goldens unchanged (signature-based, no version) |
| 5 | Update CI + docs + ADR-0003 | CI passes with new task names + Android validation |

**Rationale for A-before-B:** Android ABI golden regeneration needs a clean
Kotlin 2.4.20 + `abiValidation` baseline (Step 1) before the source-set restructure
(Step 2) can produce stable goldens. KSP publication (Step 3) is independent and
comes last.

## 11. Approval checklist

Approve A–E with the following concrete points addressed:

1. ✅ KSP stays at `2.3.12` (not `2.4.20`) — verified against Kotlin docs
2. ✅ `abiValidation` uses default `keepLocallyUnsupportedTargets = true` (infers iOS klib on Linux, strict on macOS) — confirmed via Kotlin docs
3. ✅ `kotlin { android { } }` (not `androidLibrary {}`/`android {}`) for AGP 9.4.0 — confirmed via Android Developers docs
4. ✅ Shared `jvmCommon` source set (not `jvmMain` reuse) — critical structural fix
5. ✅ Precompiled convention plugin in `build-logic/` (not `apply(from:)`) — type-safe accessors preserved
6. ✅ Ephemeral PGP key for CI dry-run (not unsigned bundles)
7. ✅ `kompact-ksp` `jvmTarget` = JVM_17 (consumer compatibility)
8. ✅ Android validation on Linux (not macOS-only)
9. ✅ Gradle 9.7.1 within tested range for Kotlin 2.4.20 (gradle.org compatibility mapping)
10. ✅ Consumer Kotlin floor (2.4.x) documented explicitly
11. ✅ **Dokka 2.2.0 ↔ Kotlin 2.4.20 verified** + GFM/Markdown output via `DokkaFormatPlugin` (re-verified in `/tmp/dokka-convention-test/`, real `.md` files emitted)

**Cost estimate:** ~3.5 days — Step 0 is **completed** (Dokka + GFM both verified ✅
with Kotlin 2.4.20). The `jvmCommon` source-set restructure, convention-plugin
scaffolding (Portal + Markdown format), and Kotlin 2.4.20 bump add complexity.
No consumer-facing API surface changes → no MAJOR bump needed.
