# ADR-0003: KMP Consumer Enablement — Android Target, KSP Publication, Java 17, GFM Markdown

- **Status:** accepted
- **Date:** 2026-09-12
- **Deciders:** kompact maintainer, pqcble integrator
- **Tags:** kmp, android, publication, toolchain, dokka, abi-validation

## Context

The `ch.trancee.kompact:kompact` and `ch.trancee.kompact:kompact-ksp` modules
must be consumable as KMP + Android dependencies by `pqcble` (Kotlin 2.4.20,
AGP 9.4.0). Three gaps blocked adoption:

1. `:kompact` published only `jvm`, `iosArm64`, `iosSimulatorArm64` — no Android
   artifact for pqcble's `android + iosArm64` consumer.
2. `:kompact-ksp` had POM metadata but no Portal pipeline, no signing, no
   checksums — the `@KompactModel` codegen was unavailable to any consumer.
3. Kotlin toolchain mismatch: kompact was pinned to Kotlin 2.3.21 / KSP 2.3.12;
   pqcble runs Kotlin 2.4.20 / AGP 9.4.0.

Since no Maven Central release has been cut, all changes are pre-release —
public ABI can change freely without a MAJOR bump.

## Assumptions

- pqcble's minimum Android API level is 21 (matches kompact's `minSdk = 21`).
- pqcble's minimum JVM target is 17 (drives the KSP `jvmTarget = JVM_17` decision).
- GitHub Actions `ubuntu-latest` ships the Android SDK (no manual SDK install
  needed for Linux). `macos-latest` does NOT ship the Android SDK (requires
  `android-actions/setup-android` for ABI-checking + Dokka on macOS).

## Decision

### A. Android target on `:kompact`

- **Plugin:** `com.android.kotlin.multiplatform.library` v9.4.0 (NOT `com.android.library`).
  This is the KMP-native Android target — it compiles `commonMain + androidMain`
  and creates a `kotlinMultiplatform` + `android` publication pair.
- **DSL:** `kotlin { android { namespace; compileSdk; minSdk; withJava() } }`
  (inside `kotlin { }`, NOT a top-level `android { }` block).
- **`withJava()`**: enabled per AGENTS.md guidance. `JvmCoveragePinning.java`
  (JVM-only Kover scaffolding) stays in `jvmMain`, not `androidMain`. `withJava()`
  enables the Java toolchain for the Android target so `compileAndroidMainJavaWithJavac`
  is available if Java sources are later added to `androidMain/java/`.
- **Source set hierarchy:** A shared `jvmCommon` intermediate source set was
  introduced. `@JvmInline actual` declarations moved from `jvmMain` to
  `jvmCommon`, with `jvmCommon.dependsOn(commonMain)`, `jvmMain.dependsOn(jvmCommon)`,
  and `androidMain.dependsOn(jvmCommon)`. This is required because KMP's Android
  target compiles from `commonMain + androidMain`, NOT from `jvmMain`.
- **JVM-only tests:** `VehicleTelemetryRawTest.kt` and `KompactResultCoverageTest.kt`
  (which use `java.lang.reflect`) moved from `commonTest` to `jvmTest`. These are
  coverage-pinning tests that force virtual dispatch on `@JvmInline` synthetic
  getters — impossible on Kotlin/Native (iOS targets).
- **ABI golden:** unchanged — the same public API surface for JVM + Android
  targets (source code is identical). The `kompact.api` and `kompact.klib.api`
  goldens were regenerated via `updateKotlinAbi` and show no new public declarations.

### B. KSP publication pipeline

- **Convention plugin:** `portal-publish` (in `build-logic/`) provides the Maven
  Central Portal pipeline tasks (`generateChecksums`, `assembleCentralBundle`,
  `centralPortalDeploy`, `centralPortalStatus`, `centralPortalPublish`) and the
  `bundleDir` local staging repository + PGP signing configuration (conditional on
  `SIGNING_KEY` env var). Applied to both `:kompact` and `:kompact-ksp`.
- **Convention plugin class naming:** The `dokka-markdown` convention plugin file
  (`dokka-markdown.gradle.kts`) defines class `DokkaMarkdownFormatPlugin` (not
  `DokkaMarkdownPlugin`) to avoid Kotlin DSL generated-accessor collision.
- **KSP publication:** `kotlin("jvm")` does NOT auto-create a publication when
  `maven-publish` is applied via a convention plugin (KGP's PluginManager listener
  may not fire). An explicit `MavenPublication("jvm")` is created from the
  `java` component. `jvmSourcesJar` and `dokkaJavadocJar` are explicitly registered
  (KGP auto-creates these for KMP `jvmTarget` but NOT for `kotlin("jvm")`).

### C. Dokka GFM Markdown

- **Format:** `DokkaFormatPlugin(formatName = "markdown")` via the `dokka-markdown`
  convention plugin. This produces `:dokkaGeneratePublicationMarkdown` (V2 task).
  `formatName = "markdown"` (NOT `"gfm"` — causes configuration conflict).
- **GFM plugin:** `gfm-plugin` + `gfm-template-processing-plugin` added via
  `DokkaFormatPluginContext.configure()`. Requires
  `@OptIn(InternalDokkaGradlePluginApi::class)`.
- **Output:** `kompact/docs/api/` (GFM Markdown committed to the repo).
  `kompact-ksp` outputs to the default build directory (not committed — it's a
  processor module).
- **Javadoc JAR:** remains a README stub for both JVM and Android publications
  (Dokka 2.x has no Javadoc-format task). Maven Central accepts this for KMP projects.

### D. Version bump

- `0.1.0-SNAPSHOT` → `0.2.0-SNAPSHOT` (root `build.gradle.kts`).
- Pre-release: no MAJOR bump needed (CONSTITUTION §Q5 — no release has been cut).

### E. Built-in ABI validation

- Replaced BCV (`apiValidation { klib { } }`) with built-in KGP `abiValidation { }`
  (KGP 2.1.0+). `keepLocallyUnsupportedTargets = true` (default) allows Linux CI
  to infer iOS klib while validating JVM + Android for real.
- In KGP 2.4.20, the `klib { }` configuration block was removed — klib validation
  is now automatic for KMP projects.
- `-Xexpect-actual-classes` flag kept (expect/actual classes are in **Beta** in
  Kotlin 2.4.20, not stabilized — the spec's §3.1 "stabilized" claim was incorrect).
- **HTML task suppression:** `dokkaGeneratePublicationHtml`, `dokkaHtml`, and
  `dokkaHtmlMultiModule` are disabled by the `dokka-markdown` convention plugin
  — only GFM Markdown is generated.
- **Portal security:** error response bodies are not logged verbatim (S1
  violation) — only the HTTP status code is surfaced.

### F. Toolchain alignment

- Kotlin = 2.4.20 (latest stable).
- AGP = 9.4.0 (minimum AGP 8.5.2 for Kotlin 2.4.x; AGP 9.4.0 is stable).
- KSP = 2.3.12 (Kotlin docs pair KSP 2.3.x with Kotlin 2.4.x; `ksp = "2.4.20"`
  will NOT resolve).
- kompact JVM target = JVM_21 (ABI validation needs ASM to parse v67 class files
  on JDK 25 hosts).
- **kompact-ksp JVM target = JVM_17** (downgraded from JVM_21). The KSP processor
  loads into the consumer's Kotlin compile daemon; JVM 21 bytecode excludes JDK 17
  consumers.

## Consequences

- `kompact` now publishes `kompact-android` (AAR) artifact in addition to
  `kompact-jvm`, `kompact-iosArm64`, `kompact-iosSimulatorArm64`, and
  `kompact` (KMP metadata).
- `kompact-ksp` is now publishable to Maven Central via the Portal Publisher API.
- CI runs on both macOS (full ABI + Markdown) and Linux (JVM tests + Android
  assemble + KSP Portal dry-run).
- `kompact/src/jvmCommon/` and `kompact/src/jvmTest/` are new source set
  directories (git-tracked).
- `build-logic/` is a new composite build providing convention plugins.
- `kompact/docs/api/` now contains GFM Markdown instead of HTML.
