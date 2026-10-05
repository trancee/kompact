plugins {
    kotlin("multiplatform") version "2.4.20"
    id("com.android.kotlin.multiplatform.library") version "9.4.1"
    id("ch.trancee.kompact.codegen")
}

kotlin {
    android {
        namespace = "example"
        compileSdk = 36
        minSdk = 21
        withJava()
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    androidNativeArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("ch.trancee.kompact:kompact:0.5.0-SNAPSHOT")
            }
        }

        configurations.configureEach {
            resolutionStrategy.capabilitiesResolution.withCapability("org.jetbrains.kotlin:kotlin-test-framework-impl") {
                select("org.jetbrains.kotlin:kotlin-test-junit5:2.4.20")
            }
        }

        tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
            useJUnitPlatform()
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5")) {
                    exclude(group = "org.junit.jupiter")
                    exclude(group = "org.junit.platform")
                }
                implementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
                runtimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
            }
        }
    }
}
