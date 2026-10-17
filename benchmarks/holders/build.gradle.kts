import kotlinx.benchmark.gradle.JvmBenchmarkTarget

plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.benchmark)
    id("ch.trancee.kompact.codegen")
}

kotlin {
    iosSimulatorArm64()
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("ch.trancee.kompact:kompact")
            implementation(libs.benchmarkRuntime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit5")) {
                exclude(group = "org.junit.jupiter")
                exclude(group = "org.junit.platform")
            }
            implementation(libs.junitJupiterApi)
            runtimeOnly(libs.junitJupiterEngine)
            runtimeOnly(libs.junitPlatformLauncher)
        }
    }
}

val benchmarkJmhVersion = libs.versions.jmh.get()
benchmark {
    targets {
        register("jvm") {
            this as JvmBenchmarkTarget
            jmhVersion = benchmarkJmhVersion
        }
    }
    configurations {
        named("main") {
            mode = "avgt"
            outputTimeUnit = "ns"
            warmups = 5
            iterations = 5
            iterationTime = 300
            iterationTimeUnit = "ms"
            advanced("jvmForks", 3)
            reportFormat = "json"
        }
        register("smoke") {
            mode = "avgt"
            outputTimeUnit = "ns"
            warmups = 1
            iterations = 1
            iterationTime = 100
            iterationTimeUnit = "ms"
            param("count", "65")
            advanced("jvmForks", 1)
            reportFormat = "json"
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.register<JavaExec>("jvmAllocationBenchmark") {
    group = "benchmark"
    description = "Measure generated-holder decoding with JMH's GC profiler."
    val benchmarkJar = tasks.named<org.gradle.jvm.tasks.Jar>("jvmBenchmarkJar")
    dependsOn(benchmarkJar)
    classpath(benchmarkJar.flatMap { it.archiveFile })
    mainClass.set("org.openjdk.jmh.Main")
    val report = layout.buildDirectory.file("reports/benchmarks/allocations.json")
    outputs.file(report)
    outputs.upToDateWhen { false }
    doFirst {
        report
            .get()
            .asFile.parentFile
            .mkdirs()
    }
    args(
        "ch.trancee.kompact.benchmark.holders.*Benchmark.*",
        "-prof",
        "gc",
        "-f",
        "3",
        "-wi",
        "5",
        "-i",
        "5",
        "-w",
        "300ms",
        "-r",
        "300ms",
        "-bm",
        "avgt",
        "-tu",
        "ns",
        "-rf",
        "json",
        "-rff",
        report.get().asFile.absolutePath,
    )
}
