package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class KompactRuntimeLongBitsTest {

    // Ticket 01: LSB-first — same bit order as readBits, but Long-backed (1..64).

    @Test
    fun readBitsLong_singleByte() {
        assertEquals(0x7FL, KompactRuntime.readBitsLong(byteArrayOf(0x7F), 0, 8))
    }

    @Test
    fun readBitsLong_masksSignedBytesLsbFirst() {
        // 0xFF (signed -1) must be masked before shl/or — identical on JVM/Native.
        val buf = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        assertEquals(0xFFFFL, KompactRuntime.readBitsLong(buf, 0, 16))
    }

    @Test
    fun readBitsLong_crossByteBoundary() {
        val buf = byteArrayOf(0xD2.toByte(), 0x3D.toByte())
        assertEquals(221L, KompactRuntime.readBitsLong(buf, 4, 8))
    }

    @Test
    fun readBitsLong_assemblesUpTo64Bits_allOnes() {
        val buf = ByteArray(8) { 0xFF.toByte() }
        assertEquals(-1L, KompactRuntime.readBitsLong(buf, 0, 64))
    }

    @Test
    fun readBitsLong_assembles63Bits() {
        val buf = ByteArray(8) { 0xFF.toByte() }
        // bit 63 = 0 → 63-bit all-ones
        buf[7] = 0x7F.toByte()
        assertEquals(0x7FFF_FFFF_FFFF_FFFFL, KompactRuntime.readBitsLong(buf, 0, 63))
    }

    @Test
    fun readBitsLong_singleBit_atByteBoundary() {
        val buf = byteArrayOf(0x00, 0x01, 0x00)
        assertEquals(1L, KompactRuntime.readBitsLong(buf, 8, 1))
    }

    @Test
    fun readBitsLong_subByteWidth_returnsUnsignedMagnitude() {
        val buf = byteArrayOf(0b1111_1111.toByte())
        // 4-bit read → 15, not -1
        assertEquals(15L, KompactRuntime.readBitsLong(buf, 0, 4))
    }

    // --- writeBitsLong round-trip ---

    @Test
    fun writeBitsLong_thenReadLong_singleByte() {
        val buf = ByteArray(1) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 4, 0b1010L)
        assertEquals(0b1010L, KompactRuntime.readBitsLong(buf, 0, 4))
        assertEquals(0L, KompactRuntime.readBitsLong(buf, 4, 4)) // high nibble untouched
    }

    @Test
    fun writeBitsLong_thenReadLong_crossByte() {
        val buf = ByteArray(2) { 0 }
        KompactRuntime.writeBitsLong(buf, 4, 10, 438L)
        assertEquals(438L, KompactRuntime.readBitsLong(buf, 4, 10))
    }

    @Test
    fun writeBitsLong_thenReadLong_64BitAllOnes() {
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, -1L)
        assertEquals(-1L, KompactRuntime.readBitsLong(buf, 0, 64))
    }

    @Test
    fun writeBitsLong_thenReadLong_largePositive() {
        val buf = ByteArray(8) { 0 }
        val value = 0x4000_0000_0000_0000L // bit 62 set, outside LongResult sentinel range
        KompactRuntime.writeBitsLong(buf, 0, 64, value)
        assertEquals(value, KompactRuntime.readBitsLong(buf, 0, 64))
    }

    @Test
    fun writeBitsLong_thenReadLong_32BitWriteDoesNotAffectUpperBits() {
        val buf = ByteArray(8) { 0 }
        buf[7] = 0x80.toByte() // pre-set bit 63
        val v32: Long = 0xBEEFCAFEL
        KompactRuntime.writeBitsLong(buf, 0, 32, v32)
        assertEquals(v32, KompactRuntime.readBitsLong(buf, 0, 32))
        // Upper 32 bits untouched — byte 7 still has 0x80
        assertEquals(0x8000_0000L, KompactRuntime.readBitsLong(buf, 32, 32))

    }

    // --- writeBitsLong only touches target range ---

    @Test
    fun writeBitsLong_doesNotClobberOtherBits() {
        val original = byteArrayOf(0xF0.toByte(), 0x0F.toByte())
        val buf = original.copyOf()
        KompactRuntime.writeBitsLong(buf, 0, 4, 0b0101L)
        // Low nibble of byte 0 changed, high nibble untouched
        assertEquals(0b0101L, KompactRuntime.readBitsLong(buf, 0, 4))
        assertEquals(0b1111L, KompactRuntime.readBitsLong(buf, 4, 4))
        // Byte 1 untouched
        assertEquals(0x0FL, KompactRuntime.readBitsLong(buf, 8, 8))
    }
}
