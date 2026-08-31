package ch.trancee.kompact.gradle

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner

class GeneratedDiagnosticFunctionalTest {
    @Test
    fun reportsSchemaCyclesWithoutEnteringGeneration() {
        val projectDirectory = createTempDirectory("kompact-cycle").toFile()
        createTailBitFixture(projectDirectory)
        projectDirectory
            .resolve("src/commonMain/kotlin/example/TailPacketSchema.kt")
            .writeText(
                """
                package example

                import ch.trancee.kompact.annotations.KompactField
                import ch.trancee.kompact.annotations.KompactNested
                import ch.trancee.kompact.annotations.KompactSchema

                @KompactSchema(registryName = "tail_packet", id = 1, version = 0)
                interface TailPacketSchema {
                    @KompactNested(registryName = "child_packet", schemaId = 2, version = 0)
                    @KompactField(stableName = "child", semanticType = "child", bitOffset = 0, bitWidth = 1)
                    val child: ChildPacketSchema
                }

                @KompactSchema(registryName = "child_packet", id = 2, version = 0)
                interface ChildPacketSchema {
                    @KompactNested(registryName = "tail_packet", schemaId = 1, version = 0)
                    @KompactField(stableName = "parent", semanticType = "parent", bitOffset = 0, bitWidth = 1)
                    val parent: TailPacketSchema
                }
                """
                    .trimIndent()
            )
        val registry = projectDirectory.resolve("kompact-registry.json")
        registry.writeText(
            registry
                .readText()
                .replace(
                    """
                    |      ]
                    |    }
                    |  ]"""
                        .trimMargin(),
                    """
                    |      ]
                    |    },
                    |    {
                    |      "stableName": "child_packet",
                    |      "id": 2,
                    |      "versions": [
                    |        {
                    |          "version": 0,
                    |          "status": "active",
                    |          "bodyBitSize": 1,
                    |          "descriptorSha256": "0000000000000000000000000000000000000000000000000000000000000000"
                    |        }
                    |      ]
                    |    }
                    |  ]"""
                        .trimMargin(),
                )
        )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("generateKompactSchemas", "--no-configuration-cache")
                .buildAndFail()

        assertTrue(result.output.contains("KOMPACT-KSP-1205"), result.output)
        assertFalse(projectDirectory.resolve("build/generated/kompact/tail_bits/kotlin").exists())
    }

    @Test
    fun reportsInvalidArrayCountWithStableDiagnostic() {
        val projectDirectory = createTempDirectory("kompact-array-count").toFile()
        createTailBitFixture(projectDirectory)
        val schema = projectDirectory.resolve("src/commonMain/kotlin/example/TailPacketSchema.kt")
        schema.writeText(
            schema.readText().replace("@KompactArray(count = 2)", "@KompactArray(count = 0)")
        )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("generateKompactSchemas", "--no-configuration-cache")
                .buildAndFail()

        assertTrue(result.output.contains("KOMPACT-KSP-1201"), result.output)
    }

    @Test
    fun rejectsNoncanonicalSemanticNamesBeforeGeneration() {
        val projectDirectory = createTempDirectory("kompact-semantic-name").toFile()
        createTailBitFixture(projectDirectory)
        val schema = projectDirectory.resolve("src/commonMain/kotlin/example/TailPacketSchema.kt")
        schema.writeText(
            schema
                .readText()
                .replace("semanticType = \"optional_sample\"", "semanticType = \"Bad Name\"")
        )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("generateKompactSchemas", "--no-configuration-cache")
                .buildAndFail()

        assertTrue(result.output.contains("KOMPACT-KSP-1101"), result.output)
    }

    @Test
    fun rejectsLifecycleTransitionsThatSkipDecodeOnly() {
        val projectDirectory = createTempDirectory("kompact-lifecycle").toFile()
        createTailBitFixture(projectDirectory)
        val registry = projectDirectory.resolve("kompact-registry.json")
        projectDirectory.resolve("baseline.json").writeText(registry.readText())
        registry.writeText(
            registry.readText().replace("\"status\": \"active\"", "\"status\": \"retired\"")
        )
        projectDirectory
            .resolve("build.gradle.kts")
            .appendText(
                "\nkompact { compatibilityBaseline.set(layout.projectDirectory.file(\"baseline.json\")) }\n"
            )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("checkKompactSchemas", "--no-configuration-cache")
                .buildAndFail()

        assertTrue(result.output.contains("KOMPACT-KSP-1010"), result.output)
    }

    @Test
    fun rejectsConfiguredNamespaceThatDiffersFromRegistry() {
        val projectDirectory = createTempDirectory("kompact-namespace").toFile()
        createTailBitFixture(projectDirectory)
        val build = projectDirectory.resolve("build.gradle.kts")
        build.writeText(
            build.readText().replace("namespace.set(\"tail_bits\")", "namespace.set(\"other\")")
        )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("generateKompactSchemas", "--no-configuration-cache")
                .buildAndFail()

        assertTrue(result.output.contains("KOMPACT-KSP-1003"), result.output)
    }

    @Test
    fun removesPublishedOutputsWhenPreflightValidationFails() {
        val projectDirectory = createTempDirectory("kompact-preflight-cleanup").toFile()
        createTailBitFixture(projectDirectory)
        val runner =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("generateKompactSchemas", "--no-configuration-cache")
        runner.build()
        val generated =
            projectDirectory.resolve(
                "build/generated/kompact/tail_bits/kotlin/example/TailPacket.kt"
            )
        assertTrue(generated.isFile)
        projectDirectory
            .resolve("build.gradle.kts")
            .appendText("\nkompact { requireCompatibilityBaseline.set(true) }\n")

        val result = runner.buildAndFail()

        assertTrue(result.output.contains("KOMPACT-KSP-1009"), result.output)
        assertFalse(generated.exists())
    }

    @Test
    fun rejectsDuplicateRegistryObjectKeys() {
        val projectDirectory = createTempDirectory("kompact-duplicate-key").toFile()
        createTailBitFixture(projectDirectory)
        val registry = projectDirectory.resolve("kompact-registry.json")
        registry.writeText(
            registry
                .readText()
                .replace(
                    "\"namespace\": \"tail_bits\",",
                    "\"namespace\": \"tail_bits\",\n  \"namespace\": \"tail_bits\",",
                )
        )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("generateKompactSchemas", "--no-configuration-cache")
                .buildAndFail()

        assertTrue(result.output.contains("KOMPACT-KSP-1008"), result.output)
    }

    @Test
    fun rejectsRemovalOfSupportedDecoderSource() {
        val projectDirectory = createTempDirectory("kompact-decoder-removal").toFile()
        createTailBitFixture(projectDirectory)
        val runner =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("generateKompactSchemas", "--no-configuration-cache")
        runner.build()
        projectDirectory.resolve("src/commonMain/kotlin/example/TailPacketSchema.kt").delete()

        val result = runner.buildAndFail()

        assertTrue(result.output.contains("KOMPACT-KSP-1012"), result.output)
        assertFalse(projectDirectory.resolve("build/generated/kompact/tail_bits/kotlin").exists())
    }
}
