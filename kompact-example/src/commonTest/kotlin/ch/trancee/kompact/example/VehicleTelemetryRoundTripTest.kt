package ch.trancee.kompact.example

import ch.trancee.kompact.runtime.KompactRuntime
import ch.trancee.kompact.writer.KompactWriter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec: .scratch/kompact-spec/issues/07-write-builder-interface.md
 *        .scratch/kompact-spec/issues/10-cross-platform-testing-model.md
 *
 * Round-trip integration test: write a VehicleTelemetrySchema into
 * a KompactWriter, build the bytes, then read them through the
 * generated value-class view (Ticket 02 KSP processor + Ticket 07
 * writer + Ticket 03 zero-alloc read).
 */
class VehicleTelemetryRoundTripTest {

    @Test
    fun write_then_read_via_generated_view() {
        val w = KompactWriter()
        w.writeUInt4(0xC)        // batteryStatus = 12
        w.writeUInt10(677)        // speed = 677
        w.writeBool(true)         // isMalfunctioning = true
        val bytes = w.build()

        val view = VehicleTelemetrySchemaView(bytes)
        assertEquals(0xC, view.batteryStatus)
        assertEquals(677, view.speed)
        assertEquals(true, view.isMalfunctioning)
    }

    @Test
    fun readBits_hot_path_via_generated_view() {
        // Hand-built 2-byte buffer: 4 bits 0xC | 10 bits 0x2A5 | 1 bit true.
        // byte 0 = 0x5C (low nibble 0xC, high nibble 0x5 — low 4 bits of 0x2A5)
        // byte 1 = 0x6A (high 6 bits of 0x2A5 in bits 0..5, bit 6 = 1 = malfunctioning)
        val bytes = byteArrayOf(0x5C, 0x6A)
        val view = VehicleTelemetrySchemaView(bytes)
        assertEquals(0xC, view.batteryStatus)
        assertEquals(0x2A5, view.speed)
        assertEquals(true, view.isMalfunctioning)
    }

    @Test
    fun raw_readBits_matches_generated_view() {
        val bytes = byteArrayOf(0x5C, 0x6A)
        val directBatt = KompactRuntime.readBits(bytes, bitOffset = 0, bitWidth = 4)
        val directSpeed = KompactRuntime.readBits(bytes, bitOffset = 4, bitWidth = 10)
        val directMal = KompactRuntime.readBitsBoolean(bytes, bitOffset = 14)
        val view = VehicleTelemetrySchemaView(bytes)
        assertEquals(directBatt, view.batteryStatus)
        assertEquals(directSpeed, view.speed)
        assertEquals(directMal, view.isMalfunctioning)
    }
}
