package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ticket 07: write-builder API — growable buffer, forward-only, sub-writer for
 * nested + count-prefixed repeats, `build(): ByteArray`, symmetric with reads.
 */
class KompactWriterTest {

    // --- fixed-width scalar writes, symmetric with the read-side checked accessors ---

    @Test
    fun writeBool_roundTrip_viaReader() {
        val w = KompactWriter()
        w.writeBool(true)
        w.writeBool(false)
        w.writeBool(true)
        val buf = w.build()
        // 3 one-bit values pack into 1 byte.
        assertEquals(1, buf.size)
        val r = KompactRuntime.readBool(buf, 0)
        assertTrue(r.isSuccess); assertTrue(r.getOrThrow())
        assertEquals(false, KompactRuntime.readBool(buf, 1).getOrThrow())
        assertEquals(true, KompactRuntime.readBool(buf, 2).getOrThrow())
    }

    @Test
    fun writeScalar_width8_signed_roundTrip() {
        val w = KompactWriter()
        w.writeScalar(8, -5L)
        val buf = w.build()
        val r = KompactRuntime.readScalar(buf, 0, 8, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(-5, r.getOrThrow())
    }

    @Test
    fun writeScalar_width16_unsigned_roundTrip() {
        val w = KompactWriter()
        w.writeScalar(16, 1023L)
        val buf = w.build()
        val r = KompactRuntime.readScalar(buf, 0, 16, signed = false)
        assertTrue(r.isSuccess)
        assertEquals(1023, r.getOrThrow())
    }

    @Test
    fun writeScalar_width32_signed_negative_roundTrip() {
        val w = KompactWriter()
        w.writeScalar(32, -1L)
        val buf = w.build()
        val r = KompactRuntime.readScalar(buf, 0, 32, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(-1, r.getOrThrow())
    }

    @Test
    fun writeScalar_width4_roundTrip() {
        val w = KompactWriter()
        w.writeScalar(bitWidth = 4, value = 7L)
        val buf = w.build()
        assertEquals(7, KompactRuntime.readBits(buf, 0, 4))
    }

    // --- length-prefixed strings & blobs (Ticket 05 framing) ---

    @Test
    fun writeString_lengthPrefixed8_roundTrip() {
        val w = KompactWriter()
        w.writeString(countWidth = 8, value = "hi")
        val buf = w.build()
        // 1 prefix byte (length=2) + 2 UTF-8 bytes.
        assertEquals(3, buf.size)
        assertEquals(2, KompactFraming.readLengthPrefix(buf, 0, 8))
        assertContentEquals("hi".encodeToByteArray(), buf.copyOfRange(1, 3))
    }

    @Test
    fun writeBlob_lengthPrefixed16_roundTrip() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val w = KompactWriter()
        w.writeBlob(countWidth = 16, bytes = payload)
        val buf = w.build()
        assertEquals(4, KompactFraming.readLengthPrefix(buf, 0, 16))
        assertContentEquals(payload, buf.copyOfRange(2, 6))
    }

    // --- nested sub-writer (Ticket 05/07: child length first, then prefix + bytes) ---

    @Test
    fun writeNested_emitsPrefixThenChildBytes() {
        val w = KompactWriter()
        w.writeNested(lengthPrefixWidth = 16) {
            writeScalar(8, 0xABL)
            writeScalar(8, 0xCDL)
        }
        val buf = w.build()
        // 2-byte LE prefix (length=2) + 2 payload bytes.
        assertEquals(4, buf.size)
        assertEquals(2, KompactFraming.readLengthPrefix(buf, 0, 16))
        val region = KompactFraming.nestedRegionOrNull(buf, 0, 16)!!
        val (start, bitLen) = region
        assertEquals(16, start)
        assertEquals(16, bitLen)
    }

    @Test
    fun writeRepeated_emitsCountThenElements() {
        val w = KompactWriter()
        w.writeRepeated(count = 3, countWidth = 8) {
            writeBool(true)
        }
        val buf = w.build()
        // 1 prefix byte (count=3) + 3 packed one-bit elements → 2 bytes.
        assertEquals(2, buf.size)
        assertEquals(3, buf[0].toInt() and 0xFF)
        assertEquals(0x07, buf[1].toInt() and 0xFF)
    }

    // --- build() is exact length and forward-only ---

    @Test
    fun build_returnsExactLength() {
        val w = KompactWriter()
        w.writeBits(4, 0b1010)
        w.writeBool(true)
        // 4 bits + 1 bit = 5 bits → ceil(5/8) = 1 byte.
        assertEquals(1, w.build().size)
    }
}
