package ch.trancee.kompact.gradle

import java.nio.file.Files
import java.nio.file.Path
import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateKompactSourcesRoutingTest {
    @Test
    fun ignoresMissingKotlinOutputDirectory() {
        val projectDirectory = Files.createTempDirectory("kompact-missing-kotlin-output")
        try {
            val task = createUnconfiguredGenerateKompactSourcesTask(projectDirectory)
            val outputDirectory = projectDirectory.resolve("generated")

            task.routeGeneratedSources(projectDirectory.resolve("missing").toFile(), outputDirectory.toFile())

            assertFalse(Files.exists(outputDirectory), "Routing absent KSP output must not create an output directory.")
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
                    task.routeGeneratedSources(kotlinOutput.toFile(), projectDirectory.resolve("generated").toFile())
                }

            assertTrue(failure.message.orEmpty().contains("Unexpected.txt"), failure.message.orEmpty())
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsKotlinFilesWithoutARecognizedGeneratedSuffix() {
        val projectDirectory = Files.createTempDirectory("kompact-unexpected-generated-name")
        try {
            val kotlinOutput = Files.createDirectories(projectDirectory.resolve("kotlin"))
            Files.writeString(kotlinOutput.resolve("UnexpectedGenName.kt"), "package example")
            val task = createUnconfiguredGenerateKompactSourcesTask(projectDirectory)

            val failure =
                assertFailsWith<GradleException> {
                    task.routeGeneratedSources(kotlinOutput.toFile(), projectDirectory.resolve("generated").toFile())
                }

            assertTrue(failure.message.orEmpty().contains("UnexpectedGenName.kt"), failure.message.orEmpty())
        } finally {
            projectDirectory.toFile().deleteRecursively()
        }
    }
}
