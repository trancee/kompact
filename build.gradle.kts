plugins {
    alias(libs.plugins.kmp) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.agp) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.kotlinPowerAssert) apply false
    alias(libs.plugins.ksp) apply false
}

allprojects {
    group = "ch.trancee.kompact"
    version = "0.0.0-SNAPSHOT"
}

// Spotless: ktlint-based formatting for all Kotlin source and Gradle Kotlin DSL files.
spotless {
    kotlin {
        target("kompact/src/**/*.kt")
        target("kompact-ksp/src/**/*.kt")
        ktlint()
    }
    kotlinGradle {
        target("build.gradle.kts")
        target("settings.gradle.kts")
        target("kompact/build.gradle.kts")
        target("kompact-ksp/build.gradle.kts")
        target("build-logic/build.gradle.kts")
        target("build-logic/settings.gradle.kts")
        target("build-logic/src/main/kotlin/*.gradle.kts")
        ktlint()
    }
}
