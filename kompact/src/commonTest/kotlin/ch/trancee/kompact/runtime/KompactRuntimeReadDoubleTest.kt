package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KompactRuntimeReadDoubleTest {

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
}
