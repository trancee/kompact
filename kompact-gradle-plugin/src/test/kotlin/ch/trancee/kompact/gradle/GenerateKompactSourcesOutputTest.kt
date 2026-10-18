package ch.trancee.kompact.gradle

import java.nio.file.Files
import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateKompactSourcesOutputTest {
    @Test
    fun ignoresMissingKotlinOutputDirectory() {
        val projectDirectory = Files.createTempDirectory("kompact-missing-kotlin-output")
        try {
            val task = createUnconfiguredGenerateKompactSourcesTask(projectDirectory)
            val outputDirectory = projectDirectory.resolve("generated")

            task.copyGeneratedSources(projectDirectory.resolve("missing").toFile(), outputDirectory.toFile())

            assertFalse(Files.exists(outputDirectory), "Copying absent KSP output must not create an output directory.")
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsUnsupportedKspOutputFiles() {
        val projectDirectory = Files.createTempDirectory("kompact-unsupported-ksp-output")
        try {
            val kotlinOutput = Files.createDirectories(projectDirectory.resolve("kotlin"))
            Files.writeString(kotlinOutput.resolve("Unexpected.txt"), "not Kotlin")
            val task = createUnconfiguredGenerateKompactSourcesTask(projectDirectory)

            val failure =
                assertFailsWith<GradleException> {
                    task.copyGeneratedSources(kotlinOutput.toFile(), projectDirectory.resolve("generated").toFile())
                }

            assertTrue(failure.message.orEmpty().contains("Unexpected.txt"), failure.message.orEmpty())
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun copiesEveryKotlinFileIntoTheSingleCommonOutputPreservingPackages() {
        val projectDirectory = Files.createTempDirectory("kompact-common-generated-output")
        try {
            val kotlinOutput = Files.createDirectories(projectDirectory.resolve("kotlin/example"))
            Files.writeString(kotlinOutput.resolve("SensorFrameView.kt"), "package example")
            val outputDirectory = projectDirectory.resolve("generated")
            val task = createUnconfiguredGenerateKompactSourcesTask(projectDirectory)

            task.copyGeneratedSources(projectDirectory.resolve("kotlin").toFile(), outputDirectory.toFile())

            assertEquals(
                "package example",
                Files.readString(outputDirectory.resolve("example/SensorFrameView.kt")),
            )
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }
}
