---
Type: research
Status: resolved
Labels:
  - wayfinder:research
  - scope:publication
  - scope:build
  - scope:kmp
Blocked by:
  - "02 generation strategy"
  - "12 module split & publication"
Decides:
  - "12 module split & publication"
Findings: ../research/kmp-publication-wiring.md
---

# Ticket 13 — KMP/KSP publication wiring

## Question (research subagent)

The Destination spec is locked except for this one non-blocking open item (map.md §"Destination: locked" → "Open work item (not blocking)"). Tickets 02 (KSP emits *whole* `value class` source into `build/generated/ksp/commonMain/kotlin`; cannot inject into existing files; `KompactAnnotations.kt` must be emitted as stubs into the consumer's common source roots) and 12 (split `:kompact` KMP runtime + `:kompact-ksp` JVM-only processor; `multiplatformPublication`; exact gradle wiring deferred) fix the design but defer the publication wiring.

Resolve by primary-source research (current Kotlin 2.x / KSP 2.x):

**(a) Publishing the KMP runtime `:kompact`** — exact `multiplatformPublication`/`mavenPublish` DSL for metadata + iosArm64 + iosSimulatorArm64 klibs (+ JVM/Android); plugin set; KLib publication DSL; ABI-baseline interaction.
**(b) Publishing + consuming the JVM-only KSP processor `:kompact-ksp` as a KSP-safe jar** — module plugins/apply; consumer `ksp(...)` coordinate; KSP-safe declaration (`ksp` vs `kspJvm`).
**(c) `binary-compatibility-validator`** — `apiValidation {}` block; baseline `.api` location for KMP; `apiCheck`/`apiDump` for metadata + klibs.
**(d) Ticket 02 "stub-source packaging in the consumer's KSP source roots"** — how `:kompact-ksp` emits `KompactAnnotations.kt` (`@KompactModel`/`@KompactField` stubs) into the consumer's `commonMain` source roots so generated value-class views compile.

## Answer

Resolved by a research subagent against current primary sources (Kotlin 2.x / KSP 2.x); findings in [`research/kmp-publication-wiring.md`](research/kmp-publication-wiring.md), folded below. This is the non-blocking deferred detail from Ticket 12 — the spec was already locked; this removes the last implementer-facing open question.

**Versions (docs last-modified / Maven Central, 2026-09-02):** KSP **2.3.11** (GitHub Releases 2026-08-03; the Maven-Central `symbol-processing-api` marker lags at 2.3.9 — consume KSP via the `com.google.devtools.ksp` Gradle **plugin**, not the API artifact, which lags). `binary-compatibility-validator` **0.18.0** stable on Maven Central (README references 0.18.1). `com.vanniktech.maven.publish` **0.37.0**.

**(a) Publishing the KMP runtime `:kompact`.** There is no hand-authored `multiplatformPublication`/`mavenPublish` DSL — KGP auto-creates the publications when `maven-publish` is applied. Plugins: `org.jetbrains.kotlin.multiplatform` + `maven-publish` + `org.jetbrains.kotlinx.binary-compatibility-validator` 0.18.0. Targets: `kotlin { jvm(); iosArm64(); iosSimulatorArm64(); androidLibrary { namespace; compileSdk; minSdk; withJava() } }`. What ships: per-target `<proj>-jvm` (.jar), `<proj>-iosarm64` (.klib), `<proj>-iossimulatorarm64` (.klib) — **klibs are published automatically per native target, no separate KLib publication DSL**. Root `kotlinMultiplatform` publication (`group:artifact`) carries Gradle module metadata referencing the per-target coordinates; for Maven Central wrap with `com.vanniktech.maven.publish` (0.37.0) → `mavenPublishing { coordinates(...); publishToMavenCentral(); signAllPublications(); pom { … } }`. Publish all targets from one macOS host (Kotlin/Native cross-compiles Apple klibs from any host; a Mac is only needed for cinterop/iOS binaries). BCV `apiCheck` runs in `check`, gating publication.

**(b) Publishing + consuming `:kompact-ksp` (the KSP-safe jar).** `:kompact-ksp` is a JVM-only module (`kotlin("jvm")`), published as a normal JVM jar + sources via `maven-publish`. "KSP-safe" is **not** a separate artifact — it is `compileOnly("com.google.devtools.ksp:symbol-processing-api:2.3.11")` so the jar does not transitively pin a KSP API version into consumers; the consumer's `com.google.devtools.ksp` plugin supplies the KSP runtime in an isolated processing classloader. Discovery is the service file `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider` containing the provider FQCN. **Consumer side:** since `:kompact-ksp` emits *common* code, add it once via `add("kspCommonMainMetadata", "ch.trancee.kompact:kompact-ksp:<ver>")` — **not** target-specific `kspJvm`/`kspIosArm64` (the processor isn't per-target), and **not** the bare deprecated `ksp(...)` (deprecated on KMP unless `ksp.allow.all.target.configuration=true`).

**(c) `binary-compatibility-validator`.** Block: `apiValidation { @OptIn(ExperimentalBCVApi); klib { enabled = true } }`. Golden files in VCS at `api/<proj>.api` (JVM public ABI) and `api/<proj>.klib.api` (merged native klib ABI). `apiDump` writes/overwrites both; `apiCheck` reads them and is auto-added to `check`. Caveat: on a non-Apple host BCV **infers** Apple-target klib ABI from supported targets (or fails with `strictValidation = true`); update `api/` on macOS when possible. Successor: KGP now ships a built-in `kotlin { abiValidation() }` (`checkKotlinAbi`/`updateKotlinAbi`) since BCV is maintenance-mode — evaluate for new setups, but BCV 0.18.0 remains the spec's named companion (tickets 10/12).

**(d) Ticket 02 stub-source packaging in consumer `commonMain`.** KSP **cannot** inject into existing source, so `KompactAnnotations.kt` (@KompactModel/@KompactField) + per-schema value-class views must be emitted as **whole generated files** into the consumer's common source root — Ticket 02's "ksp-stubs" design. Mechanism: the processor calls `CodeGenerator.createNewFile(Dependencies(aggregating=true,…), pkg="ch.trancee.kompact.runtime", fileName="KompactAnnotations")`; consumer declares `kspCommonMainMetadata` (the common-metadata compilation) so stubs land in `commonMain` (compiles for JVM + iOS). **Critical non-automatic step** — google/ksp issue #567 (open); the first-party KMP example ships `kspCommonMainMetadata` commented out:
```kotlin
kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")  // KSP-version-dependent path
}
tasks.withType<KotlinCompile<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}
```
For Kompact this means **no separate `:kompact-annotations` publishable artifact** — annotations come from the processor's generated stubs (coherent with Ticket 12's split: `kompact` runtime has no annotations; `:kompact-ksp` emits them as ksp-stubs). The stub file is an aggregating output (same content for all schemas); per-schema views are isolating per `containingFile`; the processor must sort outputs deterministically.

**Corrections to `docs/research/*`** (reference-only; re-derived from primary sources):
- `docs/research/ksp-kmp-generation.md`: its "dedicated cacheable JVM task" wrapper for `kspCommonMainMetadata` is valid only as a **project-owned** task (project owns inputs/outputs/dependencies per Gradle build-cache guidance), **not** a KSP-supported integration — do not treat `kspCommonMainMetadata` as automatic; the non-automatic seam (§d) is real. Its KSP 2.3.11 version claim is confirmed correct (Maven-Central marker just lags).
- `docs/research/allocation-boxing-measurement.md`: its "omitting `@JvmInline` is incompatible with the JVM value-class contract" is overruled by PROMPT.md §1 + map.md Reconciliation — `@JvmInline` is allowed only on the *generated JVM actual*; common source stays annotation-free. No change to the publication/ABI decision.

Informed by 02 + 12. **Non-blocking**: spec already locked (Tickets 01–12); this resolves only the deferred wiring so no implementer-facing question remains.

## Comments

- Research subagent `KmpPubResearch` executed the research; findings written to [`research/kmp-publication-wiring.md`](research/kmp-publication-wiring.md), verified against primary sources (Kotlin KMP publishing guide, KSP quickstart + KSP-with-KMP, google/ksp README + issue #567 + `CodeGenerator.kt`, kotlinx-binary-compatibility-validator README + KLibSupport, vanniktech/gradle-maven-publish, search.maven.org Solr API). 2026-09-02.
- Folded into `map.md` §Decisions-so-far + the "Open work item" paragraph (now RESOLVED).
