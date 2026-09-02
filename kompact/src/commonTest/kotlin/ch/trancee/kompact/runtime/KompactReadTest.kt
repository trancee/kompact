package ch.trancee.kompact.runtime

import ch.trancee.kompact.result.KompactError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec: .scratch/kompact-spec/issues/06-validation-model.md
 *        .scratch/kompact-spec/issues/08-runtime-error-model.md
 *
 * Checked read accessors bounds-check then read via the raw readBits
 * primitive; return a typed result. Never throw.
 */
class KompactReadTest {

    @Test
    fun readUInt8_within_bounds() {
        val buf = byteArrayOf(0x42, 0x00)
        val r = KompactRead.readUInt8(buf, bitOffset = 0)
        assertTrue(r.isOk)
        assertEquals(0x42, r.value)
    }

    @Test
    fun readUInt8_out_of_bounds_returns_error() {
        val buf = byteArrayOf(0x42)
        val r = KompactRead.readUInt8(buf, bitOffset = 1)
        assertFalse(r.isOk)
        assertEquals(KompactError.BoundsError, r.errorCode)
    }

    @Test
    fun readInt4_signed_two_complement() {
        val buf = byteArrayOf(0x0B)
        val r = KompactRead.readInt4(buf, bitOffset = 0)
        assertTrue(r.isOk)
        assertEquals(-5, r.value)
    }

    @Test
    fun readInt4_positive() {
        val buf = byteArrayOf(0x05)
        val r = KompactRead.readInt4(buf, bitOffset = 0)
        assertTrue(r.isOk)
        assertEquals(5, r.value)
    }

    @Test
    fun readInt8_negative_byte() {
        // 0xFF in 8 bits signed = -1.
        val buf = byteArrayOf(0xFF.toByte())
        val r = KompactRead.readInt8(buf, bitOffset = 0)
        assertTrue(r.isOk)
        assertEquals(-1, r.value)
    }

    @Test
    fun readBool_returns_typed_result() {
        val buf = byteArrayOf(0x01)
        val r = KompactRead.readBool(buf, bitOffset = 0)
        assertTrue(r.isOk)
        assertEquals(true, r.value)
    }

    @Test
    fun readUInt16_little_endian() {
        // 0x1234 little-endian = [0x34, 0x12]
        val buf = byteArrayOf(0x34, 0x12)
        val r = KompactRead.readUInt16(buf, bitOffset = 0)
        assertTrue(r.isOk)
        assertEquals(0x1234, r.value)
    }

    @Test
    fun readBitsRaw_bypasses_bounds_check() {
        val buf = byteArrayOf(0xA5.toByte(), 0x00)
        assertEquals(0xA5, KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 8))
        val r = KompactRead.readUInt8(buf, bitOffset = 0)
        assertTrue(r.isOk)
        assertEquals(0xA5, r.value)
    }
}
