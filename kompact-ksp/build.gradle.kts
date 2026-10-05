@file:OptIn(
    org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class,
)

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import io.github.anschnapp.mutflow.gradle.MutflowExtension
import org.gradle.api.GradleException
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.mutflow) apply false
    alias(libs.plugins.kotlinJvm)
    id("dokka-markdown")
    id("portal-publish")
    // maven-publish is also applied by portal-publish convention plugin, but
    // listed here to ensure KGP's kotlin("jvm") publication auto-creation
    // detects it during plugins{} block processing (convention plugin
    // application can be too late for KGP's PluginManager listener).
    `maven-publish`
}

kotlin {
    // KSP 2.3.10 pairs with Kotlin 2.4.20 (per Kotlin docs — KSP version track diverges
    // from Kotlin's; `ksp = "2.4.20"` will NOT resolve). Compiled against the lowest
    // supported KSP 2.3.x so the binary-compatible validate$default call works for
    // all consumers on KSP 2.3.10–2.3.12+. The KSP processor loads into the consumer's
    // Kotlin compile daemon; JVM 17 bytecode ensures compatibility with consumers on
    // JDK 17+ (the KSP plugin rejects jvmTarget=21 when the consumer runs JDK 17).
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }

    // Built-in ABI validation (KGP 2.1.0+) — validates the JVM public API surface.
    abiValidation {}
}

val mutationEnabled =
    providers.gradleProperty("mutationTest.enabled").map(String::toBooleanStrict).getOrElse(false)
if (mutationEnabled) {
    val requestedTasks = gradle.startParameter.taskNames
    val onlyMutationResultsRequested =
        requestedTasks.size == 1 &&
            requestedTasks.single() in setOf("mutationResults", "kompact-ksp:mutationResults", ":kompact-ksp:mutationResults")
    if (!onlyMutationResultsRequested) {
        throw GradleException(
            "-PmutationTest.enabled=true is only valid for the :kompact-ksp:mutationResults task."
        )
    }
    pluginManager.apply("io.github.anschnapp.mutflow")
    apply(from = rootProject.file(".omp/mutation-results.gradle.kts"))
    extensions.configure<MutflowExtension>("mutflow") {
        enabled = true
        maxMutationRuns.set(Int.MAX_VALUE)
        targets.addAll(
            listOf(
                "ch.trancee.kompact.ksp.gen.FramedClassGenerator",
                "ch.trancee.kompact.ksp.gen.FramedHolderGenerator",
                "ch.trancee.kompact.ksp.gen.FramedScalarHolderGenerator",
                "ch.trancee.kompact.ksp.gen.ValueClassGenerator",
                "ch.trancee.kompact.ksp.gen.ValueHolderGenerator",
            ),
        )
    }
    sourceSets.getByName("test").kotlin.srcDir("src/mutflowTest/kotlin")
} else {
    tasks.register("mutationResults") {
        group = "verification"
        description = "Requires -PmutationTest.enabled=true for this MutFlow JVM evaluation."
        doLast {
            throw GradleException(
                "Run :kompact-ksp:mutationResults with -PmutationTest.enabled=true."
            )
        }
    }
}

// Align Java compilation target with Kotlin's JVM_17 to satisfy KGP's
// cross-task validation (JDK 25 host defaults to v69 for Java, v67 for Kotlin
// with KGP 2.4.10; both must match for ABI validation to parse class files).
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

// --- Dependencies ---
// compileOnly: KSP API is provided by the KSP Gradle plugin at processing time.
// implementation: KotlinPoet for type-safe code generation.
// testImplementation: kotlin-test + KSP test harness.
dependencies {
    compileOnly(libs.symbolProcessingApi)
    implementation(libs.kotlinpoet)

    // testImplementation needs the KSP API on the runtime classpath so test
    // harness can construct mock SymbolProcessorEnvironment instances.
    testImplementation(libs.symbolProcessingApi)
    // Kotlin 2.4.20 exposes its Jupiter binding under the legacy junit5 artifact name.
    // Exclude its JUnit 5 transitives and bind the tests to JUnit 6 explicitly.
    testImplementation(libs.kotlinTestJupiter) {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
    }
    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

// Keep Kotlin internal-name mangling stable between main and MutFlow mutatedMain.
// Test sources call internal generator helpers; the default mutatedMain module
// name changes their JVM signatures and breaks those test calls at runtime.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name == "compileMutatedMainKotlin") {
        compilerOptions.moduleName.set("ch.trancee.kompact_kompact-ksp")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// --- Kover (100 % line + branch coverage on the KSP processor) ---
apply(plugin = "org.jetbrains.kotlinx.kover")

extensions.configure<KoverProjectExtension>("kover") {
    reports {
        total {
            xml { onCheck.set(true) }
            html { onCheck.set(false) }
        }
        verify {
            rule { minBound(100, CoverageUnit.LINE) }
            rule { minBound(100, CoverageUnit.BRANCH) }
        }
    }
}

// --- KSP processor: registered via ServiceLoader so KSP discovers it ---
// The service file at src/main/resources/META-INF/services/ is already
// included in the jar by default — no explicit from() needed (it caused
// a duplicate-copy warning with DuplicatesStrategy.WARN).
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// --- Sources JAR + Javadoc stub JAR for the JVM publication ---
// KGP's kotlin("jvm") does NOT auto-create a sources JAR (unlike KMP jvmTarget).
// Central Portal requires both artifacts for JVM publications.
val jvmSourcesJar =
    tasks.register<Jar>("jvmSourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

val dokkaJavadocJar =
    tasks.register<Jar>("dokkaJavadocJar") {
        archiveClassifier.set("javadoc")
        from(rootProject.file("README.md"))
    }

// --- Maven Central Portal publishing ---
// The portal-publish convention plugin applies maven-publish + signing,
// configures the bundleDir repository, and applies common POM metadata
// (URL, license, developer, SCM) via afterEvaluate. KGP's kotlin("jvm")
// may not auto-create a JVM publication when maven-publish is applied via
// convention plugin, so we create it explicitly from the "java" component.
publishing {
    publications {
        create<MavenPublication>("jvm") {
            from(components["java"])
            artifact(jvmSourcesJar.get())
            artifact(dokkaJavadocJar.get())
            pom {
                name.set("Kompact KSP")
                description.set(
                    "KSP processor for Kompact @KompactModel schemas that generates Kotlin views.",
                )
            }
        }
    }
    // bundleDir repository + common POM fields are configured by the
    // portal-publish convention plugin.
}
