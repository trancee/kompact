@file:OptIn(
    org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class,
    org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class,
)

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import io.github.anschnapp.mutflow.gradle.MutflowExtension
import org.gradle.api.GradleException
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    alias(libs.plugins.mutflow) apply false
    alias(libs.plugins.kmp)
    alias(libs.plugins.agp)
    alias(libs.plugins.kotlinPowerAssert)
    id("dokka-markdown")
    id("portal-publish")
}

dokka {
    dokkaPublications.configureEach {
        suppressInheritedMembers.set(true)
    }
}

val mutationJvmOnly =
    providers.gradleProperty("mutationTest.jvmOnly").map(String::toBooleanStrict).getOrElse(false)
if (mutationJvmOnly) {
    val requestedTasks = gradle.startParameter.taskNames
    val onlyMutationResultsRequested =
        requestedTasks.size == 1 &&
            requestedTasks.single() in setOf("mutationResults", "kompact:mutationResults", ":kompact:mutationResults")
    if (!onlyMutationResultsRequested) {
        throw GradleException(
            "-PmutationTest.jvmOnly=true is only valid for the :kompact:mutationResults task."
        )
    }
    // MutFlow 1.6.1 injects dependencies into common source sets. Apply it only
    // when the reduced model contains JVM, the target for which it publishes variants.
    pluginManager.apply("io.github.anschnapp.mutflow")
    apply(from = rootProject.file(".omp/mutation-results.gradle.kts"))
    extensions.configure<MutflowExtension>("mutflow") {
        targets.addAll(
            listOf(
                "ch.trancee.kompact.runtime.KompactRuntime",
                "ch.trancee.kompact.runtime.KompactCursor",
                "ch.trancee.kompact.runtime.KompactCursorByteRanges",
                "ch.trancee.kompact.runtime.KompactCursorRepeats",
                "ch.trancee.kompact.runtime.KompactByteRange",
            ),
        )
        maxMutationRuns.set(Int.MAX_VALUE)
    }
} else {
    tasks.register("mutationResults") {
        group = "verification"
        description = "Requires -PmutationTest.jvmOnly=true for this MutFlow 1.6.1 evaluation."
        doLast {
            throw GradleException(
                "Run :kompact:mutationResults with -PmutationTest.jvmOnly=true."
            )
        }
    }
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
        withHostTest {}
    }
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    if (!mutationJvmOnly) {
        // Release-optimized test binaries (releaseTest/test.kexe) carry the
        // allocation probes into optimized code for device evidence.
        listOf(iosArm64(), iosSimulatorArm64(), androidNativeArm64()).forEach { target ->
            target.binaries.test(listOf(org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE))
        }
    }

    sourceSets {
        getByName("commonTest") {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test:${libs.versions.kotlin.get()}")
            }
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(kotlin("test-junit5")) {
                    exclude(group = "org.junit.jupiter")
                    exclude(group = "org.junit.platform")
                }
                implementation(libs.junitJupiterApi)
                runtimeOnly(libs.junitJupiterEngine)
                runtimeOnly(libs.junitPlatformLauncher)
            }
        }
        getByName("jvmTest") {
            dependencies {
                // Kotlin 2.4.20 exposes its Jupiter binding under the legacy junit5 artifact name.
                // Exclude its JUnit 5 transitives and bind the tests to JUnit 6 explicitly.
                implementation(kotlin("test-junit5")) {
                    exclude(group = "org.junit.jupiter")
                    exclude(group = "org.junit.platform")
                }
                implementation("org.junit.jupiter:junit-jupiter-api:${libs.versions.junit.get()}")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:${libs.versions.junit.get()}")
                runtimeOnly("org.junit.platform:junit-platform-launcher:${libs.versions.junit.get()}")
            }
        }
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

if (mutationJvmOnly) {
    val mutatedTest = kotlin.targets.getByName<KotlinJvmTarget>("jvm")
        .compilations.getByName("mutatedTest")
    mutatedTest.defaultSourceSet.kotlin.srcDir(layout.projectDirectory.dir("src/mutflowTest/kotlin"))
    // MutFlow's generated test compilation omits the regular JVM Java output.
    mutatedTest.defaultSourceSet.dependencies {
        implementation(files(tasks.named<JavaCompile>("compileJvmMainJava").map { it.destinationDirectory }))
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
apply(plugin = "org.jetbrains.kotlinx.kover")

extensions.configure<KoverProjectExtension>("kover") {
    reports {
        total {
            xml {
                onCheck.set(true)
            }
            html {
                onCheck.set(false)
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
// Exception assertions execute blocks rather than inspect value expressions;
// leave assertFails/assertFailsWith unselected to avoid constant-expression diagnostics.
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
        )
}

// --- Maven Central Portal publishing ---
// Route: Portal Publisher API (direct integration — no third-party plugin).
// Signing: PGP key injected via environment variables (never in source).
// See the maven-central-publishing skill for the full workflow.

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
                        "Bit-stream packing and unpacking primitives with generated model views " +
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
//
// The signing plugin (configured in portal-publish.gradle.kts) also runs an
// afterEvaluate that calls sign(publishing.publications). Because convention-
// plugin afterEvaluate callbacks fire before this block, the signing plugin does
// not see dokkaJavadocJar when it wires up task dependencies. On Gradle 9.x
// this triggers strict "implicit dependency" validation: publishAndroidPublication
// picks up the .asc produced by signJvmPublication without declaring a dependency.
// Fix: explicitly wire publish-to-bundleDir tasks to depend on all sign tasks
// so every .asc output exists before any publish task runs.
@Suppress("unused")
afterEvaluate {
    publishing.publications.all {
        if (this is MavenPublication && (name == "jvm" || name == "android")) {
            artifact(dokkaJavadocJar.get())
        }
    }
    // Gradle 9.x strict task-dependency validation for shared artifacts.
    val signTasks = tasks.matching { it.name.startsWith("sign") }
    tasks.matching {
        it.name.startsWith("publish") && it.name.contains("ToBundleDirRepository")
    }.configureEach {
        dependsOn(signTasks)
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
    useJUnitPlatform()
    maxHeapSize = "4g"
}

// Align Java compilation target with Kotlin's JVM_21 to satisfy KGP's
// cross-task validation (JDK 25 host defaults to v69 for Java, v68 for Kotlin
// with Kotlin 2.4.20; both must match for ABI validation to parse class files).
tasks.withType<JavaCompile>().configureEach {
    if (
        name.contains("JvmMain") ||
            name.contains("JvmTest") ||
            name.contains("JvmMutatedMain") ||
            name.contains("JvmMutatedTest")
    ) {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}
