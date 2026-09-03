package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Spec: .scratch/kompact-spec/issues/01-wire-format-bit-order.md
 *
 * Decision: LSB-first (little-endian) bit packing.
 * Byte 0 = field bits 0..7 (LSB of value first).
 * Multi-bit ints crossing byte boundaries: low bits of byte N hold low bits of value.
 * Byte operations must mask with `and 0xFF` for identical JVM / Kotlin/Native behavior.
 */
class KompactRuntimeTest {

    // --- readBits (Ticket 01) ---

    @Test
    fun readBits_aligned_8bits_at_offset_0() {
        val buf = byteArrayOf(0xA5.toByte(), 0x00)
        assertEquals(0xA5, KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 8))
    }

    @Test
    fun readBits_aligned_8bits_at_offset_8() {
        val buf = byteArrayOf(0x00, 0x3C.toByte())
        assertEquals(0x3C, KompactRuntime.readBits(buf, bitOffset = 8, bitWidth = 8))
    }

    /**
     * The 10-bit VehicleTelemetry example: bits 0..3 in byte 0 (low nibble of
     * the enum) + bits 4..9 of the speed (low 2 bits of byte 1). Speed value
     * 0x2A5 (= 677) at bitOffset 4: byte 0 = 0x50 (low nibble 0), byte 1 low
     * 6 bits = 0x2A. So buf = [0x50, 0x2A, 0..], readBits(buf, 4, 10) == 677.
     */
    @Test
    fun readBits_cross_byte_10bits_at_offset_4() {
        val buf = byteArrayOf(0x50, 0x2A)
        assertEquals(677, KompactRuntime.readBits(buf, bitOffset = 4, bitWidth = 10))
    }

    @Test
    fun readBits_cross_byte_3bits_at_offset_5() {
        // value 5 (101) starts at bit 5, fits entirely in byte 0 bits 5..7.
        val buf = byteArrayOf((5 shl 5).toByte(), 0)
        assertEquals(5, KompactRuntime.readBits(buf, bitOffset = 5, bitWidth = 3))
    }

    @Test
    fun readBits_cross_byte_4bits_at_offset_6() {
        // value 0xA (1010) at bit 6: low 2 bits in byte 0 (bits 6,7) +
        // high 2 bits in byte 1 (bits 0,1). buf = [0x80, 0x0A, 0..]
        val buf = byteArrayOf(0x80.toByte(), 0x0A)
        assertEquals(0xA, KompactRuntime.readBits(buf, bitOffset = 6, bitWidth = 4))
    }

    @Test
    fun readBits_width_64_across_8_bytes() {
        // Span all 64 bits: low byte first (LSB-first). Value = 0x0123456789ABCDEFL.
        val buf = byteArrayOf(
            0xEF.toByte(), 0xCD.toByte(), 0xAB.toByte(), 0x89.toByte(),
            0x67.toByte(), 0x45.toByte(), 0x23.toByte(), 0x01
        )
        assertEquals(0x0123456789ABCDEFL, KompactRuntime.readBitsLong(buf, bitOffset = 0, bitWidth = 64))
    }

    @Test
    fun readBits_width_1_returns_lsb_of_byte() {
        // LSB-first: bit 0 of value sits at the chosen offset.
        // bit 0 of byte = 0x01; bit 7 of byte = 0x80.
        val buf = byteArrayOf(0x01)

        assertEquals(0, KompactRuntime.readBits(buf, bitOffset = 1, bitWidth = 1))
    }

    @Test
    fun readBits_handles_negative_byte_correctly() {
        // 0xFF as signed Byte = -1. Mask `and 0xFF` must restore 255.
        val buf = byteArrayOf(0xFF.toByte())
        assertEquals(0xFF, KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 8))
    }

    // --- readBitsBoolean (Ticket 01) ---

    @Test
    fun readBitsBoolean_true() {
        val buf = byteArrayOf(0b0000_0001.toByte())
        assertEquals(true, KompactRuntime.readBitsBoolean(buf, bitOffset = 0))
    }

    @Test
    fun readBitsBoolean_false() {
        val buf = byteArrayOf(0b0000_0000.toByte())
        assertEquals(false, KompactRuntime.readBitsBoolean(buf, bitOffset = 0))
    }

    @Test
    fun readBitsBoolean_bit_15() {
        // 0x8000 as Byte[] = [0x00, 0x80] (LE).
        val buf = byteArrayOf(0x00, 0x80.toByte())
        assertEquals(true, KompactRuntime.readBitsBoolean(buf, bitOffset = 15))
    }

    // --- Argument validation ---

    @Test
    fun readBits_rejects_zero_width() {
        val buf = byteArrayOf(0)
        assertFailsWith<IllegalArgumentException> {
            KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 0)
        }
    }

    @Test
    fun readBits_rejects_width_over_64() {
        val buf = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
        assertFailsWith<IllegalArgumentException> {
            KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 65)
        }
    }

    @Test
    fun readBits_rejects_negative_offset() {
        val buf = byteArrayOf(0)
        assertFailsWith<IllegalArgumentException> {
            KompactRuntime.readBits(buf, bitOffset = -1, bitWidth = 1)
        }
    }

    @Test
    fun readBits_rejects_past_end() {
        val buf = byteArrayOf(0, 0)
        // bit 16 = byte 2, which doesn't exist.
        assertFailsWith<IllegalArgumentException> {
            KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 17)
        }
    }
}
