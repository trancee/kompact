plugins {
    alias(libs.plugins.kmp) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.agp) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlinPowerAssert) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.benchmark) apply false
}

allprojects {
    group = "ch.trancee.kompact"
    version = "0.10.0"
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
        target("build-logic/src/**/*.kt")
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

// Detekt: static analysis of published production sources (tests and build tooling excluded).
// Type resolution is not used; findings come from the plain `detekt` task.
detekt {
    source.setFrom(
        "kompact/src/commonMain/kotlin",
        "kompact-ksp/src/main/kotlin",
        "kompact-gradle-plugin/src/main/kotlin",
    )
    config.setFrom("config/detekt/detekt.yml")
    baseline = file("config/detekt/baseline.xml")
    buildUponDefaultConfig = true
}
