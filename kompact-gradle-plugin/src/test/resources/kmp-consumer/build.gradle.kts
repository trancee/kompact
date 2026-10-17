plugins {
    kotlin("multiplatform") version "${kotlinVersion}"
    id("com.android.kotlin.multiplatform.library") version "${agpVersion}"
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
                implementation("ch.trancee.kompact:kompact:${pluginVersion}")
            }
        }

        configurations.configureEach {
            resolutionStrategy.capabilitiesResolution.withCapability("org.jetbrains.kotlin:kotlin-test-framework-impl") {
                select("org.jetbrains.kotlin:kotlin-test-junit5:${kotlinVersion}")
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
                implementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
                runtimeOnly("org.junit.platform:junit-platform-launcher:${junitVersion}")
            }
        }
    }
}
