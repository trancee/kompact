plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    `maven-publish`
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    basePath = rootProject.projectDir.absolutePath
}

dependencies {
    implementation(project(":kompact-annotations"))
    implementation(libs.ksp.api)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

publishing { publications { create<MavenPublication>("maven") { from(components["java"]) } } }
