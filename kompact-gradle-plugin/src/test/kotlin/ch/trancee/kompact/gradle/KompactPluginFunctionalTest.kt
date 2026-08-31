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
        assertEquals(
            File("../conformance/c/vehicle_telemetry_v0.h").readText(),
            generatedRoot.resolve("c/vehicle_telemetry_v0.h").readText(),
        )
        assertEquals(
            File("../conformance/c/kompact_runtime.h").readText(),
            generatedRoot.resolve("c/kompact_runtime.h").readText(),
        )
        assertTrue(
            generatedRoot.resolve("descriptors/vehicle_telemetry_v0.json").isFile,
            result.output,
        )
        assertTrue(
            generatedRoot.resolve("kotlin/com/example/AggregatePacket.kt").isFile,
            result.output,
        )
        assertTrue(generatedRoot.resolve("c/aggregate_packet_v0.h").isFile, result.output)
        assertTrue(
            generatedRoot.resolve("kotlin/com/example/LocationPacket.kt").isFile,
            result.output,
        )
        assertTrue(generatedRoot.resolve("c/location_packet_v0.h").isFile, result.output)
        val nestedKotlin = generatedRoot.resolve("kotlin/com/example/LocationPacket.kt").readText()
        assertTrue(nestedKotlin.contains("fun pointsX(index: Int): kotlin.Int"), nestedKotlin)
        assertTrue(
            nestedKotlin.contains("fun writePointsY(index: Int, value: kotlin.Int)"),
            nestedKotlin,
        )
        assertTrue(nestedKotlin.contains("readBits(packet, 88, 2)"), nestedKotlin)
        assertTrue(nestedKotlin.contains("readBits(packet, 146, 2)"), nestedKotlin)
        assertTrue(
            nestedKotlin.contains(
                "fun pointsTelemetryBatteryStatus(index: Int): com.example.BatteryStatus"
            ),
            nestedKotlin,
        )
        assertTrue(
            nestedKotlin.contains("fun pointsSamples(index: Int, index1: Int)"),
            nestedKotlin,
        )
        assertTrue(
            nestedKotlin.contains("fun pointsPayload(index: Int, index1: Int)"),
            nestedKotlin,
        )
        assertTrue(nestedKotlin.contains("fun hasPointsTemperature(index: Int)"), nestedKotlin)
        assertTrue(
            nestedKotlin.contains("fun pointsTemperatureOr(index: Int, defaultValue:"),
            nestedKotlin,
        )
        val nestedC = generatedRoot.resolve("c/location_packet_v0.h").readText()
        assertTrue(nestedC.contains("kompact_location_packet_v0_points_x"), nestedC)
        assertTrue(nestedC.contains("kompact_location_packet_v0_write_points_y"), nestedC)
        assertTrue(nestedC.contains("packet, 88u, 2u"), nestedC)
        assertTrue(nestedC.contains("packet, 146u, 2u"), nestedC)
        assertTrue(nestedC.contains("kompact_location_packet_v0_points_telemetry_speed"), nestedC)
        assertTrue(nestedC.contains("kompact_location_packet_v0_points_samples"), nestedC)
        assertTrue(nestedC.contains("size_t index1"), nestedC)
        assertTrue(nestedC.contains("kompact_location_packet_v0_has_points_temperature"), nestedC)
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
                    },
                    {
                      "stableName": "coordinates",
                      "id": 44,
                      "versions": [
                        {
                          "version": 0,
                          "status": "active",
                          "bodyBitSize": 58,
                          "descriptorSha256": "c0403d026d9bef4057e8d640466ad50b46ea67fd2fffd29910377d2ead4782ef"
                        }
                      ]
                    },
                    {
                      "stableName": "location_packet",
                      "id": 45,
                      "versions": [
                        {
                          "version": 0,
                          "status": "active",
                          "bodyBitSize": 174,
                          "descriptorSha256": "e8b261b5c2c7b86a715c50d02b47c49d80dab24538257ee74299d04bea9de574"
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

                @Target(AnnotationTarget.PROPERTY)
                annotation class KompactNested(val registryName: String, val schemaId: Int, val version: Int)
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
                    data class ReservedSchemaId(val version: UByte) : KompactDecodeError
                    data class UnknownSchemaId(val id: UShort, val version: UByte) : KompactDecodeError
                    data class UnsupportedLayoutVersion(val id: UShort, val version: UByte) : KompactDecodeError
                    data class NonzeroTailBits(val id: UShort, val version: UByte, val offset: Int) : KompactDecodeError
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
        projectDirectory
            .resolve("src/commonMain/kotlin/com/example/LocationPacketSchema.kt")
            .apply { parentFile.mkdirs() }
            .writeText(
                """
                package com.example

                import ch.trancee.kompact.annotations.KompactArray
                import ch.trancee.kompact.annotations.KompactBytes
                import ch.trancee.kompact.annotations.KompactField
                import ch.trancee.kompact.annotations.KompactNested
                import ch.trancee.kompact.annotations.KompactOptional
                import ch.trancee.kompact.annotations.KompactReserved
                import ch.trancee.kompact.annotations.KompactSchema

                @KompactSchema(registryName = "coordinates", id = 44, version = 0)
                @KompactReserved(stableName = "future", bitOffset = 14, bitWidth = 2)
                interface CoordinatesSchema {
                    @KompactField(stableName = "x", semanticType = "coordinate_x", bitOffset = 0, bitWidth = 7)
                    val x: Int

                    @KompactField(stableName = "y", semanticType = "coordinate_y", bitOffset = 7, bitWidth = 7)
                    val y: Int

                    @KompactNested(registryName = "vehicle_telemetry", schemaId = 42, version = 0)
                    @KompactField(stableName = "telemetry", semanticType = "vehicle_telemetry", bitOffset = 16, bitWidth = 16)
                    val telemetry: VehicleTelemetrySchema

                    @KompactArray(count = 2)
                    @KompactField(stableName = "samples", semanticType = "sample", bitOffset = 32, bitWidth = 10)
                    val samples: UInt

                    @KompactBytes(count = 1)
                    @KompactField(stableName = "payload", semanticType = "payload", bitOffset = 42, bitWidth = 8)
                    val payload: ByteArray

                    @KompactOptional
                    @KompactField(stableName = "temperature", semanticType = "temperature", bitOffset = 50, bitWidth = 8)
                    val temperature: Int
                }

                @KompactSchema(registryName = "location_packet", id = 45, version = 0)
                interface LocationPacketSchema {
                    @KompactNested(registryName = "coordinates", schemaId = 44, version = 0)
                    @KompactField(stableName = "coordinates", semanticType = "coordinates", bitOffset = 0, bitWidth = 58)
                    val coordinates: CoordinatesSchema

                    @KompactArray(count = 2)
                    @KompactNested(registryName = "coordinates", schemaId = 44, version = 0)
                    @KompactField(stableName = "points", semanticType = "points", bitOffset = 58, bitWidth = 116)
                    val points: CoordinatesSchema
                }
                """
                    .trimIndent()
            )
    }
}
