package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ADR-0005 two-tier diagnostics path: `decodeFull*` is opt-in and may allocate,
 * while `readScalar`/`readScalarAsLong`/`readFloat`/`readDouble`/`readBool`
 * (the hot path) stay zero-alloc (Tickets 03/10).
 *
 * Each `decodeFull*` pins one behavior per scalar shape and reuses the matching
 * zero-alloc `read*` on success (so success semantics are identical by
 * construction). On failure it returns a [DetailedResult] carrying a
 * [DetailedDecodeError] with the byte offset of the failure
 * (`bitOffset ushr 3`) — the offset ticket 08 deliberately omitted from the
 * fast path. `decodeFull*` is the opt-in diagnostics path, so it (and its
 * Double success) may box; that is out of the zero-alloc contract.
 */
class KompactResultDecodeFullTest {
    // === Int (<=32-bit, via ScalarType) — wraps readScalar ===

    @Test
    fun decodeFullInt_returnsDecodedValueOnSuccess() {
        // 8-bit signed read of 0x7F -> 127
        val raw = byteArrayOf(0x7F)

        val r = KompactRuntime.decodeFullInt(raw, bitOffset = 0, ScalarType.of(8, signed = true))

        assertTrue(r.isSuccess)
        assertFalse(r.isFailure)
        assertNull(r.error)
        assertEquals(127, r.value)
    }

    @Test
    fun decodeFullInt_returnsBoundsErrorWithByteOffsetOnOutOfBounds() {
        val raw = byteArrayOf(0x00, 0x00) // 16 bits; bitOffset 64 overreads by 56 bits

        val r = KompactRuntime.decodeFullInt(raw, bitOffset = 64, ScalarType.of(8, signed = true))

        assertTrue(r.isFailure)
        assertFalse(r.isSuccess)
        assertNull(r.value)
        val err = assertNotNull(r.error, "expected a DetailedDecodeError on failure")
        assertEquals(KompactDecodeError.BoundsError, err.error)
        assertEquals(8, err.offset) // 64 bits / 8 = byte offset 8
        assertEquals(0, err.rawCode)
    }

    // === Long (64-bit, via ScalarType) — wraps readScalarAsLong ===

    @Test
    fun decodeFullLong_returnsDecodedValueOnSuccess() {
        // 8-bit unsigned read -> 42L
        val raw = byteArrayOf(0x2A)

        val r = KompactRuntime.decodeFullLong(raw, bitOffset = 0, ScalarType.of(8, signed = false))

        assertTrue(r.isSuccess)
        assertFalse(r.isFailure)
        assertNull(r.error)
        assertEquals(42L, r.value)
    }

    @Test
    fun decodeFullLong_returnsBoundsErrorWithByteOffsetOnOutOfBounds() {
        val raw = byteArrayOf(0x00) // 8 bits; bitOffset 32 overreads by 24 bits

        val r = KompactRuntime.decodeFullLong(raw, bitOffset = 32, ScalarType.of(8, signed = false))

        assertTrue(r.isFailure)
        assertFalse(r.isSuccess)
        assertNull(r.value)
        val err = assertNotNull(r.error, "expected a DetailedDecodeError on failure")
        assertEquals(KompactDecodeError.BoundsError, err.error)
        assertEquals(4, err.offset) // 32/8
        assertEquals(0, err.rawCode)
    }

    // === Float (32-bit) — wraps readFloat ===

    @Test
    fun decodeFullFloat_returnsDecodedValueOnSuccess() {
        // 1.0f == 0x3F800000; little-endian bytes => [0x00, 0x00, 0x80, 0x3F]
        val raw = byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F)

        val r = KompactRuntime.decodeFullFloat(raw, bitOffset = 0)

        assertTrue(r.isSuccess)
        assertFalse(r.isFailure)
        assertNull(r.error)
        assertEquals(1.0f, r.value)
    }

    @Test
    fun decodeFullFloat_returnsBoundsErrorWithByteOffsetOnOutOfBounds() {
        val raw = byteArrayOf(0x00, 0x00) // 16 bits; need 32

        val r = KompactRuntime.decodeFullFloat(raw, bitOffset = 0)

        assertTrue(r.isFailure)
        assertFalse(r.isSuccess)
        assertNull(r.value)
        val err = assertNotNull(r.error, "expected a DetailedDecodeError on failure")
        assertEquals(KompactDecodeError.BoundsError, err.error)
        assertEquals(0, err.offset)
        assertEquals(0, err.rawCode)
    }

    // === Double (64-bit) — wraps readDouble; success may box (opt-in path) ===

    @Test
    fun decodeFullDouble_returnsDecodedValueOnSuccess() {
        // 1.0 == 0x3FF0000000000000; little-endian bytes => [0,0,0,0,0,0,0xF0,0x3F]
        val raw = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0.toByte(), 0x3F)

        val r = KompactRuntime.decodeFullDouble(raw, bitOffset = 0)

        assertTrue(r.isSuccess)
        assertFalse(r.isFailure)
        assertNull(r.error)
        val value = assertNotNull(r.value, "on success the value must be present")
        assertEquals(1.0, value, 0.0)
    }

    @Test
    fun decodeFullDouble_returnsBoundsErrorWithByteOffsetOnOutOfBounds() {
        val raw = byteArrayOf(0x00, 0x00) // 16 bits; need 64

        val r = KompactRuntime.decodeFullDouble(raw, bitOffset = 0)

        assertTrue(r.isFailure)
        assertFalse(r.isSuccess)
        assertNull(r.value)
        val err = assertNotNull(r.error, "expected a DetailedDecodeError on failure")
        assertEquals(KompactDecodeError.BoundsError, err.error)
        assertEquals(0, err.offset)
        assertEquals(0, err.rawCode)
    }

    // === Boolean (1-bit) — wraps readBool ===

    @Test
    fun decodeFullBoolean_returnsTrueOnSetBit() {
        val raw = byteArrayOf(0x01)

        val r = KompactRuntime.decodeFullBoolean(raw, bitOffset = 0)

        assertTrue(r.isSuccess)
        assertFalse(r.isFailure)
        assertNull(r.error)
        assertEquals(true, r.value)
    }

    @Test
    fun decodeFullBoolean_returnsBoundsErrorWithByteOffsetOnOutOfBounds() {
        val raw = byteArrayOf(0x00) // 8 bits; bitOffset 8 overreads by 7 bits

        val r = KompactRuntime.decodeFullBoolean(raw, bitOffset = 8)

        assertTrue(r.isFailure)
        assertFalse(r.isSuccess)
        assertNull(r.value)
        val err = assertNotNull(r.error, "expected a DetailedDecodeError on failure")
        assertEquals(KompactDecodeError.BoundsError, err.error)
        assertEquals(1, err.offset) // 8/8
        assertEquals(0, err.rawCode)
    }
}
