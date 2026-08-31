plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":kompact-runtime"))
            implementation(libs.kotlinx.benchmark.runtime)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

allOpen { annotation("org.openjdk.jmh.annotations.State") }

benchmark {
    targets {
        register("jvm")
        register("iosArm64")
        register("iosSimulatorArm64")
    }
    configurations {
        named("main") {
            warmups = 10
            iterations = 10
            iterationTime = 1
            iterationTimeUnit = "s"
            outputTimeUnit = "ns"
            reportFormat = "json"
        }
        register("smoke") {
            warmups = 1
            iterations = 1
            iterationTime = 100
            iterationTimeUnit = "ms"
            outputTimeUnit = "ns"
            reportFormat = "json"
        }
    }
}
