package ch.trancee.kompact.runtime

import ch.trancee.kompact.result.KompactError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec: .scratch/kompact-spec/issues/06-validation-model.md
 *
 * Fail-closed contracts: when a `widthBits` is outside the supported
 * set {8, 16, 32}, the length-prefix read/write APIs must return a
 * typed [KompactError.BoundsError] rather than silently misread or
 * write garbage. These tests pin that contract.
 */
class KompactReadWidthBitsTest {

    @Test
    fun writeLengthPrefix_invalid_width_returns_BoundsError() {
        val buf = ByteArray(8)
        val r = KompactRead.writeLengthPrefix(buf, bitOffset = 0, widthBits = 12, length = 1)
        assertFalse(r.isOk)
        assertEquals(KompactError.BoundsError, r.errorCode)
    }

    @Test
    fun writeLengthPrefix_zero_width_returns_BoundsError() {
        val buf = ByteArray(8)
        val r = KompactRead.writeLengthPrefix(buf, bitOffset = 0, widthBits = 0, length = 1)
        assertFalse(r.isOk)
        assertEquals(KompactError.BoundsError, r.errorCode)
    }

    @Test
    fun writeLengthPrefix_three_is_rejected() {
        val buf = ByteArray(8)
        val r = KompactRead.writeLengthPrefix(buf, bitOffset = 0, widthBits = 3, length = 1)
        assertFalse(r.isOk)
        assertEquals(KompactError.BoundsError, r.errorCode)
    }

    @Test
    fun writeLengthPrefix_eight_succeeds() {
        val buf = ByteArray(8)
        val r = KompactRead.writeLengthPrefix(buf, bitOffset = 0, widthBits = 8, length = 5)
        assertTrue(r.isOk)
        assertEquals(8, r.value)
    }

    @Test
    fun writeLengthPrefix_thirty_two_succeeds() {
        val buf = ByteArray(8)
        val r = KompactRead.writeLengthPrefix(buf, bitOffset = 0, widthBits = 32, length = 7)
        assertTrue(r.isOk)
        assertEquals(32, r.value)
    }
}
