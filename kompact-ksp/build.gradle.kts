// :kompact-ksp — JVM-only KSP processor (Ticket 02/12/13).
// Emits KompactAnnotations.kt stub + per-schema value-class views into
// the consumer's commonMain source root (kspCommonMainMetadata).
// KSP-safe: symbol-processing-api is compileOnly, not implementation.
// BCV is applied at the root build script (auto-applies to all subprojects).

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":kompact"))
    // KSP-safe: the consumer's KSP plugin supplies the KSP runtime in an
    // isolated processing classloader. compileOnly avoids pinning the API.
    compileOnly(libs.ksp.api)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.annotations.common)
}
