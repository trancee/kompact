package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class KompactRuntimeTest {

    // Ticket 01: LSB-first — byte 0 holds value bits 0-7, bit 0 = value LSB.

    @Test
    fun readBits_lowNibbleOfSingleByte() {
        val buf = byteArrayOf(0b0000_0001)
        val result = KompactRuntime.readBits(buf, 0, 4)
        assertEquals(1, result)
    }

    @Test
    fun readBits_highNibbleOfSingleByteClear() {
        val buf = byteArrayOf(0b0000_0001)
        val result = KompactRuntime.readBits(buf, 4, 4)
        assertEquals(0, result)
    }

    @Test
    fun readBits_highNibbleOfSingleByteSet() {
        val buf = byteArrayOf(0x80.toByte())
        val result = KompactRuntime.readBits(buf, 4, 4)
        assertEquals(8, result)
    }

    @Test
    fun readBits_masksSignedBytesLsbFirst() {
        // PROMPT §1: 0xFF (signed -1) must be masked so the sign bit doesn't
        // pollute shl/or — identical result on JVM and Kotlin/Native.
        val buf = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val low = KompactRuntime.readBits(buf, 0, 4)
        val high = KompactRuntime.readBits(buf, 4, 4)
        assertEquals(0b1111, low)
        assertEquals(0b1111, high)
    }

    @Test
    fun readBits_assemblesLeastSignificantBitsFirst() {
        val buf = byteArrayOf(0xF0.toByte(), 0x03)
        val result = KompactRuntime.readBits(buf, 0, 10) // low 8 from byte0, high 2 from byte1
        assertEquals(0xF0 or (0b11 shl 8), result) // 1008
    }

    @Test
    fun readBits_crossByteBoundary() {
        val buf = byteArrayOf(0xD2.toByte(), 0x3D.toByte())
        val result = KompactRuntime.readBits(buf, 4, 8)
        assertEquals(221, result)
    }

    @Test
    fun readBits_allOnesCrossByteMax() {
        val buf = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val result = KompactRuntime.readBits(buf, 4, 10)
        assertEquals(1023, result)
    }

    // --- readBitsBoolean (single bit) ---

    @Test
    fun readBitsBoolean_trueWhenSet() {
        val buf = byteArrayOf(0b0000_0001)
        val result = KompactRuntime.readBitsBoolean(buf, 0)
        assertEquals(true, result)
    }

    @Test
    fun readBitsBoolean_falseWhenClear() {
        val buf = byteArrayOf(0b0000_0010)
        val result = KompactRuntime.readBitsBoolean(buf, 0)
        assertEquals(false, result)
    }

    @Test
    fun readBitsBoolean_falseOnZeroByte() {
        val result = KompactRuntime.readBitsBoolean(byteArrayOf(0), 0)
        assertEquals(false, result)
    }

    @Test
    fun readBitsBoolean_bit7() {
        val result = KompactRuntime.readBitsBoolean(byteArrayOf(0x80.toByte()), 7)
        assertEquals(true, result)
    }

    // --- writeBits then readBits (write encodes the input; read decodes it) ---

    @Test
    fun writeBits_thenReadBits_singleByte() {
        val buf = ByteArray(1) { 0 }
        KompactRuntime.writeBits(buf, 0, 4, 0b1010)
        val low = KompactRuntime.readBits(buf, 0, 4)
        val high = KompactRuntime.readBits(buf, 4, 4)
        assertEquals(0b1010, low)
        assertEquals(0, high) // high nibble untouched
    }

    @Test
    fun writeBits_thenReadBits_crossByte() {
        val buf = ByteArray(2) { 0 }
        KompactRuntime.writeBits(buf, 4, 10, 438)
        val result = KompactRuntime.readBits(buf, 4, 10)
        assertEquals(438, result)
    }

    @Test
    fun writeBits_overwritesExistingBitsLowNibble() {
        val buf = byteArrayOf(0xFF.toByte())
        KompactRuntime.writeBits(buf, 0, 4, 0)
        val low = KompactRuntime.readBits(buf, 0, 4)
        val high = KompactRuntime.readBits(buf, 4, 4)
        assertEquals(0, low)
        assertEquals(0b1111, high)
    }

    @Test
    fun writeBits_overwritesExistingBitsHighNibble() {
        val buf = byteArrayOf(0xFF.toByte())
        KompactRuntime.writeBits(buf, 4, 4, 0b0101)
        val low = KompactRuntime.readBits(buf, 0, 4)
        val high = KompactRuntime.readBits(buf, 4, 4)
        assertEquals(0b0101, high)
        assertEquals(0b1111, low)
    }

    // --- writeBitsBoolean ---

    @Test
    fun writeBitsBoolean_setsBit() {
        val buf = ByteArray(1) { 0 }
        KompactRuntime.writeBitsBoolean(buf, 3, true)
        val readBack = KompactRuntime.readBitsBoolean(buf, 3)
        assertEquals(true, readBack)
        assertEquals(0b0000_1000, buf[0].toInt() and 0xFF)
    }

    @Test
    fun writeBitsBoolean_clearsBit() {
        val buf = byteArrayOf(0xFF.toByte())
        KompactRuntime.writeBitsBoolean(buf, 3, false)
        val readBack = KompactRuntime.readBitsBoolean(buf, 3)
        assertEquals(false, readBack)
        assertEquals(0b1111_0111, buf[0].toInt() and 0xFF)
    }

    @Test
    fun writeBits_doesNotClobberOtherBits() {
        val buf = byteArrayOf(0xF0.toByte())
        KompactRuntime.writeBits(buf, 0, 4, 0b0101)
        val low = KompactRuntime.readBits(buf, 0, 4)
        val high = KompactRuntime.readBits(buf, 4, 4)
        assertEquals(0b0101, low)
        assertEquals(0b1111, high)
    }
}
