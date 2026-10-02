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
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
