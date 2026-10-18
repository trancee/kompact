# ADR-0003: KMP consumer enablement with an Android target, KSP publication, Java 17, and GFM Markdown

- Status: Accepted. [ADR-0009](0009-common-only-code-generation.md) partially
  supersedes it on 2026-10-18.
- Date: 2026-09-12
- Deciders: kompact maintainer, pqcble integrator
- Tags: kmp, android, publication, toolchain, dokka, abi-validation
- Partially superseded by: [ADR-0009](0009-common-only-code-generation.md)
  (the `jvmCommon` source-set hierarchy and the `-Xexpect-actual-classes` flag)
- Amended by: [ADR-0010](0010-minimal-checked-result-surface.md) (removes
  `JvmCoveragePinning` and the coverage-pinning tests for result classes)

> Note added 2026-10-18. [ADR-0009](0009-common-only-code-generation.md)
> replaces two parts of this decision: the `jvmCommon` source-set hierarchy for
> `@JvmInline actual` declarations, and the `-Xexpect-actual-classes` flag. The
> runtime and the generated code no longer use `expect` or `actual`, and
> `kompact/src/jvmCommon/` no longer exists.
> [ADR-0010](0010-minimal-checked-result-surface.md) deleted
> `JvmCoveragePinning` and `KompactResultCoverageTest.kt`. The other decisions
> stand.
>
> The Kotlin, KSP, and AGP versions below are the pins at decision time. The
> current pins live in [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml).

## Context

`pqcble` (Kotlin 2.4.20, AGP 9.4.0) needs to consume the
`ch.trancee.kompact:kompact` and `ch.trancee.kompact:kompact-ksp` modules as
KMP and Android dependencies. Three gaps blocked adoption:

1. `:kompact` published only `jvm`, `iosArm64`, and `iosSimulatorArm64`. It
   had no Android artifact for the `android + iosArm64` consumer in pqcble.
2. `:kompact-ksp` had POM metadata, but no Portal pipeline, no signing, and no
   checksums. No consumer could use the `@KompactModel` code generation.
3. The Kotlin toolchains did not match. kompact pinned Kotlin 2.3.21 and KSP
   2.3.12, while pqcble runs Kotlin 2.4.20 and AGP 9.4.0. Section F below
   resolves this baseline by aligning kompact to Kotlin 2.4.20.

No Maven Central release had been cut, so all changes were pre-release. The
public ABI could change freely without a MAJOR bump.

## Assumptions

- The minimum Android API level of pqcble is 21, which matches the
  `minSdk = 21` of kompact.
- The minimum JVM target of pqcble is 17. This drives the KSP
  `jvmTarget = JVM_17` decision.
- GitHub Actions `ubuntu-latest` ships the Android SDK, so Linux jobs need no
  manual SDK install. `macos-latest` does not ship the Android SDK. ABI checks
  and Dokka on macOS need `android-actions/setup-android`.

## Decision

### A. Add an Android target to `:kompact`

- Plugin: `com.android.kotlin.multiplatform.library` 9.4.0, not
  `com.android.library`. This plugin is the KMP-native Android target. It
  compiles `commonMain` and `androidMain` and creates a `kotlinMultiplatform`
  and `android` publication pair.
- DSL: `kotlin { android { namespace; compileSdk; minSdk; withJava() } }`. The
  block sits inside `kotlin { }`, not in a top-level `android { }` block.
- `withJava()` is enabled, as AGENTS.md recommends. `JvmCoveragePinning.java`
  (JVM-only Kover support code) stays in `jvmMain`, not `androidMain`.
  `withJava()` turns on the Java toolchain for the Android target, so
  `compileAndroidMainJavaWithJavac` is available if Java sources are later
  added to `androidMain/java/`.
- Source-set hierarchy: a shared intermediate source set, `jvmCommon`, holds the
  `@JvmInline actual` declarations that used to live in `jvmMain`. The wiring is
  `jvmCommon.dependsOn(commonMain)`, `jvmMain.dependsOn(jvmCommon)`, and
  `androidMain.dependsOn(jvmCommon)`. The Android target of KMP compiles from
  `commonMain` and `androidMain`, not from `jvmMain`, so the shared source set
  is required.
- JVM-only tests: `VehicleTelemetryRawTest.kt` and `KompactResultCoverageTest.kt`
  use `java.lang.reflect`, so they move from `commonTest` to `jvmTest`. These
  coverage-pinning tests force virtual dispatch on the synthetic getters of
  `@JvmInline` classes. Kotlin/Native (the iOS targets) cannot do that.
- ABI golden: unchanged. The JVM and Android targets share the same public API,
  because their source code is identical. `updateKotlinAbi` regenerated the
  `kompact.api` and `kompact.klib.api` goldens, and they show no new public
  declarations.

### B. Publish the KSP processor

- Convention plugin: `portal-publish` in `build-logic/` provides the Maven
  Central Portal pipeline tasks (`generateChecksums`, `assembleCentralBundle`,
  `centralPortalDeploy`, `centralPortalStatus`, and `centralPortalPublish`).
  It also provides the local `bundleDir` staging repository and the PGP signing
  configuration, which applies only when the `SIGNING_KEY` environment variable
  is set. Both `:kompact` and `:kompact-ksp` apply it.
- Convention plugin class name: the `dokka-markdown` convention plugin file
  (`dokka-markdown.gradle.kts`) defines the class `DokkaMarkdownFormatPlugin`,
  not `DokkaMarkdownPlugin`. The longer name avoids a collision with a
  generated Kotlin DSL accessor.
- KSP publication: `kotlin("jvm")` does not create a publication on its own
  when a convention plugin applies `maven-publish`, because the KGP
  `PluginManager` listener may not fire. The build creates an explicit
  `MavenPublication("jvm")` from the `java` component. It also registers
  `jvmSourcesJar` and `dokkaJavadocJar` explicitly. KGP creates these for a KMP
  `jvmTarget`, but not for `kotlin("jvm")`.

### C. Generate GFM Markdown with Dokka

- Format: the `dokka-markdown` convention plugin uses
  `DokkaFormatPlugin(formatName = "markdown")`. It produces the Dokka v2 task
  `:dokkaGeneratePublicationMarkdown`. The format name is `"markdown"`, not
  `"gfm"`, because `"gfm"` causes a configuration conflict.
- GFM plugin: `DokkaFormatPluginContext.configure()` adds `gfm-plugin` and
  `gfm-template-processing-plugin`. This call needs
  `@OptIn(InternalDokkaGradlePluginApi::class)`.
- Output: `kompact/docs/api/` holds the GFM Markdown, which is committed to the
  repository. `kompact-ksp` writes to the default build directory and is not
  committed, because it is a processor module.
- Javadoc JAR: stays a README stub for both the JVM and Android publications,
  because Dokka 2.x has no Javadoc-format task. Maven Central accepts this for
  KMP projects.

### D. Bump the version

- Change `0.1.0-SNAPSHOT` to `0.2.0-SNAPSHOT` in the root `build.gradle.kts`.
- No MAJOR bump is needed before the first release (CONSTITUTION §Q5: no
  release has been cut).

### E. Use built-in ABI validation

- Replace BCV (`apiValidation { klib { } }`) with the built-in KGP
  `abiValidation { }` (KGP 2.1.0 and later). With
  `keepLocallyUnsupportedTargets = true` (the default), Linux CI infers the iOS
  klib and validates JVM and Android for real.
- KGP 2.4.20 removed the `klib { }` configuration block. Klib validation now
  runs automatically for KMP projects.
- Keep the `-Xexpect-actual-classes` flag. In Kotlin 2.4.20, `expect` and
  `actual` classes are still Beta, not stable. The "stabilized" claim in §3.1
  of the spec was wrong.
- HTML task suppression: the `dokka-markdown` convention plugin disables
  `dokkaGeneratePublicationHtml`, `dokkaHtml`, and `dokkaHtmlMultiModule`.
  Dokka generates only GFM Markdown.
- Portal security: the build does not log error response bodies verbatim,
  because that would violate S1. It reports only the HTTP status code.

### F. Align the toolchain

- Kotlin 2.4.20, the latest stable release at the time.
- AGP 9.4.0. Kotlin 2.4.x needs at least AGP 8.5.2, and AGP 9.4.0 is stable.
- KSP 2.3.12. The Kotlin documentation pairs KSP 2.3.x with Kotlin 2.4.x, and
  `ksp = "2.4.20"` does not resolve.
- kompact JVM target JVM_21. ABI validation needs ASM to parse version 67
  class files on JDK 25 hosts. *Later lowered to JVM_17 so that JDK 17
  consumers can load the runtime. ABI validation parses JVM 17 class files
  just as well.*
- kompact-ksp JVM target JVM_17, lowered from JVM_21. The KSP processor loads
  into the Kotlin compile daemon of the consumer, and JVM 21 bytecode excludes
  consumers on JDK 17.

## Consequences

- `kompact` publishes a `kompact-android` (AAR) artifact in addition to
  `kompact-jvm`, `kompact-iosArm64`, `kompact-iosSimulatorArm64`, and
  `kompact` (KMP metadata).
- `kompact-ksp` can be published to Maven Central through the Portal Publisher
  API.
- CI runs on macOS (full ABI and Markdown) and on Linux (JVM tests, Android
  assemble, and the KSP Portal dry run).
- `kompact/src/jvmCommon/` and `kompact/src/jvmTest/` are new source-set
  directories tracked in git.
- `build-logic/` is a new composite build that provides convention plugins.
- `kompact/docs/api/` contains GFM Markdown instead of HTML.
