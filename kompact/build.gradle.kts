// :kompact — Kotlin Multiplatform runtime + commonMain Main API.
// Spec: .scratch/kompact-spec/map.md (Tickets 01–13).
// Targets: jvm, iosArm64, iosSimulatorArm64 (Ticket 03 platforms).
// Publication: vanniktech maven-publish (Ticket 13).
// BCV auto-applies from the root build script.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.vanniktechMavenPublish)
}

kotlin {
    jvmToolchain(17)

    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.test.annotations.common)
            }
        }
    }
}
