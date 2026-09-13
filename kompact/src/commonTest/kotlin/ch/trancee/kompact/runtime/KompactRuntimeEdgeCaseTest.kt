package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Edge-case tests for KompactRuntime error paths and branch conditions that
 * existing per-feature test suites don't trigger:
 *
 *  - `fits` with negative bitOffset and zero/negative bitWidth
 *  - `readScalar` / `readScalarAsLong` with invalid bitWidth (>32 / >64)
 *  - `readFloat` / `readDouble` with negative bitOffset
 */
class KompactRuntimeEdgeCaseTest {
    // --- fits() edge cases ---

    @Test
    fun fits_negativeBitOffset_returnsFalse() {
        assertFalse(KompactRuntime.fits(ByteArray(4), -1, 8))
    }

    @Test
    fun fits_zeroBitWidth_returnsFalse() {
        assertFalse(KompactRuntime.fits(ByteArray(4), 0, 0))
    }

    @Test
    fun fits_negativeBitWidth_returnsFalse() {
        assertFalse(KompactRuntime.fits(ByteArray(4), 0, -1))
    }

    // --- readScalar edge cases ---

    @Test
    fun readScalar_invalidBitWidth33_returnsBoundsError() {
        val r = KompactRuntime.readScalar(ByteArray(8), 0, ScalarType.of(33, signed = true))
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalar_unsignedWidth32_value() {
        val buf = ByteArray(4) { 0xFF.toByte() }
        val r = KompactRuntime.readScalar(buf, 0, ScalarType.of(32, signed = false))
        assertTrue(r.isSuccess)
        assertEquals(-1, r.getOrThrow())
    }

    // --- readScalarAsLong edge cases ---

    @Test
    fun readScalarAsLong_bitWidth65_returnsBoundsError() {
        val r = KompactRuntime.readScalarAsLong(ByteArray(9), 0, ScalarType.of(65, signed = false))
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalarAsLong_signedWidth64_negativeValue() {
        // signed && bitWidth == 64 → no sign extension (uses raw magnitude).
        val buf = ByteArray(8) { 0 }
        KompactRuntime.writeBitsLong(buf, 0, 64, -1L)
        val r = KompactRuntime.readScalarAsLong(buf, 0, ScalarType.of(64, signed = true))
        assertTrue(r.isSuccess)
        assertEquals(-1L, r.getOrThrow())
    }

    @Test
    fun readScalarAsLong_unsignedWidth64_value() {
        // Use a value outside the LongResult failure-sentinel band (bit 63 set
        // but bit 58 also set, per Ticket 08 documentation).
        val buf = ByteArray(8) { 0 }
        val value = Long.MIN_VALUE + (1L shl 58)
        KompactRuntime.writeBitsLong(buf, 0, 64, value)
        val r = KompactRuntime.readScalarAsLong(buf, 0, ScalarType.of(64, signed = false))
        assertTrue(r.isSuccess)
        assertEquals(value, r.getOrThrow())
    }

    // --- readFloat / readDouble edge cases (negative bitOffset) ---

    @Test
    fun readFloat_negativeBitOffset_returnsBoundsError() {
        val r = KompactRuntime.readFloat(ByteArray(4), -1)
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readDouble_negativeBitOffset_returnsBoundsError() {
        val r = KompactRuntime.readDouble(ByteArray(8), -1)
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    // --- readScalar with negative bitOffset ---

    @Test
    fun readScalar_negativeBitOffset_returnsBoundsError() {
        val r = KompactRuntime.readScalar(ByteArray(4), -1, ScalarType.of(8, signed = true))
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalar_bufferTooSmall_returnsBoundsError() {
        val r = KompactRuntime.readScalar(ByteArray(1), 0, ScalarType.of(16, signed = true))
        assertTrue(r.isFailure)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalarAsLong_bufferTooSmall_returnsBoundsError() {
        val r = KompactRuntime.readScalarAsLong(ByteArray(1), 0, ScalarType.of(16, signed = true))
        assertTrue(r.isFailure)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun readScalarAsLong_negativeBitOffset_returnsBoundsError() {
        val r = KompactRuntime.readScalarAsLong(ByteArray(8), -1, ScalarType.of(8, signed = true))
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }
}
