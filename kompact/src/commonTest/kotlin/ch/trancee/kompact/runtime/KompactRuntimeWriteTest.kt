package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Spec: .scratch/kompact-spec/issues/01-wire-format-bit-order.md + 07-write-builder-interface.md
 *
 * writeBits must be the inverse of readBits for all (offset, width, value) triples
 * within the buffer's bit-capacity. The writer owns its buffer; this test
 * exercises the raw bit-write primitive directly.
 */
class KompactRuntimeWriteTest {

    @Test
    fun writeBits_aligned_8bits_then_read_back() {
        val buf = ByteArray(2)
        KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 8, value = 0xA5)
        assertEquals(0xA5, KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 8))
    }

    @Test
    fun writeBits_cross_byte_10bits_then_read_back() {
        val buf = ByteArray(2)
        KompactRuntime.writeBits(buf, bitOffset = 4, bitWidth = 10, value = 677)
        assertEquals(677, KompactRuntime.readBits(buf, bitOffset = 4, bitWidth = 10))
    }

    @Test
    fun writeBits_preserves_adjacent_fields() {
        // Two adjacent 4-bit fields. First = 0xA, second = 0x5. Together = 0x5A in the byte.
        val buf = ByteArray(1)
        KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 4, value = 0xA)
        KompactRuntime.writeBits(buf, bitOffset = 4, bitWidth = 4, value = 0x5)
        assertEquals(0xA, KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 4))
        assertEquals(0x5, KompactRuntime.readBits(buf, bitOffset = 4, bitWidth = 4))
        // And the byte is exactly 0x5A.
        assertEquals(0x5A, buf[0].toInt() and 0xFF)
    }

    @Test
    fun writeBits_overwrite_does_not_corrupt_siblings() {
        val buf = ByteArray(2)
        KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 4, value = 0xC)
        KompactRuntime.writeBits(buf, bitOffset = 8, bitWidth = 4, value = 0x3)
        // overwrite the first field with a new value
        KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 4, value = 0x5)
        assertEquals(0x5, KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 4))
        assertEquals(0x3, KompactRuntime.readBits(buf, bitOffset = 8, bitWidth = 4))
    }

    @Test
    fun writeBits_full_64bit_round_trip() {
        val buf = ByteArray(8)
        val v = 0x0123456789ABCDEFL.toLong()
        KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 64, value = v.toULong().toLong())
        assertEquals(v, KompactRuntime.readBitsLong(buf, bitOffset = 0, bitWidth = 64))
    }

    @Test
    fun writeBits_zero_width_is_rejected() {
        val buf = ByteArray(1)
        assertFailsWith<IllegalArgumentException> {
            KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 0, value = 0)
        }
    }

    @Test
    fun writeBits_truncates_value_to_width() {
        // 0x1FF (= 9 bits) written into 8 bits must keep only the low 8 bits = 0xFF.
        val buf = ByteArray(1)
        KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 8, value = 0x1FF)
        assertEquals(0xFF, KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 8))
    }
}
