package ch.trancee.kompact.writer

import ch.trancee.kompact.runtime.KompactRuntime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec: .scratch/kompact-spec/issues/07-write-builder-interface.md
 *
 * KompactWriter owns a growable buffer; fields are written forward-only;
 * build() snapshots the result. Write→ByteArray→read is symmetric with
 * the read path (Ticket 07).
 */
class KompactWriterTest {

    @Test
    fun writeUInt8_then_build() {
        val w = KompactWriter()
        w.writeUInt8(0x42)
        val bytes = w.build()
        assertEquals(1, bytes.size)
        assertEquals(0x42, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun writeUInt8_then_read_back_via_runtime() {
        val w = KompactWriter()
        w.writeUInt8(0xA5)
        val bytes = w.build()
        assertEquals(0xA5, KompactRuntime.readBits(bytes, bitOffset = 0, bitWidth = 8))
    }

    @Test
    fun writeTwoAdjacentFields_packed_in_one_byte() {
        val w = KompactWriter()
        w.writeUInt4(0xA) // bits 0..3
        w.writeUInt4(0x5) // bits 4..7
        val bytes = w.build()
        assertEquals(1, bytes.size)
        assertEquals(0x5A, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun writeBool1_true() {
        val w = KompactWriter()
        w.writeBool(true)
        val bytes = w.build()
        assertEquals(1, bytes.size)
        assertEquals(0x01, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun writeBool1_false() {
        val w = KompactWriter()
        w.writeBool(false)
        val bytes = w.build()
        assertEquals(1, bytes.size)
        assertEquals(0x00, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun writeUInt10_across_byte_boundary() {
        val w = KompactWriter()
        w.writeUInt4(0xC)    // bits 0..3
        w.writeUInt10(677)    // bits 4..13
        val bytes = w.build()
        assertEquals(2, bytes.size)
        // byte 0: low nibble = 0xC, high nibble = low 4 bits of 677 = 0x5
        // => 0x5C
        assertEquals(0x5C, bytes[0].toInt() and 0xFF)
        // byte 1: high 6 bits of 677 = 0x2A
        assertEquals(0x2A, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun writeString_length_prefix_then_bytes() {
        val w = KompactWriter()
        w.writeString("Hi", lengthPrefixBits = 8)
        val bytes = w.build()
        assertEquals(3, bytes.size)
        assertEquals(2, bytes[0].toInt() and 0xFF) // length
        assertEquals('H'.code, bytes[1].toInt() and 0xFF)
        assertEquals('i'.code, bytes[2].toInt() and 0xFF)
    }

    @Test
    fun writeNested_emits_sub_region_with_length_prefix() {
        val w = KompactWriter()
        w.writeNested(lengthPrefixBits = 8) { n ->
            n.writeUInt8(0xAB)
            n.writeUInt8(0xCD)
        }
        val bytes = w.build()
        assertEquals(3, bytes.size)
        assertEquals(2, bytes[0].toInt() and 0xFF)
        assertEquals(0xAB, bytes[1].toInt() and 0xFF)
        assertEquals(0xCD, bytes[2].toInt() and 0xFF)
    }

    @Test
    fun writeNested_returns_its_bytes() {
        val w = KompactWriter()
        val nested = w.writeNested(lengthPrefixBits = 8) { n ->
            n.writeUInt8(0x12)
        }
        assertEquals(1, nested.size)
        assertEquals(0x12, nested[0].toInt() and 0xFF)
    }

    @Test
    fun writeRepeated_count_prefix_then_elements() {
        val w = KompactWriter()
        w.writeRepeated(count = 2, countPrefixBits = 8) { rw ->
            rw.writeUInt4(0x1)
            rw.writeUInt4(0x2)
        }
        val bytes = w.build()
        // 1 byte count + 1 byte packed 2 elements of 4 bits = 2 bytes
        assertEquals(2, bytes.size)
        assertEquals(2, bytes[0].toInt() and 0xFF) // count = 2
        // second byte holds both 4-bit elements
        assertEquals(0x21, bytes[1].toInt() and 0xFF)
    }
}
