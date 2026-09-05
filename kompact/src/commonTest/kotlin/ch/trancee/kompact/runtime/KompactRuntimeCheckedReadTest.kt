package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KompactRuntimeCheckedReadTest {

    // === readBool ===

    @Test
    fun readBool_success_true() {
        val buf = byteArrayOf(0x01)
        val r = KompactRuntime.readBool(buf, 0)
        assertTrue(r.isSuccess)
        assertEquals(true, r.getOrThrow())
    }

    @Test
    fun readBool_success_false() {
        val buf = byteArrayOf(0x00)
        val r = KompactRuntime.readBool(buf, 0)
        assertTrue(r.isSuccess)
        assertEquals(false, r.getOrThrow())
    }

    @Test
    fun readBool_success_bit7() {
        val r = KompactRuntime.readBool(byteArrayOf(0x80.toByte()), 7)
        assertTrue(r.isSuccess)
        assertEquals(true, r.getOrThrow())
    }

    @Test
    fun readBool_boundsError_shortBuffer() {
        val buf = byteArrayOf(0x00)
        val r = KompactRuntime.readBool(buf, 8)
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    // === readScalar (1..32 bits) / readScalarLong (1..64 bits) ===
    // Replaces the per-width readInt8/readUInt8/readInt16/readUInt16/readInt32/
    // readUInt32/readInt64/readUInt64 accessors (Ticket 10 deepen).
    // Sign/zero extension uses Long-arithmetic shifts — bit-identical to the
    // legacy accessors' Int/Long shift logic.

    // --- 8-bit width (readScalar) ---

    @Test
    fun readScalar_width8_signed_positiveValue() {
        val buf = ByteArray(1) { 0 }
        KompactRuntime.writeBits(buf, 0, 8, 42)
        val r = KompactRuntime.readScalar(buf, 0, 8, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(42, r.getOrThrow())
    }

    @Test
    fun readScalar_width8_signed_negativeValue_signExtended() {
        // 8-bit value 0xC8 (200 unsigned) → -56 signed
        val buf = byteArrayOf(0xC8.toByte())
        val r = KompactRuntime.readScalar(buf, 0, 8, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(-56, r.getOrThrow())
    }

    @Test
    fun readScalar_width8_signed_smallBitWidth_signExtended() {
        // 4-bit value 0b1111 (15 unsigned) → -1 signed
        val buf = byteArrayOf(0x0F.toByte())
        val r = KompactRuntime.readScalar(buf, 0, 4, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(-1, r.getOrThrow())
    }

    @Test
    fun readScalar_width8_signed_smallBitWidth_positive() {
        val buf = ByteArray(1) { 0 }
        KompactRuntime.writeBits(buf, 0, 4, 5)
        val r = KompactRuntime.readScalar(buf, 0, 4, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(5, r.getOrThrow())
    }

    @Test
    fun readScalar_boundsError_shortBuffer() {
        val buf = ByteArray(1) { 0 }
        val r = KompactRuntime.readScalar(buf, 4, 8, signed = true) // needs 12 bits, only 8 available
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalar_boundsError_bitWidthTooLarge() {
        val buf = ByteArray(1) { 0 }
        val r = KompactRuntime.readScalar(buf, 0, 33, signed = true) // max 32
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalar_width8_unsigned_value() {
        // 0xFF as unsigned → 255 (zero-extended to Int)
        val r = KompactRuntime.readScalar(byteArrayOf(0xFF.toByte()), 0, 8, signed = false)
        assertTrue(r.isSuccess)
        assertEquals(255, r.getOrThrow())
    }

    @Test
    fun readScalar_width8_unsigned_smallBitWidth() {
        val buf = ByteArray(1) { 0 }
        KompactRuntime.writeBits(buf, 0, 3, 7)
        val r = KompactRuntime.readScalar(buf, 0, 3, signed = false)
        assertTrue(r.isSuccess)
        assertEquals(7, r.getOrThrow())
    }

    @Test
    fun readScalar_width8_unsigned_boundsError_shortBuffer() {
        val r = KompactRuntime.readScalar(ByteArray(0), 0, 4, signed = false)
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    // --- 16-bit width (readScalar) ---

    @Test
    fun readScalar_width16_signed_positiveValue() {
        val buf = ByteArray(2) { 0 }
        KompactRuntime.writeBits(buf, 0, 16, 1024)
        val r = KompactRuntime.readScalar(buf, 0, 16, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(1024, r.getOrThrow())
    }

    @Test
    fun readScalar_width16_signed_negativeValue_signExtended() {
        // 16-bit value 0x8000 (32768 unsigned) → -32768 signed
        val buf = ByteArray(2) { 0 }
        buf[0] = 0x00.toByte()
        buf[1] = 0x80.toByte() // LSB-first: low byte = 0x00, high byte = 0x80
        val r = KompactRuntime.readScalar(buf, 0, 16, signed = true)
        assertTrue(r.isSuccess)
    assertEquals(Short.MIN_VALUE.toInt(), r.getOrThrow())
    }

    @Test
    fun readScalar_width16_signed_smallBitWidth() {
        // 5-bit value 0b11111 (31 unsigned) → -1 signed
        val buf = byteArrayOf(0x1F)
        val r = KompactRuntime.readScalar(buf, 0, 5, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(-1, r.getOrThrow())
    }

    @Test
    fun readScalar_width16_signed_boundsError_shortBuffer() {
        val buf = ByteArray(1) { 0 }
        val r = KompactRuntime.readScalar(buf, 4, 16, signed = true) // needs 20 bits, only 8 available
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalar_width16_unsigned_value() {
        val buf = ByteArray(2) { 0 }
        buf[0] = 0xFF.toByte()
        buf[1] = 0xFF.toByte()
        val r = KompactRuntime.readScalar(buf, 0, 16, signed = false)
        assertTrue(r.isSuccess)
        assertEquals(65535, r.getOrThrow())
    }

    // --- 32-bit width (readScalar) ---

    @Test
    fun readScalar_width32_signed_positiveValue() {
        val buf = ByteArray(4) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 32, 1_000_000L)
        val r = KompactRuntime.readScalar(buf, 0, 32, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(1_000_000, r.getOrThrow())
    }

    @Test
    fun readScalar_width32_signed_negativeValue() {
        val buf = ByteArray(4) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 32, 0xFF80_0000L)
        val r = KompactRuntime.readScalar(buf, 0, 32, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(-8_388_608, r.getOrThrow())
    }

    @Test
    fun readScalar_width32_signed_maxInt() {
        val buf = ByteArray(4) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 32, Int.MAX_VALUE.toLong())
        val r = KompactRuntime.readScalar(buf, 0, 32, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(Int.MAX_VALUE, r.getOrThrow())
    }

    @Test
    fun readScalar_width32_signed_minInt() {
        val buf = ByteArray(4) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 32, Int.MIN_VALUE.toLong())
        val r = KompactRuntime.readScalar(buf, 0, 32, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(Int.MIN_VALUE, r.getOrThrow())
    }

    @Test
    fun readScalar_width32_signed_smallBitWidth() {
        // 10-bit value 1008
        val buf = ByteArray(2) { 0 }
        KompactRuntime.writeBits(buf, 0, 10, 1008)
        val r = KompactRuntime.readScalar(buf, 0, 10, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(-16, r.getOrThrow())
    }

    @Test
    fun readScalar_width32_signed_boundsError_shortBuffer() {
        val buf = ByteArray(3) { 0 }
        val r = KompactRuntime.readScalar(buf, 0, 32, signed = true) // needs 32 bits, only 24 available
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalar_width32_unsigned_value() {
        val buf = ByteArray(4) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 32, 0xFFFF_FFFFL)
        val r = KompactRuntime.readScalar(buf, 0, 32, signed = false)
        assertTrue(r.isSuccess)
        assertEquals(-1, r.getOrThrow()) // 0xFFFFFFFF as signed Int = -1
        assertEquals(0xFFFF_FFFFL, r.getOrThrow().toLong() and 0xFFFF_FFFFL)
    }

    // --- 64-bit width (readScalarLong) ---

    @Test
    fun readScalarLong_width64_signed_maxValue() {
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, Long.MAX_VALUE)
        val r = KompactRuntime.readScalarLong(buf, 0, 64, signed = true)
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
        val r = KompactRuntime.readScalarLong(buf, 0, 64, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(value, r.getOrThrow())
    }

    @Test
    fun readScalarLong_width64_signed_negativeValue() {
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, -1L)
        val r = KompactRuntime.readScalarLong(buf, 0, 64, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(-1L, r.getOrThrow())
    }

    @Test
    fun readScalarLong_width64_signed_smallBitWidth_signExtended() {
        // 4-bit value 0b1111 (15 unsigned) → -1 signed
        val buf = byteArrayOf(0x0F.toByte())
        val r = KompactRuntime.readScalarLong(buf, 0, 4, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(-1L, r.getOrThrow())
    }

    @Test
    fun readScalarLong_width64_signed_largePositive() {
        // 0x4000_0000_0000_0000 is outside the LongResult failure sentinel range
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, 0x4000_0000_0000_0000L)
        val r = KompactRuntime.readScalarLong(buf, 0, 64, signed = true)
        assertTrue(r.isSuccess)
        assertEquals(0x4000_0000_0000_0000L, r.getOrThrow())
    }

    @Test
    fun readScalarLong_width64_signed_boundsError_shortBuffer() {
        val buf = ByteArray(7) { 0 }
        val r = KompactRuntime.readScalarLong(buf, 0, 64, signed = true) // needs 64 bits, only 56 available
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalarLong_width64_unsigned_value() {
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, -1L)
        val r = KompactRuntime.readScalarLong(buf, 0, 64, signed = false)
        assertTrue(r.isSuccess)
        assertEquals(-1L, r.getOrThrow())
    }

    @Test
    fun readScalarLong_width64_unsigned_smallBitWidth() {
        val buf = ByteArray(1) { 0 }
        KompactRuntime.writeBits(buf, 0, 3, 5)
        val r = KompactRuntime.readScalarLong(buf, 0, 3, signed = false)
        assertTrue(r.isSuccess)
        assertEquals(5L, r.getOrThrow())
    }

    @Test
    fun readScalarLong_width64_unsigned_boundsError_shortBuffer() {
        val r = KompactRuntime.readScalarLong(ByteArray(4), 0, 64, signed = false)
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    // === readFloat ===

    @Test
    fun readFloat_success_value() {
        val buf = ByteArray(4) { 0 }
        val value = 3.14f
        KompactRuntime.writeBitsLong(buf, 0, 32, value.toBits().toLong())
        val r = KompactRuntime.readFloat(buf, 0)
        assertTrue(r.isSuccess)
        assertEquals(3.14f, r.getOrThrow(), 0.0001f)
    }

    @Test
    fun readFloat_success_zero() {
        val buf = ByteArray(4) { 0 }
        val r = KompactRuntime.readFloat(buf, 0)
        assertTrue(r.isSuccess)
        assertEquals(0.0f, r.getOrThrow())
    }

    @Test
    fun readFloat_success_nan() {
        val buf = ByteArray(4) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 32, Float.NaN.toBits().toLong())
        val r = KompactRuntime.readFloat(buf, 0)
        assertTrue(r.isSuccess)
        assertTrue(r.getOrThrow().isNaN())
    }

    @Test
    fun readFloat_success_infinity() {
        val buf = ByteArray(4) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 32, Float.POSITIVE_INFINITY.toBits().toLong())
        val r = KompactRuntime.readFloat(buf, 0)
        assertTrue(r.isSuccess)
        assertEquals(Float.POSITIVE_INFINITY, r.getOrThrow())
    }

    @Test
    fun readFloat_boundsError_shortBuffer() {
        val buf = ByteArray(3) { 0 }
        val r = KompactRuntime.readFloat(buf, 0) // needs 32 bits, only 24 available
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    // === readDouble ===

    @Test
    fun readDouble_success_value() {
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, 3.14.toBits())
        val r = KompactRuntime.readDouble(buf, 0)
        assertTrue(r.isSuccess)
        assertEquals(3.14, r.getOrThrow(), 0.0001)
    }

    @Test
    fun readDouble_success_zero() {
        val buf = ByteArray(8) { 0 }
        val r = KompactRuntime.readDouble(buf, 0)
        assertTrue(r.isSuccess)
        assertEquals(0.0, r.getOrThrow())
    }

    @Test
    fun readDouble_success_nan() {
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, Double.NaN.toBits())
        val r = KompactRuntime.readDouble(buf, 0)
        assertTrue(r.isSuccess)
        assertTrue(r.getOrThrow().isNaN())
    }

    @Test
    fun readDouble_success_negativeInfinity() {
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, Double.NEGATIVE_INFINITY.toBits())
        val r = KompactRuntime.readDouble(buf, 0)
        assertTrue(r.isSuccess)
        assertEquals(Double.NEGATIVE_INFINITY, r.getOrThrow())
    }

    @Test
    fun readDouble_success_positiveInfinity() {
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, Double.POSITIVE_INFINITY.toBits())
        val r = KompactRuntime.readDouble(buf, 0)
        assertTrue(r.isSuccess)
        assertEquals(Double.POSITIVE_INFINITY, r.getOrThrow())
    }

    @Test
    fun readDouble_boundsError_shortBuffer() {
        val buf = ByteArray(7) { 0 }
        val r = KompactRuntime.readDouble(buf, 0) // needs 64 bits, only 56 available
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    // === F-002: integer-overflow hardening (Ticket 06) ===
    // A buffer >= 2^28 bytes (256 MiB) makes `raw.size * 8` overflow signed Int to a
    // negative value, so Int-arithmetic bounds checks in fits()/readFloat()/
    // readDouble() false-negative in-bounds reads as BoundsError. The Long-
    // promoted checks must succeed. (Boundary test; requires -Xmx1g — see
    // kompact/build.gradle.kts.)
    @Test
    fun boundsChecks_survive256MiBBuffer() {
        val buf = ByteArray(1 shl 28) // 268_435_456 bytes -> 2^31 bits (Int-overflow threshold)
        assertTrue(KompactRuntime.readBool(buf, 0).isSuccess, "readBool must succeed on a 256 MiB buffer")
        assertTrue(KompactRuntime.readScalar(buf, 0, 8, signed = false).isSuccess, "readScalar/8 unsigned must succeed")
        assertTrue(KompactRuntime.readScalar(buf, 0, 32, signed = true).isSuccess, "readScalar/32 signed must succeed")
        assertTrue(KompactRuntime.readFloat(buf, 0).isSuccess, "readFloat must succeed")
        assertTrue(KompactRuntime.readDouble(buf, 0).isSuccess, "readDouble must succeed")
    }
}
