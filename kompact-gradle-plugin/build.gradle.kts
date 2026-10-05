import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.kotlinJvm)
    `maven-publish`
    id("portal-publish")
    `java-gradle-plugin`
}

val kspVersion = libs.versions.ksp.get()
val kspCoroutinesVersion = libs.versions.kspCoroutines.get()
val kotlinVersion = libs.versions.kotlin.get()
val pluginResourceProperties =
    mapOf(
        "pluginVersion" to project.version.toString(),
        "kspVersion" to kspVersion,
        "kspCoroutinesVersion" to kspCoroutinesVersion,
        "kotlinVersion" to kotlinVersion,
    )

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-common-deps:$kspVersion")
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlinTestJupiter) {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
    }
    testImplementation(libs.junitJupiterApi)
    testRuntimeOnly(libs.junitJupiterEngine)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.processResources {
    inputs.properties(pluginResourceProperties)
    filesMatching("kompact-plugin.properties") {
        expand(pluginResourceProperties)
    }
}

val gradlePluginSourcesJar =
    tasks.register<Jar>("gradlePluginSourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

val gradlePluginJavadocJar =
    tasks.register<Jar>("gradlePluginJavadocJar") {
        archiveClassifier.set("javadoc")
        from(rootProject.file("README.md"))
    }

gradlePlugin {
    plugins {
        create("kompactCodegen") {
            id = "ch.trancee.kompact.codegen"
            implementationClass = "ch.trancee.kompact.gradle.KompactGradlePlugin"
            displayName = "Kompact code generation"
            description = "Generates Kompact schema views for Kotlin Multiplatform source sets."
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifact(gradlePluginSourcesJar.get())
            artifact(gradlePluginJavadocJar.get())
            pom {
                name.set("Kompact Gradle code-generation plugin")
                description.set(
                    "Generates common and platform-specific Kotlin views for Kompact schemas.",
                )
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("kompact.repository.root", rootProject.projectDir.absolutePath)
}
