package ch.trancee.kompact.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class KompactPluginFunctionalTest {
    @Test
    fun generatesKotlinCAndDescriptorFromCommonSchema() {
        val projectDirectory = createTempDirectory("kompact-functional").toFile()
        createFixture(projectDirectory)

        val result =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("compileKotlinJvm", "--stacktrace", "--no-configuration-cache")
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKompactSchemas")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinJvm")?.outcome)
        val generatedRoot = projectDirectory.resolve("build/generated/kompact/telemetry")
        assertTrue(
            generatedRoot.resolve("kotlin/com/example/VehicleTelemetry.kt").isFile,
            result.output,
        )
        assertTrue(generatedRoot.resolve("c/vehicle_telemetry_v0.h").isFile, result.output)
        assertTrue(
            generatedRoot.resolve("descriptors/vehicle_telemetry_v0.json").isFile,
            result.output,
        )
        assertTrue(
            generatedRoot.resolve("kotlin/com/example/AggregatePacket.kt").isFile,
            result.output,
        )
        assertTrue(generatedRoot.resolve("c/aggregate_packet_v0.h").isFile, result.output)
    }

    @Test
    fun failsWhenRequiredCompatibilityBaselineIsMissing() {
        val projectDirectory = createTempDirectory("kompact-baseline").toFile()
        createFixture(projectDirectory)
        projectDirectory
            .resolve("build.gradle.kts")
            .appendText("\nkompact { requireCompatibilityBaseline.set(true) }\n")

        val result =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("checkKompactSchemas", "--no-configuration-cache")
                .buildAndFail()

        assertTrue(result.output.contains("KOMPACT-KSP-1009"), result.output)
    }

    @Test
    fun writesProposalAndRemovesPublishedOutputsWhenRegistryDrifts() {
        val projectDirectory = createTempDirectory("kompact-registry-drift").toFile()
        createFixture(projectDirectory)
        val registry = projectDirectory.resolve("kompact-registry.json")
        registry.writeText(registry.readText().replace("23d2ee1e", "00000000"))

        val result =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("generateKompactSchemas", "--no-configuration-cache")
                .buildAndFail()

        val generatedRoot = projectDirectory.resolve("build/generated/kompact/telemetry")
        assertTrue(result.output.contains("KOMPACT-KSP-1006"), result.output)
        assertTrue(generatedRoot.resolve("reports/kompact-registry.proposed.json").isFile)
        assertFalse(generatedRoot.resolve("kotlin").exists())
    }

    @Test
    fun reusesConfigurationAndTaskStateWithoutSourceChanges() {
        val projectDirectory = createTempDirectory("kompact-cache").toFile()
        createFixture(projectDirectory)
        val runner =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("generateKompactSchemas", "--configuration-cache")

        runner.build()
        val second = runner.build()

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateKompactSchemas")?.outcome)
        assertTrue(second.output.contains("Reusing configuration cache"), second.output)
    }

    @Test
    fun rejectsBooleanFieldWiderThanOneBit() {
        val projectDirectory = createTempDirectory("kompact-invalid-width").toFile()
        createFixture(projectDirectory)
        val schema =
            projectDirectory.resolve("src/commonMain/kotlin/com/example/VehicleTelemetrySchema.kt")
        schema.writeText(
            schema
                .readText()
                .replace(
                    "semanticType = \"engine_malfunction\", bitOffset = 14, bitWidth = 1",
                    "semanticType = \"engine_malfunction\", bitOffset = 14, bitWidth = 2",
                )
        )

        val result =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("generateKompactSchemas", "--no-configuration-cache")
                .buildAndFail()

        assertTrue(result.output.contains("KOMPACT-KSP-1104"), result.output)
        assertFalse(projectDirectory.resolve("build/generated/kompact/telemetry/kotlin").exists())
    }

    private fun createFixture(projectDirectory: File) {
        projectDirectory
            .resolve("settings.gradle.kts")
            .writeText(
                """
                pluginManagement { repositories { google(); gradlePluginPortal(); mavenCentral() } }
                dependencyResolutionManagement { repositories { google(); mavenCentral() } }
                rootProject.name = "fixture"
                """
                    .trimIndent()
            )
        projectDirectory
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    kotlin("multiplatform") version "2.3.20"
                    id("ch.trancee.kompact")
                }

                kotlin { jvm() }

                kompact {
                    namespace.set("telemetry")
                    maxPacketBytes.set(244)
                }
                """
                    .trimIndent()
            )
        projectDirectory
            .resolve("kompact-registry.json")
            .writeText(
                """
                {
                  "${'$'}schema": "https://example.invalid/kompact-registry.schema.json",
                  "formatVersion": 1,
                  "namespace": "telemetry",
                  "maxPacketBytes": 244,
                  "schemas": [
                    {
                      "stableName": "vehicle_telemetry",
                      "id": 42,
                      "versions": [
                        {
                          "version": 0,
                          "status": "active",
                          "bodyBitSize": 16,
                          "descriptorSha256": "23d2ee1e222cc72beb4dffa5d9dbf9335aea966ef6c6a1e6beb7f1a9c746bf76"
                        }
                      ]
                    },
                    {
                      "stableName": "aggregate_packet",
                      "id": 43,
                      "versions": [
                        {
                          "version": 0,
                          "status": "active",
                          "bodyBitSize": 42,
                          "descriptorSha256": "e4fb101e204a0ddfa37acc0fb0c1150454a26c061b82db2338f25c4f86b64140"
                        }
                      ]
                    }
                  ]
                }
                """
                    .trimIndent() + "\n"
            )
        projectDirectory
            .resolve("src/commonMain/kotlin/ch/trancee/kompact/annotations/Annotations.kt")
            .apply { parentFile.mkdirs() }
            .writeText(
                """
                package ch.trancee.kompact.annotations

                @Target(AnnotationTarget.CLASS)
                annotation class KompactSchema(val registryName: String, val id: Int, val version: Int)

                @Target(AnnotationTarget.PROPERTY)
                annotation class KompactField(
                    val stableName: String,
                    val semanticType: String,
                    val bitOffset: Int,
                    val bitWidth: Int,
                    val unit: String = "",
                    val scaleNumerator: String = "1",
                    val scaleDenominator: String = "1",
                    val offsetNumerator: String = "0",
                    val offsetDenominator: String = "1",
                    val minimum: String = "",
                    val maximum: String = "",
                )

                @Target(AnnotationTarget.CLASS)
                @Repeatable
                annotation class KompactReserved(val stableName: String, val bitOffset: Int, val bitWidth: Int)

                @Target(AnnotationTarget.CLASS)
                annotation class KompactEnum(val bitWidth: Int)

                @Target(AnnotationTarget.FIELD)
                annotation class KompactCode(val stableName: String, val code: Long)

                @Target(AnnotationTarget.PROPERTY)
                annotation class KompactBytes(val count: Int)

                @Target(AnnotationTarget.PROPERTY)
                annotation class KompactArray(val count: Int)

                @Target(AnnotationTarget.PROPERTY)
                annotation class KompactOptional
                """
                    .trimIndent()
            )
        projectDirectory
            .resolve("src/commonMain/kotlin/ch/trancee/kompact/runtime/Runtime.kt")
            .apply { parentFile.mkdirs() }
            .writeText(
                """
                package ch.trancee.kompact.runtime

                sealed interface KompactDecodeResult<out T> {
                    data class Success<T>(val value: T) : KompactDecodeResult<T>
                    data class Failure(val error: KompactDecodeError) : KompactDecodeResult<Nothing>
                }

                sealed interface KompactDecodeError {
                    data class InvalidPacketLength(val expected: Int, val actual: Int) : KompactDecodeError
                    data class UnknownSchemaId(val id: UShort, val version: UByte) : KompactDecodeError
                    data class UnsupportedLayoutVersion(val id: UShort, val version: UByte) : KompactDecodeError
                    data class NonzeroReservedBits(val id: UShort, val version: UByte, val field: String, val offset: Int) : KompactDecodeError
                    data class UnknownEnumCode(val id: UShort, val version: UByte, val field: String, val offset: Int) : KompactDecodeError
                    data class NonzeroAbsentOptional(val id: UShort, val version: UByte, val field: String, val offset: Int) : KompactDecodeError
                }

                sealed interface KompactWriteError {
                    data class ValueOutOfRange(val width: Int) : KompactWriteError
                    data class IndexOutOfRange(val index: Int) : KompactWriteError
                }

                object KompactRuntime {
                    fun readBits(packet: ByteArray, bitOffset: Int, bitWidth: Int): ULong = 0uL
                    fun readBitsBoolean(packet: ByteArray, bitOffset: Int): Boolean = false
                    fun readSignedBits(packet: ByteArray, bitOffset: Int, bitWidth: Int): Long = 0L
                    fun readFloatBits(packet: ByteArray, bitOffset: Int): Float = 0f
                    fun readDoubleBits(packet: ByteArray, bitOffset: Int): Double = 0.0
                    fun writeBits(packet: ByteArray, bitOffset: Int, bitWidth: Int, value: ULong): KompactWriteError? = null
                    fun writeBitsBoolean(packet: ByteArray, bitOffset: Int, value: Boolean) {}
                    fun writeSignedBits(packet: ByteArray, bitOffset: Int, bitWidth: Int, value: Long): KompactWriteError? = null
                    fun writeFloatBits(packet: ByteArray, bitOffset: Int, value: Float) {}
                    fun writeDoubleBits(packet: ByteArray, bitOffset: Int, value: Double) {}
                }
                """
                    .trimIndent()
            )
        projectDirectory
            .resolve("src/commonMain/kotlin/com/example/VehicleTelemetrySchema.kt")
            .apply { parentFile.mkdirs() }
            .writeText(
                """
                package com.example

                import ch.trancee.kompact.annotations.KompactCode
                import ch.trancee.kompact.annotations.KompactEnum
                import ch.trancee.kompact.annotations.KompactField
                import ch.trancee.kompact.annotations.KompactReserved
                import ch.trancee.kompact.annotations.KompactSchema

                @KompactEnum(bitWidth = 4)
                enum class BatteryStatus {
                    @KompactCode(stableName = "normal", code = 0)
                    NORMAL,
                    @KompactCode(stableName = "low", code = 1)
                    LOW,
                    @KompactCode(stableName = "critical", code = 2)
                    CRITICAL,
                }

                @KompactSchema(registryName = "vehicle_telemetry", id = 42, version = 0)
                @KompactReserved(stableName = "future", bitOffset = 15, bitWidth = 1)
                interface VehicleTelemetrySchema {
                    @KompactField(stableName = "battery_status", semanticType = "battery_status", bitOffset = 0, bitWidth = 4)
                    val batteryStatus: BatteryStatus

                    @KompactField(stableName = "speed", semanticType = "vehicle_speed", bitOffset = 4, bitWidth = 10, unit = "km/h", minimum = "0", maximum = "1023")
                    val speed: UInt

                    @KompactField(stableName = "is_malfunctioning", semanticType = "engine_malfunction", bitOffset = 14, bitWidth = 1)
                    val isMalfunctioning: Boolean
                }
                """
                    .trimIndent()
            )
        projectDirectory
            .resolve("src/commonMain/kotlin/com/example/AggregatePacketSchema.kt")
            .apply { parentFile.mkdirs() }
            .writeText(
                """
                package com.example

                import ch.trancee.kompact.annotations.KompactArray
                import ch.trancee.kompact.annotations.KompactBytes
                import ch.trancee.kompact.annotations.KompactField
                import ch.trancee.kompact.annotations.KompactOptional
                import ch.trancee.kompact.annotations.KompactSchema

                @KompactSchema(registryName = "aggregate_packet", id = 43, version = 0)
                interface AggregatePacketSchema {
                    @KompactBytes(count = 2)
                    @KompactField(stableName = "payload", semanticType = "payload", bitOffset = 0, bitWidth = 16)
                    val payload: ByteArray

                    @KompactArray(count = 3)
                    @KompactField(stableName = "samples", semanticType = "sample", bitOffset = 16, bitWidth = 15)
                    val samples: UInt

                    @KompactOptional
                    @KompactField(stableName = "temperature", semanticType = "temperature", bitOffset = 31, bitWidth = 11)
                    val temperature: Int
                }
                """
                    .trimIndent()
            )
    }
}
