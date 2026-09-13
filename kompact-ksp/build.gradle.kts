@file:OptIn(
    org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class,
)

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kover)
    id("dokka-markdown")
    id("portal-publish")
    // maven-publish is also applied by portal-publish convention plugin, but
    // listed here to ensure KGP's kotlin("jvm") publication auto-creation
    // detects it during plugins{} block processing (convention plugin
    // application can be too late for KGP's PluginManager listener).
    `maven-publish`
}

kotlin {
    // KSP 2.3.12 pairs with Kotlin 2.4.20 (per Kotlin docs). The KSP processor
    // loads into the consumer's Kotlin compile daemon; JVM 17 bytecode ensures
    // compatibility with consumers on JDK 17+ (the KSP plugin rejects jvmTarget=21
    // when the consumer runs JDK 17).
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }

    // Built-in ABI validation (KGP 2.1.0+) — validates the JVM public API surface.
    abiValidation {}
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
    testImplementation(kotlin("test"))
}

// --- Kover (100 % line + branch coverage on the KSP processor) ---
kover {
    reports {
        total {
            xml { onCheck.set(true) }
            html { onCheck.set(true) }
        }
        verify {
            rule { minBound(100, CoverageUnit.LINE) }
            rule { minBound(100, CoverageUnit.BRANCH) }
        }
    }
}

// --- KSP processor: registered via ServiceLoader so KSP discovers it ---
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.WARN
    from(
        sourceSets.main
            .get()
            .resources.srcDirs,
    )
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
                    "KSP processor for Kompact @KompactModel schemas — " +
                        "generates value-class views with zero-alloc bit-stream reads.",
                )
            }
        }
    }
    // bundleDir repository + common POM fields are configured by the
    // portal-publish convention plugin.
}
