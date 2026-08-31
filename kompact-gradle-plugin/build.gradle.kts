plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    `java-gradle-plugin`
    `maven-publish`
}

kotlin {
    explicitApi()
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { enabled.set(true) }
    jvmToolchain(17)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    basePath = rootProject.projectDir.absolutePath
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(gradleKotlinDsl())
    implementation(libs.ksp.api)
    implementation(libs.ksp.common.deps)
    implementation(project(":kompact-processor"))
    implementation(libs.ksp.embeddable)
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.1")
}

gradlePlugin {
    plugins {
        create("kompact") {
            id = "ch.trancee.kompact"
            implementationClass = "ch.trancee.kompact.gradle.KompactPlugin"
            displayName = "Kompact schema generator"
            description = "Generates bit-packed Kotlin and C99 schema interfaces"
        }
    }
}

tasks.processResources {
    from(
        project(":kompact-annotations")
            .file("src/commonMain/kotlin/ch/trancee/kompact/annotations/KompactAnnotations.kt")
    ) {
        into("kompact-ksp-stubs")
    }
    from(rootProject.file("schemas")) { into("schemas") }
}

tasks.jar { manifest.attributes["Implementation-Version"] = project.version }

tasks.test { useJUnitPlatform() }
