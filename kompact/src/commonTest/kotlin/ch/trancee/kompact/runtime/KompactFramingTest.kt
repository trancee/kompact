package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Ticket 05: sequential, length-delimited framing (fixed-width LE byte-count
 * prefix per field; parse-forward nested sub-regions; count-prefixed repeats).
 */
class KompactFramingTest {

    // --- 8-bit length prefix (1 byte) ---

    @Test
    fun readLengthPrefix8_readsSingleByteLittleEndian() {
        val buf = ByteArray(1)
        KompactRuntime.writeBits(buf, 0, 8, 5)
        val len = KompactFraming.readLengthPrefix(buf, 0, 8)
        assertEquals(5, len)
    }

    @Test
    fun readLengthPrefix8_readsMax255() {
        val buf = ByteArray(1)
        KompactRuntime.writeBits(buf, 0, 8, 255)
        assertEquals(255, KompactFraming.readLengthPrefix(buf, 0, 8))
    }

    // --- 16-bit length prefix (little-endian) ---

    @Test
    fun readLengthPrefix16_readsLittleEndianTwoBytes() {
        val buf = ByteArray(2)
        // 0x0100 = 256 in LE bytes = [0x00, 0x01]
        buf[0] = 0x00
        buf[1] = 0x01
        assertEquals(256, KompactFraming.readLengthPrefix(buf, 0, 16))
    }

    @Test
    fun readLengthPrefix16_readsBigValueMax65535() {
        val buf = ByteArray(2) { 0xFF.toByte() }
        assertEquals(65535, KompactFraming.readLengthPrefix(buf, 0, 16))
    }

    // --- 32-bit length prefix (little-endian) ---

    @Test
    fun readLengthPrefix32_readsLittleEndianFourBytes() {
        val buf = ByteArray(4)
        // 0x00010000 = 65536 in LE = [0x00,0x00,0x01,0x00]
        buf[2] = 0x01.toByte()
        assertEquals(65536, KompactFraming.readLengthPrefix(buf, 0, 32))
    }

    // --- write + read round trip ---

    @Test
    fun lengthPrefix8_roundTrip() {
        val buf = ByteArray(3)
        KompactFraming.writeLengthPrefix(buf, 0, 8, 3)
        assertEquals(3, KompactFraming.readLengthPrefix(buf, 0, 8))
    }

    @Test
    fun lengthPrefix16_roundTrip() {
        val buf = ByteArray(4)
        KompactFraming.writeLengthPrefix(buf, 0, 16, 1234)
        assertEquals(1234, KompactFraming.readLengthPrefix(buf, 0, 16))
    }

    @Test
    fun lengthPrefix32_roundTrip() {
        val buf = ByteArray(6)
        KompactFraming.writeLengthPrefix(buf, 0, 32, 70_000)
        assertEquals(70_000, KompactFraming.readLengthPrefix(buf, 0, 32))
    }

    // --- nested region: length-delimited, parse-forward ---

    @Test
    fun nestedRegionBoundsFromLengthPrefix8() {
        // [0..7] prefix byte = 4 (8-bit, 4 bytes follow); payload at bits 8..39.
        val buf = byteArrayOf(4, 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0x10)
        val region = KompactFraming.nestedRegionOrNull(buf, 0, 8)
        assertNotNull(region)
        val (startBit, bitLen) = region
        assertEquals(8, startBit)
        assertEquals(32, bitLen)
        // First payload byte at bit 8 == 0xAB; last byte at bits 32..39 == 0x10.
        assertEquals(0xAB, KompactRuntime.readBits(buf, startBit, 8))
        assertEquals(0x10, KompactRuntime.readBits(buf, startBit + bitLen - 8, 8))
    }

    @Test
    fun nestedRegion8_truncatedReturnsNull() {
        // length prefix says 10 bytes but only 3 available past the prefix byte.
        val buf = byteArrayOf(10, 1, 2, 3)
        val region = KompactFraming.nestedRegionOrNull(buf, 0, 8)
        // TruncatedNested: prefix exceeds remaining bytes (fail-fast, never silent).
        assertEquals(null, region)
    }


    // --- repeat count: fixed-width LE count prefix, sequential ---

    @Test
    fun repeatCount_roundTrip() {
        val buf = ByteArray(4)
        KompactFraming.writeLengthPrefix(buf, 0, 16, 7)
        assertEquals(7, KompactFraming.readLengthPrefix(buf, 0, 16))
    }
}
