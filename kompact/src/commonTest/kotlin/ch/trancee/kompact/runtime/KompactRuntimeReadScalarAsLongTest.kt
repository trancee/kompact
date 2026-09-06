package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KompactRuntimeReadScalarAsLongTest {

    // --- 64-bit width (readScalarAsLong) ---

    @Test
    fun readScalarLong_width64_signed_maxValue() {
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, Long.MAX_VALUE)
        val r = KompactRuntime.readScalarAsLong(buf, 0, ScalarType.of(64, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(Long.MAX_VALUE, r.getOrThrow())
    }

    @Test
    fun readScalarLong_width64_signed_nearMinValue() {
        // Long.MIN_VALUE (0x8000...) is in the failure sentinel range (documented tradeoff).
        // 0x8400_0000_0000_0000 is the first representable success value below 0.
        val value = Long.MIN_VALUE + (1L shl 58)
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, value)
        val r = KompactRuntime.readScalarAsLong(buf, 0, ScalarType.of(64, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(value, r.getOrThrow())
    }

    @Test
    fun readScalarLong_width64_signed_negativeValue() {
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, -1L)
        val r = KompactRuntime.readScalarAsLong(buf, 0, ScalarType.of(64, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(-1L, r.getOrThrow())
    }

    @Test
    fun readScalarLong_width64_signed_smallBitWidth_signExtended() {
        // 4-bit value 0b1111 (15 unsigned) → -1 signed
        val buf = byteArrayOf(0x0F.toByte())
        val r = KompactRuntime.readScalarAsLong(buf, 0, ScalarType.of(4, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(-1L, r.getOrThrow())
    }

    @Test
    fun readScalarLong_width64_signed_largePositive() {
        // 0x4000_0000_0000_0000 is outside the LongResult failure sentinel range
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, 0x4000_0000_0000_0000L)
        val r = KompactRuntime.readScalarAsLong(buf, 0, ScalarType.of(64, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(0x4000_0000_0000_0000L, r.getOrThrow())
    }

    @Test
    fun readScalarLong_width64_signed_boundsError_shortBuffer() {
        val buf = ByteArray(7) { 0 }
        val r = KompactRuntime.readScalarAsLong(buf, 0, ScalarType.of(64, signed = true)) // needs 64 bits, only 56 available
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalarLong_width64_unsigned_value() {
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, -1L)
        val r = KompactRuntime.readScalarAsLong(buf, 0, ScalarType.of(64, signed = false))
        assertTrue(r.isSuccess)
        assertEquals(-1L, r.getOrThrow())
    }

    @Test
    fun readScalarLong_width64_unsigned_smallBitWidth() {
        val buf = ByteArray(1) { 0 }
        KompactRuntime.writeBits(buf, 0, 3, 5)
        val r = KompactRuntime.readScalarAsLong(buf, 0, ScalarType.of(3, signed = false))
        assertTrue(r.isSuccess)
        assertEquals(5L, r.getOrThrow())
    }

    @Test
    fun readScalarLong_width64_unsigned_boundsError_shortBuffer() {
        val r = KompactRuntime.readScalarAsLong(ByteArray(4), 0, ScalarType.of(64, signed = false))
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }
}
