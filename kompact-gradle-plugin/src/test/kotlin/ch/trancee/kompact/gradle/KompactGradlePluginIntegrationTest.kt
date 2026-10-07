package ch.trancee.kompact.gradle

import java.nio.file.Files
import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KompactGradlePluginIntegrationTest {
    @Test
    fun removesGeneratedSourcesWhenLastCommonSourceIsRemoved() {
        val projectDir = Files.createTempDirectory("kompact-empty-kmp-consumer")
        try {
            copyFixture(projectDir)
            val generatedFile =
                projectDir.resolve(
                    "build/generated/kompact/main/common/example/PacketSchemaViewGen.kt",
                )

            val initialRun =
                gradle(projectDir, ":generateKompactSources").build()
            assertTrue(
                initialRun.task(":generateKompactSources")?.outcome in
                    setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE),
            )
            assertTrue(Files.exists(generatedFile), "Expected the initial run to generate the framed view.")

            Files.delete(projectDir.resolve("src/commonMain/kotlin/example/Schema.kt"))
            gradle(projectDir, ":generateKompactSources").build()

            assertFalse(Files.exists(generatedFile), "Generated views must be removed after the last schema is deleted.")
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun restoresGeneratedSourcesFromBuildCacheForRelocatedConsumer() {
        val originalProjectDir = Files.createTempDirectory("kompact-kmp-consumer")
        val relocatedProjectDir = Files.createTempDirectory("kompact-relocated-kmp-consumer")
        try {
            copyFixture(originalProjectDir)
            copyFixture(relocatedProjectDir)
            val originalRun =
                gradle(originalProjectDir, ":generateKompactSources", "--build-cache")
                    .build()
            assertTrue(
                originalRun.task(":generateKompactSources")?.outcome in
                    setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE),
                "Expected the original consumer to generate sources or restore them from cache.",
            )
            val expected = generatedSources(originalProjectDir)
            assertTrue(expected.isNotEmpty(), "Expected the original consumer to generate sources.")

            val relocatedRun =
                gradle(relocatedProjectDir, ":generateKompactSources", "--build-cache")
                    .build()
            assertEquals(
                TaskOutcome.FROM_CACHE,
                relocatedRun.task(":generateKompactSources")?.outcome,
                "Expected a cache hit after moving the consumer project.",
            )
            val actual = generatedSources(relocatedProjectDir)
            assertEquals(expected.keys, actual.keys, "The cache restored a different generated file set.")
            expected.forEach { (path, bytes) ->
                assertContentEquals(bytes, actual.getValue(path), "Cached output differs for $path.")
            }
        } finally {
            originalProjectDir.toFile().deleteRecursively()
            relocatedProjectDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun generatesCommonAndPlatformSourcesForKmpConsumer() {
        val projectDir = Files.createTempDirectory("kompact-kmp-consumer")
        try {
            copyFixture(projectDir)

            val generationRun =
                gradle(projectDir, ":generateKompactSources", "--build-cache", "--configuration-cache")
                    .build()

            assertTrue(
                generationRun.task(":generateKompactSources")?.outcome in
                    setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE),
                "Expected the generator to execute or restore its output from cache.",
            )
            assertGeneratedSource(projectDir, "common", "PacketSchemaViewGen.kt")
            assertGeneratedSource(projectDir, "common", "ScalarSchemaGenEncoder.kt")
            assertGeneratedSource(projectDir, "jvm", "PacketSchemaViewGenJvm.kt")
            assertGeneratedSource(projectDir, "jvm", "ScalarSchemaGenJvm.kt")
            assertGeneratedSource(projectDir, "ios", "PacketSchemaViewGenIos.kt")
            assertGeneratedSource(projectDir, "ios", "ScalarSchemaGenIos.kt")
            assertGeneratedSource(projectDir, "androidArm64", "PacketSchemaViewGenAndroidArm64.kt")
            assertGeneratedSource(projectDir, "androidArm64", "ScalarSchemaGenAndroidArm64.kt")

            val firstRun =
                gradle(
                    projectDir,
                    ":jvmTest",
                    ":compileAndroidMain",
                    ":compileKotlinIosArm64",
                    ":compileKotlinIosSimulatorArm64",
                    ":compileKotlinAndroidNativeArm64",
                    "--build-cache",
                    "--configuration-cache",
                    // Keep the multi-target nested build within the CI runner's memory budget.
                    "--max-workers=1",
                ).build()

            assertEquals(TaskOutcome.UP_TO_DATE, firstRun.task(":generateKompactSources")?.outcome)

            val secondRun =
                gradle(projectDir, ":generateKompactSources", "--build-cache", "--configuration-cache")
                    .build()

            assertEquals(TaskOutcome.UP_TO_DATE, secondRun.task(":generateKompactSources")?.outcome)

            val thirdRun =
                gradle(projectDir, ":generateKompactSources", "--build-cache", "--configuration-cache")
                    .build()
            val configurationCacheMessages =
                thirdRun.output.lineSequence()
                    .filter { it.contains("configuration cache", ignoreCase = true) }
                    .joinToString(" | ")
            assertTrue(
                configurationCacheMessages.contains("entry reused", ignoreCase = true),
                "Expected the repeated generation invocation to reuse the configuration cache; " +
                    "observed: $configurationCacheMessages",
            )
            assertEquals(TaskOutcome.UP_TO_DATE, thirdRun.task(":generateKompactSources")?.outcome)
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsUntestedKotlinGradlePluginVersionBeforeGeneration() {
        val projectDir = Files.createTempDirectory("kompact-unsupported-kotlin-consumer")
        try {
            copyFixture(projectDir)
            val buildFile = projectDir.resolve("build.gradle.kts")
            Files.writeString(
                buildFile,
                Files.readString(buildFile).replace(
                    "kotlin(\"multiplatform\") version \"2.4.20\"",
                    "kotlin(\"multiplatform\") version \"2.4.10\"",
                ),
            )

            val result = gradle(projectDir, ":generateKompactSources").buildAndFail()

            assertTrue(result.output.contains("Kotlin Gradle Plugin 2.4.10"))
            assertTrue(result.output.contains("KSP 2.3.12"))
            assertTrue(result.output.contains("only tested pair"))
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun reportsTestedPairWhenKsp2EntryPointIsMissing() {
        val projectDir = Files.createTempDirectory("kompact-missing-ksp2-entrypoint")
        try {
            copyFixture(projectDir)
            val initScript = projectDir.resolve("remove-ksp2-engine.init.gradle")
            Files.writeString(
                initScript,
                """
                gradle.projectsEvaluated {
                    rootProject.allprojects.each { project ->
                        def generation = project.tasks.findByName("generateKompactSources")
                        if (generation != null) {
                            def incompleteEngine = project.configurations.detachedConfiguration(
                                project.dependencies.create("com.google.devtools.ksp:symbol-processing-api:2.3.12"),
                                project.dependencies.create("com.google.devtools.ksp:symbol-processing-common-deps:2.3.12"),
                                project.dependencies.create("org.jetbrains.kotlin:kotlin-stdlib:2.4.20"),
                                project.dependencies.create("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
                            )
                            incompleteEngine.transitive = false
                            generation.kspClasspath.setFrom(incompleteEngine)
                        }
                    }
                }
                """.trimIndent(),
            )

            val result =
                gradle(
                    projectDir,
                    "--init-script",
                    initScript.toString(),
                    ":generateKompactSources",
                ).buildAndFail()

            assertTrue(result.output.contains("Kotlin Gradle Plugin 2.4.20"), result.output)
            assertTrue(result.output.contains("KSP 2.3.12"), result.output)
            assertTrue(result.output.contains("KSPLoader.loadAndRunKSP"), result.output)
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    private fun assertGeneratedSource(
        projectDir: Path,
        sourceSet: String,
        fileName: String,
    ) {
        val outputDirectory = projectDir.resolve("build/generated/kompact/main").toFile()
        val generatedFile = projectDir.resolve("build/generated/kompact/main/$sourceSet/example/$fileName")
        val generatedFiles =
            outputDirectory
                .walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(outputDirectory).invariantSeparatorsPath }
                .toList()
        assertTrue(
            Files.exists(generatedFile),
            "Expected $sourceSet/example/$fileName; found $generatedFiles",
        )
    }

    private fun generatedSources(projectDir: Path): Map<String, ByteArray> {
        val outputDirectory = projectDir.resolve("build/generated/kompact/main").toFile()
        return outputDirectory
            .walkTopDown()
            .filter { it.isFile }
            .associate { it.relativeTo(outputDirectory).invariantSeparatorsPath to it.readBytes() }
    }

    private fun copyFixture(projectDir: Path) {
        val fixtureUrl = requireNotNull(javaClass.getResource("/kmp-consumer"))
        val fixtureDir = Path.of(fixtureUrl.toURI())
        fixtureDir.toFile().copyRecursively(projectDir.toFile(), overwrite = true)

        val repositoryRoot = requireNotNull(System.getProperty("kompact.repository.root"))
        val settingsFile = projectDir.resolve("settings.gradle.kts")
        Files.writeString(
            settingsFile,
            Files
                .readString(settingsFile)
                .replace("@KOMPACT_ROOT@", repositoryRoot.replace("\\", "\\\\")),
        )
    }

    private fun gradle(
        projectDir: Path,
        vararg arguments: String,
    ): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(listOf("--stacktrace") + arguments)
}
