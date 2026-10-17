plugins {
    alias(libs.plugins.kmp) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.agp) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.kotlinPowerAssert) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.benchmark) apply false
}

val koverVersion = libs.versions.kover.get()
subprojects {
    buildscript {
        dependencies {
            classpath("org.jetbrains.kotlinx.kover:org.jetbrains.kotlinx.kover.gradle.plugin:$koverVersion") {
                exclude(group = "org.freemarker", module = "freemarker")
            }
        }
    }
}

allprojects {
    group = "ch.trancee.kompact"
    version = "0.9.0-SNAPSHOT"
}

// Keep the unpublished root from competing with :kompact in composite dependency substitution.
group = "ch.trancee.kompact.build"

// Spotless: ktlint-based formatting for all Kotlin source and Gradle Kotlin DSL files.
spotless {
    kotlin {
        target("kompact/src/**/*.kt")
        target("kompact-gradle-plugin/src/**/*.kt")
        target("kompact-ksp/src/**/*.kt")
        target("kompact-ksp-integration/src/**/*.kt")
        target("benchmarks/src/**/*.kt", "benchmarks/holders/src/**/*.kt")
        ktlint()
    }
    kotlinGradle {
        target("build.gradle.kts")
        target("settings.gradle.kts")
        target("kompact/build.gradle.kts")
        target("kompact-gradle-plugin/build.gradle.kts")
        target("kompact-ksp/build.gradle.kts")
        target("kompact-ksp-integration/build.gradle.kts")
        target("benchmarks/build.gradle.kts")
        target("build-logic/build.gradle.kts")
        target("build-logic/settings.gradle.kts")
        target(
            "build-logic/src/main/kotlin/*.gradle.kts",
            "build.gradle.kts",
            "benchmarks/holders/*.gradle.kts",
        )
        ktlint()
    }
}
