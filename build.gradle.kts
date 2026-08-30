plugins {
    base
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinx.benchmark) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
}

group = "ch.trancee.kompact"

version = "0.1.0-SNAPSHOT"

allprojects {
    group = rootProject.group
    version = rootProject.version
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktfmt("0.58").kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktfmt("0.58").kotlinlangStyle()
    }
    format("misc") {
        target("*.md", "docs/**/*.md", "*.properties", "*.toml", ".gitignore", ".gitattributes")
        targetExclude("**/build/**", "PROMPT.md")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

val cConformanceBinary =
    layout.buildDirectory.file("tmp/compileCConformance/vehicle-telemetry-test")

val compileCConformance =
    tasks.register<Exec>("compileCConformance") {
        group = "verification"
        inputs.files(fileTree("conformance/c") { include("*.c", "*.h") })
        outputs.file(cConformanceBinary)
        commandLine(
            "cc",
            "-std=c99",
            "-Wall",
            "-Wextra",
            "-Wconversion",
            "-Wsign-conversion",
            "-Werror",
            "-pedantic-errors",
            "conformance/c/vehicle_telemetry_test.c",
            "-o",
            cConformanceBinary.get().asFile.absolutePath,
        )
    }

val cConformanceTest =
    tasks.register<Exec>("cConformanceTest") {
        group = "verification"
        dependsOn(compileCConformance)
        inputs.file(cConformanceBinary)
        commandLine(cConformanceBinary.get().asFile.absolutePath)
    }

tasks.named("check") { dependsOn(cConformanceTest) }

subprojects {
    tasks
        .matching { it.name == "check" }
        .configureEach {
            rootProject.tasks.named("check").configure { dependsOn(this@configureEach) }
        }
}

tasks.named("check") {
    dependsOn(":kompact-runtime:koverVerify")
    dependsOn(":kompact-benchmarks:jvmSmokeBenchmark")
}
