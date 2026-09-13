plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}

// Dokka 2.2.0 is needed at build-logic classpath for the dokka-markdown convention plugin,
// which extends DokkaFormatPlugin("markdown").
dependencies {
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:2.2.0")
}
