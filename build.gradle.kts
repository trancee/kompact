plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.binaryCompatibilityValidator) apply false
    alias(libs.plugins.vanniktechMavenPublish) apply false
}

allprojects {
    group = "ch.trancee.kompact"
    version = "0.1.0-SNAPSHOT"
}
