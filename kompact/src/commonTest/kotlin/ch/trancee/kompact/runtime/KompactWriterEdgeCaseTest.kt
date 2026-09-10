package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Edge-case tests for KompactWriter error paths and branch conditions:
 *
 *  - require() guards in writeBits, writeBitsLong, writeScalar, writeRepeated
 *  - appendBytes() byte-aligned fast path vs. bit-level fallback path
 *  - build() with zero writes (empty result)
 *  - buffer growth past INITIAL_CAPACITY_BYTES (16)
 */
class KompactWriterEdgeCaseTest {
    // --- require() guards ---

    @Test
    fun writeBits_invalidZeroWidth_throws() {
        val w = KompactWriter()
        assertFailsWith<IllegalArgumentException> { w.writeBits(0, 0) }
    }

    @Test
    fun writeBits_invalidWidth32_throws() {
        val w = KompactWriter()
        assertFailsWith<IllegalArgumentException> { w.writeBits(32, 0) }
    }

    @Test
    fun writeBitsLong_invalidZeroWidth_throws() {
        val w = KompactWriter()
        assertFailsWith<IllegalArgumentException> { w.writeBitsLong(0, 0L) }
    }

    @Test
    fun writeBitsLong_invalidWidth65_throws() {
        val w = KompactWriter()
        assertFailsWith<IllegalArgumentException> { w.writeBitsLong(65, 0L) }
    }

    @Test
    fun writeScalar_invalidZeroWidth_throws() {
        val w = KompactWriter()
        assertFailsWith<IllegalArgumentException> {
            w.writeScalar(ScalarType.of(0, signed = false), 0L)
        }
    }

    @Test
    fun writeScalar_invalidWidth65_throws() {
        val w = KompactWriter()
        assertFailsWith<IllegalArgumentException> {
            w.writeScalar(ScalarType.of(65, signed = false), 0L)
        }
    }

    @Test
    fun writeRepeated_invalidCountWidth_throws() {
        val w = KompactWriter()
        assertFailsWith<IllegalArgumentException> {
            w.writeRepeated(count = 1, countWidth = 7) {}
        }
    }

    @Test
    fun writeRepeated_negativeCount_throws() {
        val w = KompactWriter()
        assertFailsWith<IllegalArgumentException> {
            w.writeRepeated(count = -1, countWidth = 8) {}
        }
    }

    // --- appendBytes: non-aligned bit cursor (bit-by-bit fallback path) ---

    @Test
    fun writeBlob_nonAlignedBitCursor_usesBitWritePath() {
        val w = KompactWriter()
        w.writeBits(4, 0b1010) // leave cursor at bit 4 (not byte-aligned)
        val blob = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        w.writeBlob(countWidth = 8, bytes = blob)
        val buf = w.build()

        // Prefix is at bit 4, payload starts at bit 4+8=12.
        assertEquals(4, KompactFraming.readLengthPrefix(buf, 4, 8))
        assertEquals(0x01, KompactRuntime.readBits(buf, 12, 8))
        assertEquals(0x02, KompactRuntime.readBits(buf, 20, 8))
        assertEquals(0x03, KompactRuntime.readBits(buf, 28, 8))
        assertEquals(0x04, KompactRuntime.readBits(buf, 36, 8))
    }

    // --- build() with empty writer ---

    @Test
    fun build_emptyWriter_returnsEmptyArray() {
        val w = KompactWriter()
        assertContentEquals(ByteArray(0), w.build())
    }

    // --- buffer growth past 16 bytes ---

    @Test
    fun writeBits_growsBuffer_pastInitialCapacity() {
        val w = KompactWriter()
        // 16 bytes = 128 bits initial capacity. Write 200 bits to force growth.
        w.writeBits(8, 0x42) // 8 bits
        repeat(24) { w.writeBits(8, 0x00) } // 24 * 8 = 192 bits, total 200 bits = 25 bytes
        val buf = w.build()
        assertEquals(25, buf.size) // ceil(200/8) = 25
        assertEquals(0x42, buf[0].toInt() and 0xFF)
        // remaining bytes are zero
        for (i in 1 until 25) {
            assertEquals(0, buf[i].toInt())
        }
    }

    @Test
    fun writeNested_growsBuffer_pastInitialCapacity() {
        val w = KompactWriter()
        w.writeNested(lengthPrefixWidth = 16) {
            repeat(20) { writeBitsLong(32, 0xDEADBEEFL) } // 20 * 32 = 640 bits = 80 bytes
        }
        val buf = w.build()
        // 16-bit prefix (2 bytes) + 80 bytes payload = 82 bytes
        assertEquals(82, buf.size)
    }

    @Test
    fun writeString_growsBuffer_pastInitialCapacity() {
        val w = KompactWriter()
        w.writeString(countWidth = 8, value = "x".repeat(30)) // 30 bytes + 1 prefix = 31
        val buf = w.build()
        assertEquals(31, buf.size)
        assertEquals(30, buf[0].toInt()) // prefix = 30
    }

    // --- writeScalar dispatching to writeBitsLong (width > 31) ---

    @Test
    fun writeScalar_width33_dispatchesToWriteBitsLong() {
        val w = KompactWriter()
        w.writeScalar(ScalarType.of(33, signed = false), 0x1FFFF) // 33-bit value
        val buf = w.build()
        assertEquals(5, buf.size) // ceil(33/8) = 5 bytes

        val r = KompactRuntime.readScalarAsLong(buf, 0, ScalarType.of(33, signed = false))
        assertTrue(r.isSuccess)
        assertEquals(0x1FFFFL, r.getOrThrow())
    }

    // --- writeRepeated with count=0 (empty repeat) ---

    @Test
    fun writeRepeated_countZero_emitsOnlyPrefix() {
        val w = KompactWriter()
        w.writeRepeated(count = 0, countWidth = 8) { } // just the count prefix
        val buf = w.build()
        assertEquals(1, buf.size)
        assertEquals(0, buf[0].toInt()) // count = 0
    }

    // --- getBitCursor access ---

    @Test
    fun bitCursor_tracksWrittenBits() {
        val w = KompactWriter()
        assertEquals(0, w.bitCursor)
        w.writeBits(4, 0b1010)
        assertEquals(4, w.bitCursor)
        w.writeBool(true)
        assertEquals(5, w.bitCursor)
        w.writeBits(3, 0b111)
        assertEquals(8, w.bitCursor) // now byte-aligned
    }

    // --- appendBytes byte-aligned fast path with content verification ---

    @Test
    fun appendBytes_byteAligned_verifiesContent() {
        val w = KompactWriter()
        w.writeBits(8, 0xAB) // byte-aligned
        w.writeBlob(countWidth = 8, bytes = byteArrayOf(0x01, 0x02, 0x03))
        val buf = w.build()
        assertEquals(5, buf.size) // 1 byte (data) + 1 prefix + 3 payload

        assertEquals(0xAB, buf[0].toInt() and 0xFF)
        assertEquals(3, KompactFraming.readLengthPrefix(buf, 8, 8))
    }
}
