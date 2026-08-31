package ch.trancee.kompact.gradle

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner

class LifecycleGenerationFunctionalTest {
    @Test
    fun decodeOnlySchemasExposeDecodersWithoutWriters() {
        val projectDirectory = createTempDirectory("kompact-decode-only").toFile()
        createTailBitFixture(projectDirectory)
        val registry = projectDirectory.resolve("kompact-registry.json")
        registry.writeText(
            registry.readText().replace("\"status\": \"active\"", "\"status\": \"decode-only\"")
        )

        GradleRunner.create()
            .withProjectDir(projectDirectory)
            .withPluginClasspath()
            .withArguments("generateKompactSchemas", "--no-configuration-cache")
            .build()

        val generatedRoot = projectDirectory.resolve("build/generated/kompact/tail_bits")
        val kotlin = generatedRoot.resolve("kotlin/example/TailPacket.kt").readText()
        assertTrue(kotlin.contains("fun wrap("), kotlin)
        assertTrue(kotlin.contains("value class TailPacketView"), kotlin)
        assertFalse(kotlin.contains("TailPacketWriter"), kotlin)
        assertFalse(kotlin.contains("fun initialize("), kotlin)
        assertFalse(kotlin.contains("fun write"), kotlin)

        val c = generatedRoot.resolve("c/tail_packet_v0.h").readText()
        assertTrue(c.contains("kompact_tail_packet_v0_wrap"), c)
        assertTrue(c.contains("kompact_tail_packet_v0_view_t"), c)
        assertFalse(c.contains("kompact_tail_packet_v0_writer_t"), c)
        assertFalse(c.contains("kompact_tail_packet_v0_initialize"), c)
        assertFalse(c.contains("kompact_tail_packet_v0_write_"), c)
    }
}
