plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}

// Convention plugins compile against the real Gradle plugins; versions come from the root catalog.
dependencies {
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${libs.versions.dokka.get()}")
    // Kover's HTML report pulls in freemarker, which has an unpatched advisory and is not
    // resolvable as a project dependency (breaks Dependabot). Only XML reports are used.
    implementation("org.jetbrains.kotlinx.kover:org.jetbrains.kotlinx.kover.gradle.plugin:${libs.versions.kover.get()}") {
        exclude(group = "org.freemarker", module = "freemarker")
    }
}
