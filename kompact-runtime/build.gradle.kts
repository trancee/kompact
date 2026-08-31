plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    `maven-publish`
}

kotlin {
    explicitApi()
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { enabled.set(true) }

    jvm()
    androidLibrary {
        namespace = "ch.trancee.kompact.runtime"
        compileSdk = 36
        minSdk = 23
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets { commonTest.dependencies { implementation(kotlin("test")) } }
}

kover {
    reports {
        verify {
            rule("complete line coverage") {
                minBound(
                    100,
                    kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE,
                    kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE,
                )
            }
            rule("complete branch coverage") {
                minBound(
                    100,
                    kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH,
                    kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE,
                )
            }
        }
    }
}
