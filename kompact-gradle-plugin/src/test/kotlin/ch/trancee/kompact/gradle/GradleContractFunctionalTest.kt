package ch.trancee.kompact.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class GradleContractFunctionalTest {
    @Test
    fun restoresByteIdenticalOutputsFromRelocatedBuildCache() {
        val root = createTempDirectory("kompact-relocated-cache").toFile()
        val cache = root.resolve("cache")
        val first =
            root.resolve("first").apply {
                mkdirs()
                createTailBitFixture(this)
            }
        val second =
            root.resolve("second").apply {
                mkdirs()
                createTailBitFixture(this)
            }
        configureBuildCache(first, cache)
        configureBuildCache(second, cache)

        val firstResult =
            runner(
                    first,
                    "clean",
                    "generateKompactSchemas",
                    "--build-cache",
                    "--no-configuration-cache",
                )
                .build()
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":generateKompactSchemas")?.outcome)
        val expected = generatedFiles(first)

        val secondResult =
            runner(
                    second,
                    "clean",
                    "generateKompactSchemas",
                    "--build-cache",
                    "--no-configuration-cache",
                )
                .build()
        assertEquals(TaskOutcome.FROM_CACHE, secondResult.task(":generateKompactSchemas")?.outcome)
        assertEquals(expected, generatedFiles(second))
    }

    @Test
    fun supportsGradleIsolatedProjects() {
        val projectDirectory = createTempDirectory("kompact-isolated-projects").toFile()
        createTailBitFixture(projectDirectory)

        val result =
            runner(
                    projectDirectory,
                    "generateKompactSchemas",
                    "-Dorg.gradle.unsafe.isolated-projects=true",
                )
                .build()

        assertTrue(result.output.contains("Configuration cache entry stored"), result.output)
    }

    private fun runner(projectDirectory: File, vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDirectory)
            .withPluginClasspath()
            .withArguments(*arguments)

    private fun configureBuildCache(projectDirectory: File, cache: File) {
        projectDirectory
            .resolve("settings.gradle.kts")
            .appendText(
                "\nbuildCache { local { directory = file(\"${cache.invariantSeparatorsPath}\") } }\n"
            )
    }

    private fun generatedFiles(projectDirectory: File): Map<String, List<Byte>> {
        val root = projectDirectory.resolve("build/generated/kompact/tail_bits")
        return root.walkTopDown().filter(File::isFile).associate {
            it.relativeTo(root).invariantSeparatorsPath to it.readBytes().toList()
        }
    }
}
