// :kompact-example — consumer example (Ticket 13).
// Demonstrates the published :kompact runtime + :kompact-ksp processor being
// applied via kspCommonMainMetadata. KSP emits the expect + per-target
// actuals into the commonMain generated root; the consumer's srcDir
// wiring (below) puts them into the build source set.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)

    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":kompact"))
                // KSP-safe processor on the common-metadata configuration
                // (per KSP Gradle Configurations Reference + Ticket 13).
                dependencies.add("kspCommonMainMetadata", project(":kompact-ksp"))
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// Manual wiring required by google/ksp#567: KSP-generated common sources
// do not automatically compile into each target's commonMain.
kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}
tasks.matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }
    .configureEach {
        dependsOn("kspCommonMainKotlinMetadata")
    }
tasks.matching { it.name != "kspCommonMainKotlinMetadata" && it.name.startsWith("compile") }
    .configureEach {
        dependsOn("kspCommonMainKotlinMetadata")
    }
