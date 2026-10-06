package ch.trancee.kompact.gradle

import java.nio.file.Files
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Internal
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KompactGradlePluginProjectTest {
    @Test
    fun requiresKotlinMultiplatformPlugin() {
        val project = ProjectBuilder.builder().build()

        val failure = assertFailsWith<GradleException> { KompactGradlePlugin().apply(project) }
        val messages = mutableListOf<String>()
        var currentFailure: Throwable? = failure
        while (currentFailure != null) {
            currentFailure.message?.let(messages::add)
            currentFailure = currentFailure.cause
        }

        assertTrue(
            messages.any { it.contains("Apply the Kotlin Multiplatform plugin") },
            messages.joinToString(" <- "),
        )
    }

    @Test
    fun reportsCompatibilityWhenKotlinPropertyGetterIsMissing() {
        val plugin = KompactGradlePlugin()
        val compatibility = "Detected Kotlin Gradle Plugin 2.4.20 with KSP 2.3.12; only tested pair is compatible."

        val failure =
            assertFailsWith<GradleException> {
                plugin.readKotlinProperty(Any(), "sourceSets", compatibility)
            }

        assertTrue(failure.message.orEmpty().contains("missing getSourceSets()"), failure.message.orEmpty())
        assertTrue(failure.message.orEmpty().contains(compatibility), failure.message.orEmpty())
    }

    @Test
    fun registersSourceGenerationForKotlinMultiplatformProject() {
        val projectDirectory = Files.createTempDirectory("kompact-project-builder")
        try {
            val project =
                ProjectBuilder
                    .builder()
                    .withProjectDir(projectDirectory.toFile())
                    .build()

            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm()
            KompactGradlePlugin().apply(project)

            assertNotNull(project.tasks.findByName("generateKompactSources"))
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun registersGeneratedJvmSourcesForKotlinTarget() {
        val projectDirectory = Files.createTempDirectory("kompact-generated-jvm-sources")
        try {
            val project =
                ProjectBuilder
                    .builder()
                    .withProjectDir(projectDirectory.toFile())
                    .build()
            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            val plugin = KompactGradlePlugin()
            plugin.apply(project)

            kotlin.jvm()
            plugin.configureTargetSources(
                kotlin.targets.getByName("jvm"),
                "test",
                project.tasks.named("generateKompactSources", GenerateKompactSources::class.java),
            )

            val generatedJvmDirectory = projectDirectory.resolve("build/generated/kompact/main/jvm")
            Files.createDirectories(generatedJvmDirectory)
            val jvmGeneratedSources = kotlin.sourceSets.getByName("jvmMain").kotlin.srcDirs

            assertTrue(
                jvmGeneratedSources.any {
                    it.canonicalFile == generatedJvmDirectory.toFile().canonicalFile
                },
                "Expected the generated JVM source directory in $jvmGeneratedSources",
            )
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun resolvesGenerationInputsFromKotlinMultiplatformProject() {
        val projectDirectory = Files.createTempDirectory("kompact-project-inputs")
        try {
            val project =
                ProjectBuilder
                    .builder()
                    .withProjectDir(projectDirectory.toFile())
                    .build()

            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlin.jvm()
            val commonSource = projectDirectory.resolve("src/commonMain/kotlin")
            Files.createDirectories(commonSource)
            val commonSourceFile = commonSource.resolve("Schema.kt")
            Files.writeString(commonSourceFile, "package test\nclass Schema")
            kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(commonSource)
            project.tasks.register("compileCommonMainKotlinMetadata", TestCommonMetadataCompileTask::class.java)
            KompactGradlePlugin().apply(project)

            val task = project.tasks.getByName("generateKompactSources") as GenerateKompactSources
            val generatedRoot = projectDirectory.resolve("build/generated/kompact/main/common").toFile()
            val generatedFile = generatedRoot.resolve("example/Generated.kt")
            Files.createDirectories(generatedFile.parentFile.toPath())
            Files.writeString(generatedFile.toPath(), "package example\nclass Generated")
            val sourceRoots = task.commonSourceRoots.files
            task.commonClasspath.files

            assertTrue(sourceRoots.any { it.toPath().normalize() == commonSourceFile.normalize() })
            assertTrue(sourceRoots.none { it.toPath().normalize().startsWith(generatedRoot.toPath().normalize()) })
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun reportsInvalidMetadataLibrariesInput() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm()
        project.tasks.register("compileCommonMainKotlinMetadata", InvalidCommonMetadataCompileTask::class.java)
        KompactGradlePlugin().apply(project)

        val task = project.tasks.getByName("generateKompactSources") as GenerateKompactSources
        val failure = assertFailsWith<GradleException> { task.commonClasspath.files }

        assertTrue(failure.message.orEmpty().contains("expected common metadata compilation libraries"))
    }

    abstract class TestCommonMetadataCompileTask : DefaultTask() {
        @get:Internal
        val libraries: FileCollection
            get() = project.objects.fileCollection()
    }

    abstract class InvalidCommonMetadataCompileTask : DefaultTask() {
        @get:Internal
        val libraries: Any
            get() = "not-a-file-collection"
    }

}
