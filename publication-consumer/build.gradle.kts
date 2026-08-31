plugins {
    kotlin("multiplatform") version "2.3.20"
    id("com.android.kotlin.multiplatform.library") version "9.0.0"
    id("ch.trancee.kompact") version "0.1.0-SNAPSHOT"
}

kotlin {
    jvm()
    androidLibrary {
        namespace = "ch.trancee.kompact.publication.consumer"
        compileSdk = 36
        minSdk = 23
    }
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            implementation("ch.trancee.kompact:kompact-runtime:0.1.0-SNAPSHOT")
            implementation("ch.trancee.kompact:kompact-annotations:0.1.0-SNAPSHOT")
        }
    }
}

kompact {
    namespace.set("publication")
    maxPacketBytes.set(244)
}
