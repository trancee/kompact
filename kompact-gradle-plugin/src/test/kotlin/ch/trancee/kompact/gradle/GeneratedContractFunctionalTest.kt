package ch.trancee.kompact.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner

class GeneratedContractFunctionalTest {
    @Test
    fun generatedFactoryRejectsNonzeroTransportTailBits() {
        val projectDirectory = createTempDirectory("kompact-tail-bits").toFile()
        createTailBitFixture(projectDirectory)

        val result =
            GradleRunner.create()
                .withProjectDir(projectDirectory)
                .withPluginClasspath()
                .withArguments("jvmTest", "--stacktrace", "--no-configuration-cache")
                .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        val generated =
            projectDirectory
                .resolve("build/generated/kompact/tail_bits/kotlin/example/TailPacket.kt")
                .readText()
        assertTrue(
            generated.contains(
                "const val DESCRIPTOR_SHA256: String = \"2c5471bad145ad87bd75c3ecf20ac7ddbc4169599692ae8f18e17fe926d816a3\""
            ),
            generated,
        )
        val descriptor =
            projectDirectory
                .resolve("build/generated/kompact/tail_bits/descriptors/tail_packet_v0.json")
                .readText()
        assertTrue(
            descriptor.contains("\"scale\":{\"denominator\":\"2\",\"numerator\":\"1\"}"),
            descriptor,
        )
    }

    @Test
    fun generatedCAggregatesCompileAndExecute() {
        val projectDirectory = createTempDirectory("kompact-c-aggregates").toFile()
        createTailBitFixture(projectDirectory)
        GradleRunner.create()
            .withProjectDir(projectDirectory)
            .withPluginClasspath()
            .withArguments("generateKompactSchemas", "--no-configuration-cache")
            .build()
        val probe = projectDirectory.resolve("aggregate_probe.c")
        probe.writeText(
            """
            #include "tail_packet_v0.h"

            int main(void) {
                uint8_t packet[KOMPACT_TAIL_PACKET_V0_PACKET_BYTES];
                kompact_tail_packet_v0_writer_t writer;
                kompact_tail_packet_v0_view_t view;
                uint32_t sample = 0u;
                uint32_t optional_sample = 0u;
                if (kompact_tail_packet_v0_initialize(packet, sizeof packet, &writer) != KOMPACT_STATUS_OK) return 1;
                if (kompact_tail_packet_v0_write_samples(writer, 1u, 5u) != KOMPACT_STATUS_OK) return 2;
                if (kompact_tail_packet_v0_write_temperature(writer, -3) != KOMPACT_STATUS_OK) return 3;
                view = kompact_tail_packet_v0_writer_view(writer);
                if (kompact_tail_packet_v0_samples(view, 1u, &sample) != KOMPACT_STATUS_OK || sample != 5u) return 4;
                if (!kompact_tail_packet_v0_has_temperature(view)) return 5;
                if (kompact_tail_packet_v0_temperature_or(view, 0) != -3) return 6;
                if (kompact_tail_packet_v0_clear_temperature(writer) != KOMPACT_STATUS_OK) return 7;
                if (kompact_tail_packet_v0_has_temperature(view)) return 8;
                if (kompact_tail_packet_v0_has_optional_samples(view)) return 9;
                if (kompact_tail_packet_v0_write_optional_samples(writer, 0u, 6u) != KOMPACT_STATUS_OK) return 10;
                if (!kompact_tail_packet_v0_has_optional_samples(view)) return 11;
                if (kompact_tail_packet_v0_optional_samples_or(view, 0u, 0u, &optional_sample) != KOMPACT_STATUS_OK || optional_sample != 6u) return 12;
                if (kompact_tail_packet_v0_clear_optional_samples(writer) != KOMPACT_STATUS_OK) return 13;
                if (kompact_tail_packet_v0_has_optional_samples(view)) return 14;
                return 0;
            }
            """
                .trimIndent()
        )
        val generatedHeaders =
            projectDirectory.resolve("build/generated/kompact/tail_bits/c").absolutePath
        runCommand(
            projectDirectory,
            "cc",
            "-std=c99",
            "-Wall",
            "-Wextra",
            "-Werror",
            "-pedantic-errors",
            "-I",
            generatedHeaders,
            probe.absolutePath,
            "-o",
            "aggregate_probe",
        )
        runCommand(projectDirectory, projectDirectory.resolve("aggregate_probe").absolutePath)
    }
}

internal fun createTailBitFixture(projectDirectory: File) {
    projectDirectory
        .resolve("settings.gradle.kts")
        .writeText(
            """
            pluginManagement { repositories { google(); gradlePluginPortal(); mavenCentral() } }
            dependencyResolutionManagement { repositories { google(); mavenCentral() } }
            rootProject.name = "tail-bit-fixture"
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

            kotlin {
                jvm()
                sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
            }

            kompact {
                namespace.set("tail_bits")
                maxPacketBytes.set(8)
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
              "namespace": "tail_bits",
              "maxPacketBytes": 8,
              "schemas": [
                {
                  "stableName": "tail_packet",
                  "id": 1,
                  "versions": [
                    {
                      "version": 0,
                      "status": "active",
                      "bodyBitSize": 19,
                      "descriptorSha256": "2c5471bad145ad87bd75c3ecf20ac7ddbc4169599692ae8f18e17fe926d816a3"
                    }
                  ]
                }
              ]
            }
            """
                .trimIndent() + "\n"
        )
    projectDirectory
        .resolve("src/commonMain/kotlin/example/TailPacketSchema.kt")
        .apply { parentFile.mkdirs() }
        .writeText(
            """
            package example

            import ch.trancee.kompact.annotations.KompactArray
            import ch.trancee.kompact.annotations.KompactCode
            import ch.trancee.kompact.annotations.KompactEnum
            import ch.trancee.kompact.annotations.KompactField
            import ch.trancee.kompact.annotations.KompactReserved
            import ch.trancee.kompact.annotations.KompactOptional
            import ch.trancee.kompact.annotations.KompactSchema

            @KompactEnum(bitWidth = 1)
            enum class Mode {
                @KompactCode(stableName = "normal", code = 0)
                NORMAL,
            }

            @KompactSchema(registryName = "tail_packet", id = 1, version = 0)
            @KompactReserved(stableName = "future", bitOffset = 1, bitWidth = 1)
            interface TailPacketSchema {
                @KompactField(stableName = "mode", semanticType = "mode", bitOffset = 0, bitWidth = 1)
                val mode: Mode

                @KompactArray(count = 2)
                @KompactField(stableName = "samples", semanticType = "sample", bitOffset = 2, bitWidth = 6)
                val samples: UInt

                @KompactOptional
                @KompactField(stableName = "temperature", semanticType = "temperature", bitOffset = 8, bitWidth = 4)
                val temperature: Int

                @KompactOptional
                @KompactArray(count = 2)
                @KompactField(stableName = "optional_samples", semanticType = "optional_sample", bitOffset = 12, bitWidth = 7, scaleNumerator = "2", scaleDenominator = "4")
                val optionalSamples: UInt
            }
            """
                .trimIndent()
        )
    projectDirectory
        .resolve("src/commonMain/kotlin/ch/trancee/kompact/annotations/Annotations.kt")
        .apply { parentFile.mkdirs() }
        .writeText(
            """
            package ch.trancee.kompact.annotations

            @Target(AnnotationTarget.CLASS)
            annotation class KompactSchema(val registryName: String, val id: Int, val version: Int)

            @Target(AnnotationTarget.CLASS)
            annotation class KompactEnum(val bitWidth: Int)

            @Target(AnnotationTarget.FIELD)
            annotation class KompactCode(val stableName: String, val code: Long)

            @Repeatable
            @Target(AnnotationTarget.CLASS)
            annotation class KompactReserved(val stableName: String, val bitOffset: Int, val bitWidth: Int)

            @Target(AnnotationTarget.PROPERTY)
            annotation class KompactArray(val count: Int)

            @Target(AnnotationTarget.PROPERTY)
            annotation class KompactOptional

            @Target(AnnotationTarget.PROPERTY)
            annotation class KompactNested(val registryName: String, val schemaId: Int, val version: Int)

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
                fun readBits(packet: ByteArray, bitOffset: Int, bitWidth: Int): ULong {
                    var value = 0uL
                    repeat(bitWidth) { valueBit ->
                        val packetBit = bitOffset + valueBit
                        val bit = (packet[packetBit / 8].toInt() ushr (packetBit % 8)) and 1
                        value = value or (bit.toULong() shl valueBit)
                    }
                    return value
                }

                fun readBitsBoolean(packet: ByteArray, bitOffset: Int): Boolean =
                    readBits(packet, bitOffset, 1) != 0uL

                fun readSignedBits(packet: ByteArray, bitOffset: Int, bitWidth: Int): Long {
                    val value = readBits(packet, bitOffset, bitWidth)
                    val sign = 1uL shl (bitWidth - 1)
                    return if (value and sign == 0uL) value.toLong()
                    else (value or (ULong.MAX_VALUE shl bitWidth)).toLong()
                }

                fun writeBits(packet: ByteArray, bitOffset: Int, bitWidth: Int, value: ULong): KompactWriteError? {
                    repeat(bitWidth) { valueBit ->
                        val packetBit = bitOffset + valueBit
                        val byteIndex = packetBit / 8
                        val mask = 1 shl (packetBit % 8)
                        val old = packet[byteIndex].toInt() and 0xff
                        packet[byteIndex] = if (((value shr valueBit) and 1uL) == 0uL) {
                            (old and mask.inv()).toByte()
                        } else {
                            (old or mask).toByte()
                        }
                    }
                    return null
                }

                fun writeSignedBits(packet: ByteArray, bitOffset: Int, bitWidth: Int, value: Long): KompactWriteError? {
                    val limit = 1L shl (bitWidth - 1)
                    if (value < -limit || value >= limit) return KompactWriteError.ValueOutOfRange(bitWidth)
                    return writeBits(packet, bitOffset, bitWidth, value.toULong() and ((1uL shl bitWidth) - 1uL))
                }

                fun writeBitsBoolean(packet: ByteArray, bitOffset: Int, value: Boolean) {
                    writeBits(packet, bitOffset, 1, if (value) 1uL else 0uL)
                }
            }
            """
                .trimIndent()
        )
    projectDirectory
        .resolve("src/commonTest/kotlin/example/TailPacketContractTest.kt")
        .apply { parentFile.mkdirs() }
        .writeText(
            """
            package example

            import ch.trancee.kompact.runtime.KompactDecodeError
            import ch.trancee.kompact.runtime.KompactDecodeResult
            import kotlin.test.assertEquals
            import kotlin.test.assertFalse
            import kotlin.test.assertNull
            import kotlin.test.assertTrue
            import kotlin.test.Test
            import kotlin.test.assertIs

            class TailPacketContractTest {
                @Test
                fun rejectsNonzeroTransportTailBits() {
                    val packet = ByteArray(TailPacket.PACKET_BYTE_SIZE)
                    assertIs<KompactDecodeResult.Success<TailPacketWriter>>(TailPacket.initialize(packet))
                    packet[packet.lastIndex] = 0x80.toByte()

                    val failure = assertIs<KompactDecodeResult.Failure>(TailPacket.wrap(packet))
                    assertIs<KompactDecodeError.NonzeroTailBits>(failure.error)
                }

                @Test
                fun reportsTheLowestBodyBitFailureFirst() {
                    val packet = ByteArray(TailPacket.PACKET_BYTE_SIZE)
                    assertIs<KompactDecodeResult.Success<TailPacketWriter>>(TailPacket.initialize(packet))
                    packet[2] = 0x03

                    val failure = assertIs<KompactDecodeResult.Failure>(TailPacket.wrap(packet))
                    assertIs<KompactDecodeError.UnknownEnumCode>(failure.error)
                }

                @Test
                fun readsAndWritesOptionalArraysWithoutAllocationBackedSlices() {
                    val packet = ByteArray(TailPacket.PACKET_BYTE_SIZE)
                    val writer =
                        assertIs<KompactDecodeResult.Success<TailPacketWriter>>(
                            TailPacket.initialize(packet)
                        ).value
                    assertFalse(writer.view().hasOptionalSamples)
                    assertNull(writer.writeOptionalSamples(1, 5u))
                    assertTrue(writer.view().hasOptionalSamples)
                    assertEquals(5u, writer.view().optionalSamplesOr(1, 0u))
                    writer.clearOptionalSamples()
                    assertFalse(writer.view().hasOptionalSamples)
                }
            }
            """
                .trimIndent()
        )
}

private fun runCommand(workingDirectory: File, vararg command: String) {
    val process =
        ProcessBuilder(*command).directory(workingDirectory).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    assertTrue(process.waitFor() == 0, "${command.joinToString(" ")} failed:\n$output")
}
