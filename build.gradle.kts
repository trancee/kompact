// Root build script for the Kompact multi-module KMP project.
// BCV is applied at the root (auto-applies to subprojects). Each KMP
// subproject enables klib validation locally (see kompact/build.gradle.kts).

plugins {
    alias(libs.plugins.bcv)
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.vanniktechMavenPublish) apply false
}

allprojects {
    group = "ch.trancee.kompact"
    version = "0.1.0-SNAPSHOT"
}
