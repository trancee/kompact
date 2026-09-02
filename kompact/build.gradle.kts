plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.binaryCompatibilityValidator)
    alias(libs.plugins.vanniktechMavenPublish)
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            compilerOptions {
                // KT-61573: expect/actual value classes are stable in 2.4; silence the Beta warning.
                freeCompilerArgs.addAll("-Xexpect-actual-classes")
            }
        }
        val commonTest by getting {
            dependencies {
                // kotlin("test") is version-aligned to the Kotlin Gradle plugin (catalog'd).
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting
        // Shared iOS source set (Ticket 03 expect/actual value class).
        // gradle.properties: kotlin.mpp.applyDefaultHierarchyTemplate=false so this
        // intermediate is the sole iosMain (avoids the default-template conflict).
        val iosMain by creating
        iosMain { dependsOn(commonMain) }
        iosArm64Main { dependsOn(iosMain) }
        iosSimulatorArm64Main { dependsOn(iosMain) }
    }
}

// Ticket 13: BCV 0.18.0 — lock the public ABI for common + each Kotlin/Native target.
apiValidation {
    klib {
        enabled = true
    }
}

// Ticket 13: vanniktech maven-publish 0.37.0 — central publishing gates.
mavenPublishing {
    coordinates("ch.trancee.kompact", "kompact", "0.1.0-SNAPSHOT")
    pom {
        name.set("Kompact")
        description.set("Zero-allocation bit-stream pack/unpack primitives and generated model views for Kotlin Multiplatform.")
        url.set("https://github.com/trancee/kompact")
        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("trancee")
                name.set("Philipp Grosswiler")
                email.set("philipp.grosswiler@gmail.com")
            }
        }
        scm {
            url.set("https://github.com/trancee/kompact")
            connection.set("scm:git:git://github.com/trancee/kompact.git")
            developerConnection.set("scm:git:ssh://git@github.com/trancee/kompact.git")
        }
    }
}
