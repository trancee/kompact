@file:OptIn(
    kotlinx.validation.ExperimentalBCVApi::class,
)

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.bcv)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    `maven-publish`
    signing
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Align Java compilation target with Kotlin's JVM_21 to satisfy KGP's
// cross-task validation (JDK 25 host defaults to v69 for Java, v65 for Kotlin;
// both must match — Ticket 13 constraint).
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "21"
    targetCompatibility = "21"
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

// --- Maven Central Portal publishing ---
publishing {
    publications {
        all {
            if (this is MavenPublication) {
                pom {
                    name.set("Kompact KSP")
                    description.set(
                        "KSP processor for Kompact @KompactModel schemas — " +
                            "generates value-class views with zero-alloc bit-stream reads.",
                    )
                    url.set("https://github.com/trancee/kompact")
                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("trancee")
                            name.set("Philipp Grosswiler")
                            email.set("philipp.grosswiler@gmail.com")
                        }
                    }
                    scm {
                        url.set("https://github.com/trancee/kompact")
                        connection.set("scm:git:git://github.com/trancee/kompact.git")
                        developerConnection.set("scm:git:ssh://git@github.com/trancee/kompact.git")
                    }
                }
            }
        }
    }
}
