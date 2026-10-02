import com.google.devtools.ksp.gradle.KspAATask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":kompact"))
    ksp(project(":kompact-ksp"))
    testImplementation(kotlin("test"))
}

tasks.withType<KspAATask>().configureEach {
    if (name == "kspKotlin") {
        commandLineArgumentProviders.add(
            object : CommandLineArgumentProvider {
                override fun asArguments(): Iterable<String> = listOf("kompact.generate=jvm")
            },
        )
    }
}

val commonExpectSources =
    listOf(
        "src/main/kotlin/ch/trancee/kompact/compiletest/PayloadSchemaView.kt",
        "src/main/kotlin/ch/trancee/kompact/compiletest/PacketSchemaView.kt",
    ).joinToString(",") { layout.projectDirectory.file(it).asFile.absolutePath }

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions.freeCompilerArgs.addAll(
        "-Xmulti-platform",
        "-Xexpect-actual-classes",
        "-Xcommon-sources=$commonExpectSources",
    )
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}
