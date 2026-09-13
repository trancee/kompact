@file:OptIn(
    org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class,
    org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class,
)

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.agp)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinPowerAssert)
    id("dokka-markdown")
    id("portal-publish")
}

kotlin {
    // Pin JVM target to 21 LTS so the built-in ABI validation (ASM) can parse the
    // emitted class files on hosts running JDK 25 (without an explicit target,
    // Kotlin 2.4.x emits v67 for JVM and v68 for Android).
    android {
        namespace = "ch.trancee.kompact"
        compileSdk = 36
        minSdk = 21
        withJava()
    }
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain =
            getByName("commonMain") {
                compilerOptions {
                    // KT-61573: expect/actual classes are in Beta in Kotlin 2.4.20.
                    // The -Xexpect-actual-classes flag suppresses the Beta warning.
                    // Keep this until the feature reaches Stable.
                    freeCompilerArgs.addAll("-Xexpect-actual-classes")
                }
            }
        val commonTest =
            getByName("commonTest") {
                dependencies {
                    // kotlin("test") is version-aligned to the Kotlin Gradle plugin (catalog'd).
                    implementation(kotlin("test"))
                }
            }
        // Shared JVM+Android source set: @JvmInline actuals + generated views
        // that compile for both JVM and Android targets (agp.com.android.kotlin.multiplatform.library).
        val jvmMain = getByName("jvmMain")
        val jvmTest =
            getByName("jvmTest") {
                dependencies {
                    // kotlin("test") (from commonTest) provides kotlin.test assertions
                    // and JUnit 4 transitively on the JVM. No explicit JUnit dep needed.
                }
            }
        // Shared iOS source set (Ticket 03 expect/actual value class).
        // gradle.properties: kotlin.mpp.applyDefaultHierarchyTemplate=false so this
        // intermediate is the sole iosMain (avoids the default-template conflict).
        val iosMain = create("iosMain")
        iosMain.dependsOn(commonMain)
        getByName("iosArm64Main") { dependsOn(iosMain) }
        getByName("iosSimulatorArm64Main") { dependsOn(iosMain) }
        // jvmCommon: shared intermediate between commonMain and jvmMain/androidMain.
        // Moved @JvmInline actuals + VehicleTelemetry here so both JVM and Android
        // targets compile them. JvmCoveragePinning.java stays in jvmMain (JVM-only).
        val jvmCommon = create("jvmCommon") {
            dependsOn(commonMain)
        }
        jvmMain.dependsOn(jvmCommon)
        getByName("androidMain") { dependsOn(jvmCommon) }
    }

    // Built-in ABI validation (KGP 2.1.0+): replaces BCV's apiValidation { klib { ... } }.
    // In KGP 2.4.10, the klib { } block was removed — klib validation is now automatic
    // for KMP projects (enabled by default when abiValidation is accessed).
    // keepLocallyUnsupportedTargets defaults to true — macOS validates all targets
    // for real (strict); Linux infers iOS klib but validates JVM + Android for real.
    abiValidation {
        keepLocallyUnsupportedTargets = true
    }
}

// --- SKIE (iOS/Swift interop improvements) ---
// SKIE (co.touchlab.skie) provides KMP-to-Swift interop via a Gradle plugin
// extension. SKIE 0.10.14 (latest) supports Kotlin up to 2.4.20; the project
// runs Kotlin 2.4.20, which is within SKIE's supported range. SKIE is declared
// as `apply false` in the root build.gradle.kts and is NOT yet applied to
// this module. When enabled, add `alias(libs.plugins.skie)` to this module's
// plugins block and enable the desired features:
//   - Sealed class → Swift enum conversion (e.g. KompactDecodeError)
//   - Value class Swift-friendliness (e.g. BooleanResult, ByteResult, etc.)

// --- Kover (100 % line + branch coverage on the JVM target) ---
// Kover measures coverage from the jvmTest execution via its JVM TI agent
// (not JaCoCo). For KMP projects, coverage is collected from the compiled JVM
// bytecode (commonMain + jvmMain). The verify rules enforce strict 100 % thresholds.
// See: kotlinx.kover.gradle.plugin.dsl (KoverProjectExtension → reports → total/verify)
//
// Kover filters exclude test-only utility classes that intentionally contain
// never-called constructors and assertion branches — these are coverage-pinning
// scaffolding (see JvmCoveragePinning.java), not production logic. The JVM TI
// agent tracks INVOKEVIRTUAL (synthetic @JvmInline getters), not GETFIELD, so
// the Java scaffolding forces method-level coverage.
kover {
    reports {
        filters {
            excludes {
                classes("ch.trancee.kompact.runtime.JvmCoveragePinning*")
            }
        }
        total {
            xml {
                onCheck.set(true)
            }
            html {
                onCheck.set(true)
            }
        }
        verify {
            rule {
                minBound(100, CoverageUnit.LINE)
            }
            rule {
                minBound(100, CoverageUnit.BRANCH)
            }
        }
    }
}

// --- Kotlin Power-Assert (enhanced test failure messages) ---
// Power-Assert transforms assertion calls in test source sets, rendering
// sub-expressions and intermediate values in failure messages.
// The compilationFilter defaults to TESTS (commonTest, jvmTest, iosTest).
powerAssert {
    functions =
        listOf(
            "kotlin.assert",
            "kotlin.require",
            "kotlin.requireNotNull",
            "kotlin.check",
            "kotlin.checkNotNull",
            "kotlin.test.assertTrue",
            "kotlin.test.assertFalse",
            "kotlin.test.assertEquals",
            "kotlin.test.assertNotEquals",
            "kotlin.test.assertNull",
            "kotlin.test.assertNotNull",
            "kotlin.test.assertContentEquals",
            "kotlin.test.assertContentNotEquals",
            "kotlin.test.assertContains",
            "kotlin.test.assertNotContains",
            "kotlin.test.assertFails",
            "kotlin.test.assertFailsWith",
        )
}

// Exclude JVM-coverage-pinning scaffolding (JvmCoveragePinning.java) from the
// published JVM JAR. It lives in jvmMain so KMP compiles it before the Kotlin
// test sources that reflectively exercise @JvmInline getters (JaCoCo/Kover
// tracks INVOKEVIRTUAL but not GETFIELD). It is package-private (excluded from
// ABI) and Kover-filtered — it must not ship in the Maven artifact.
tasks.named<Jar>("jvmJar") {
    exclude("ch/trancee/kompact/runtime/JvmCoveragePinning*.class")
    exclude("ch/trancee/kompact/runtime/JvmCoveragePinning*.java")
}

// --- Maven Central Portal publishing ---
// Route: Portal Publisher API (direct integration — no third-party plugin).
// Signing: PGP key injected via environment variables (never in source).
// See the maven-central-publishing skill for the full workflow.

// KGP auto-creates jvmSourcesJar and auto-attaches it to the JVM publication.
// KGP already includes commonMain + jvmMain sources; we add jvmCommon as a safety net
// (custom source set may not be auto-included by KGP's jvmSourcesJar).
// Uses withGroovyBuilder to avoid type-cast issues with KGP's internal task type.
val commonMainSource = kotlin.sourceSets.getByName("commonMain")
val jvmCommonSource = kotlin.sourceSets.getByName("jvmCommon")
afterEvaluate {
    val task = tasks.findByName("jvmSourcesJar")
    if (task != null) {
        task.withGroovyBuilder {
            invokeMethod("setDuplicatesStrategy", DuplicatesStrategy.EXCLUDE)
            invokeMethod("from", commonMainSource.kotlin)
            invokeMethod("from", jvmCommonSource.kotlin)
        }
    }
}

// Javadoc JAR for the JVM target — Central requires a Javadoc artifact for JVM publications.
// Dokka 2.x generates GFM Markdown from KDoc via the dokka-markdown convention plugin
// (producing dokkaGeneratePublicationMarkdown, verified on Kotlin 2.4.10). Dokka 2.x has
// no Javadoc-*format* task — the legacy dokkaJavadoc/dokkaGfm task names were
// removed/changed in 2.x — so the published javadoc artifact remains a minimal README
// stub, which Maven Central accepts for KMP projects. The full generated API reference
// is rendered as GFM Markdown into docs/api (committed; see docs/api-reference.md)
val dokkaJavadocJar =
    tasks.register<Jar>("dokkaJavadocJar") {
        archiveClassifier.set("javadoc")
        from(rootProject.file("README.md"))
    }

// Generated Markdown (GFM) API reference, committed under docs/api so the
// api-reference.md pointer is always live. Regenerate with
// `:kompact:dokkaGeneratePublicationMarkdown`.
// The `dokka-markdown` convention plugin (build-logic/src/main/kotlin/dokka-markdown.gradle.kts)
// registers the Markdown format via DokkaFormatPlugin("markdown"), producing
// dokkaGeneratePublicationMarkdown (V2 task). It also adds gfm-plugin +
// gfm-template-processing-plugin dependencies.
tasks.named<org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask>("dokkaGeneratePublicationMarkdown") {
    outputDirectory.set(layout.projectDirectory.dir("docs/api"))
}

// POM metadata: module-specific name + description here; common fields
// (URL, license, developer, SCM) are configured by the portal-publish
// convention plugin's afterEvaluate hook. The bundleDir repository is
// also configured there.
publishing {
    publications {
        all {
            if (this is MavenPublication) {
                pom {
                    name.set("Kompact")
                    description.set(
                        "Zero-allocation bit-stream pack/unpack primitives and generated model views " +
                            "for Kotlin Multiplatform.",
                    )
                }
            }
        }
    }
}

// Attach the Dokka Javadoc JAR to the JVM publication.
// KGP auto-attaches jvmSourcesJar to the JVM publication; we only add dokkaJavadocJar.
// KGP creates KMP publications during evaluation, so this runs after.
afterEvaluate {
    publishing.publications.all {
        if (this is MavenPublication && (name == "jvm" || name == "android")) {
            artifact(dokkaJavadocJar.get())
        }
    }
}

// --- Central Portal Publisher API tasks ---
// (portal-publish convention plugin in build-logic/ provides: generateChecksums,
// assembleCentralBundle, centralPortalDeploy, centralPortalStatus, centralPortalPublish.
// Publishing repository (bundleDir) and PGP signing are also configured there.)

// F-002 boundary tests allocate 256 MiB buffers; give the JVM test fork headroom.
// Power-Assert's expression diagram renderer needs additional headroom for
// the 256 MiB ByteArray captured in assertion expressions.
tasks.withType<Test>().configureEach {
    maxHeapSize = "4g"
}

// Align Java compilation target with Kotlin's JVM_21 to satisfy KGP's
// cross-task validation (JDK 25 host defaults to v69 for Java, v68 for Kotlin
// with Kotlin 2.4.20; both must match for ABI validation to parse class files).
tasks.withType<JavaCompile>().configureEach {
    if (name.contains("JvmMain") || name.contains("JvmTest")) {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}
