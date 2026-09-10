plugins {
    alias(libs.plugins.kmp) apply false
    alias(libs.plugins.bcv) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.kotlinPowerAssert) apply false
}

allprojects {
    group = "ch.trancee.kompact"
    version = "0.1.0-SNAPSHOT"
}

// Spotless: ktlint-based formatting for all Kotlin source and Gradle Kotlin DSL files.
spotless {
    kotlin {
        target("kompact/src/**/*.kt")
        ktlint()
    }
    kotlinGradle {
        target("build.gradle.kts")
        target("settings.gradle.kts")
        target("kompact/build.gradle.kts")
        ktlint()
    }
}
