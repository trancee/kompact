package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KompactRuntimeReadScalarTest {
    // === readScalar (1..32 bits) / readScalarLong (1..64 bits) ===
    // Replaces the per-width readInt8/readUInt8/readInt16/readUInt16/readInt32/
    // readUInt32/readInt64/readUInt64 accessors (ergonomics-01: ScalarType consolidation).
    // Sign/zero extension uses Long-arithmetic shifts — bit-identical to the
    // legacy accessors' Int/Long shift logic.

    // --- 8-bit width (readScalar) ---

    @Test
    fun readScalar_width8_signed_positiveValue() {
        val buf = ByteArray(1) { 0 }
        KompactRuntime.writeBits(buf, 0, 8, 42)
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(8, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(42, r.getOrThrow())
    }

    @Test
    fun readScalar_width8_signed_negativeValue_signExtended() {
        // 8-bit value 0xC8 (200 unsigned) → -56 signed
        val buf = byteArrayOf(0xC8.toByte())
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(8, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(-56, r.getOrThrow())
    }

    @Test
    fun readScalar_width8_signed_smallBitWidth_signExtended() {
        // 4-bit value 0b1111 (15 unsigned) → -1 signed
        val buf = byteArrayOf(0x0F.toByte())
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(4, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(-1, r.getOrThrow())
    }

    @Test
    fun readScalar_width8_signed_smallBitWidth_positive() {
        val buf = ByteArray(1) { 0 }
        KompactRuntime.writeBits(buf, 0, 4, 5)
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(4, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(5, r.getOrThrow())
    }

    @Test
    fun readScalar_boundsError_shortBuffer() {
        val buf = ByteArray(1) { 0 }
        val r = KompactRuntime.readScalar(buf, 4, ScalarType.of(8, signed = true)) // needs 12 bits, only 8 available
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalar_boundsError_bitWidthTooLarge() {
        val buf = ByteArray(1) { 0 }
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(33, signed = true)) // max 32
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalar_width8_unsigned_value() {
        // 0xFF as unsigned → 255 (zero-extended to Int)
        val r = KompactRuntime.readScalar(byteArrayOf(0xFF.toByte()), 0, ScalarType.of(8, signed = false))
        assertTrue(r.isSuccess)
        assertEquals(255, r.getOrThrow())
    }

    @Test
    fun readScalar_width8_unsigned_smallBitWidth() {
        val buf = ByteArray(1) { 0 }
        KompactRuntime.writeBits(buf, 0, 3, 7)
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(3, signed = false))
        assertTrue(r.isSuccess)
        assertEquals(7, r.getOrThrow())
    }

    @Test
    fun readScalar_width8_unsigned_boundsError_shortBuffer() {
        val r = KompactRuntime.readScalar(ByteArray(0), 0, ScalarType.of(4, signed = false))
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    // --- 16-bit width (readScalar) ---

    @Test
    fun readScalar_width16_signed_positiveValue() {
        val buf = ByteArray(2) { 0 }
        KompactRuntime.writeBits(buf, 0, 16, 1024)
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(16, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(1024, r.getOrThrow())
    }

    @Test
    fun readScalar_width16_signed_negativeValue_signExtended() {
        // 16-bit value 0x8000 (32768 unsigned) → -32768 signed
        val buf = ByteArray(2) { 0 }
        buf[0] = 0x00.toByte()
        buf[1] = 0x80.toByte() // LSB-first: low byte = 0x00, high byte = 0x80
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(16, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(Short.MIN_VALUE.toInt(), r.getOrThrow())
    }

    @Test
    fun readScalar_width16_signed_smallBitWidth() {
        // 5-bit value 0b11111 (31 unsigned) → -1 signed
        val buf = byteArrayOf(0x1F)
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(5, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(-1, r.getOrThrow())
    }

    @Test
    fun readScalar_width16_signed_boundsError_shortBuffer() {
        val buf = ByteArray(1) { 0 }
        val r = KompactRuntime.readScalar(buf, 4, ScalarType.of(16, signed = true)) // needs 20 bits, only 8 available
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalar_width16_unsigned_value() {
        val buf = ByteArray(2) { 0 }
        buf[0] = 0xFF.toByte()
        buf[1] = 0xFF.toByte()
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(16, signed = false))
        assertTrue(r.isSuccess)
        assertEquals(65535, r.getOrThrow())
    }

    // --- 32-bit width (readScalar) ---

    @Test
    fun readScalar_width32_signed_positiveValue() {
        val buf = ByteArray(4) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 32, 1_000_000L)
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(32, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(1_000_000, r.getOrThrow())
    }

    @Test
    fun readScalar_width32_signed_negativeValue() {
        val buf = ByteArray(4) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 32, 0xFF80_0000L)
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(32, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(-8_388_608, r.getOrThrow())
    }

    @Test
    fun readScalar_width32_signed_maxInt() {
        val buf = ByteArray(4) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 32, Int.MAX_VALUE.toLong())
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(32, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(Int.MAX_VALUE, r.getOrThrow())
    }

    @Test
    fun readScalar_width32_signed_minInt() {
        val buf = ByteArray(4) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 32, Int.MIN_VALUE.toLong())
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(32, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(Int.MIN_VALUE, r.getOrThrow())
    }

    @Test
    fun readScalar_width32_signed_smallBitWidth() {
        // 10-bit value 1008
        val buf = ByteArray(2) { 0 }
        KompactRuntime.writeBits(buf, 0, 10, 1008)
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(10, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(-16, r.getOrThrow())
    }

    @Test
    fun readScalar_width32_signed_boundsError_shortBuffer() {
        val buf = ByteArray(3) { 0 }
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(32, signed = true)) // needs 32 bits, only 24 available
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalar_width32_unsigned_value() {
        val buf = ByteArray(4) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 32, 0xFFFF_FFFFL)
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(32, signed = false))
        assertTrue(r.isSuccess)
        assertEquals(-1, r.getOrThrow()) // 0xFFFFFFFF as signed Int = -1
        assertEquals(0xFFFF_FFFFL, r.getOrThrow().toLong() and 0xFFFF_FFFFL)
    }
}
