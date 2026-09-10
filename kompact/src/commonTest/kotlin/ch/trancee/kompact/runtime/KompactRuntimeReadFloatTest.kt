package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KompactRuntimeReadFloatTest {
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
}
