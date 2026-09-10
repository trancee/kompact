package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KompactRuntimeReadBoolTest {
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
}
