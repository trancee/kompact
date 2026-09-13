package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage-pinning tests for accessors that other test suites exercise only
 * indirectly: `packed`, `isFailure` on success, `error` on success.
 *
 * Each result value class's `getPacked()` getter (synthetic on JVM, direct
 * backing-field accessor on Native) is invoked here so JaCoCo/Kover records it
 * as covered. The `isFailure` and `error` accessors on *success* results
 * (returning `false` / `null`) close the remaining branch gaps.
 *
 * On the JVM, @JvmInline value-class property access is compiled to direct
 * GETFIELD (not INVOKEVIRTUAL getPacked()). To force a virtual call on the
 * getter we route the value through `Any` (forcing boxing) and invoke the
 * getter via java.lang.reflect. This is JVM-only coverage pinning.
 */
class KompactResultCoverageTest {
    // Helper: force a virtual call to getPacked() via reflection on a boxed
    // @JvmInline value class. Without this, the Kotlin compiler inlines the
    // property access to a direct field read, and the synthetic getter is
    // never recorded as covered by JaCoCo/Kover.
    private fun callGetPacked(value: Any): Long = value.javaClass.getMethod("getPacked").invoke(value) as Long

    private fun callGetPackedInt(value: Any): Int = value.javaClass.getMethod("getPacked").invoke(value) as Int

    // --- getPacked on every result type (covers synthetic backing-field getter) ---

    @Test
    fun byteResult_packedRoundTrips() {
        val r = ByteResult.success(0xAB.toByte())
        assertEquals(encodeSmallSuccess(0xAB.toByte().toLong()), callGetPacked(r))
    }

    @Test
    fun shortResult_packedRoundTrips() {
        val r = ShortResult.success(1000)
        assertEquals(encodeSmallSuccess(1000L), callGetPacked(r))
    }

    @Test
    fun intResult_packedRoundTrips() {
        val r = IntResult.success(42)
        assertEquals(encodeSmallSuccess(42L), callGetPacked(r))
    }

    @Test
    fun floatResult_packedRoundTrips() {
        val r = FloatResult.success(1.0f)
        assertTrue(callGetPacked(r) != 0L) // non-zero by construction
    }

    @Test
    fun booleanResult_packedRoundTrips() {
        assertEquals(encodeSmallSuccess(1L), callGetPacked(BooleanResult.success(true)))
        assertEquals(encodeSmallSuccess(0L), callGetPacked(BooleanResult.success(false)))
    }

    @Test
    fun booleanResult_failure_packedRoundTrips() {
        assertEquals(
            encodeSmallFailure(KompactDecodeError.BoundsError),
            callGetPacked(BooleanResult.failure(KompactDecodeError.BoundsError)),
        )
    }

    @Test
    fun longResult_packedRoundTrips() {
        assertEquals(42L, callGetPacked(LongResult.success(42L)))
    }

    @Test
    fun longResult_failure_packedRoundTrips() {
        assertEquals(
            encodeLongFailure(KompactDecodeError.BoundsError),
            callGetPacked(LongResult.failure(KompactDecodeError.BoundsError)),
        )
    }

    @Test
    fun doubleResult_packedRoundTrips() {
        val r = DoubleResult.success(3.14)
        assertEquals(3.14.toBits(), callGetPacked(r))
    }

    @Test
    fun doubleResult_failure_packedRoundTrips() {
        assertEquals(
            encodeDoubleFailure(KompactDecodeError.BoundsError),
            callGetPacked(DoubleResult.failure(KompactDecodeError.BoundsError)),
        )
    }

    // --- ScalarType.packed (jvmMain: backing Int of the @JvmInline) ---

    @Test
    fun scalarType_packedRoundTrips() {
        val st = ScalarType.of(8, signed = true)
        val expected = (8 shl 1) or 1 // bitWidth=8 → 0b10000, signed=1 → 0b10001 = 17
        assertEquals(expected, callGetPackedInt(st))
    }

    // --- NestedRegionResult.packed ---

    @Test
    fun nestedRegionResult_packedRoundTrips() {
        val r = NestedRegionResult.success(8, 8)
        assertTrue(callGetPacked(r) != 0L) // encoded with success flag
    }

    @Test
    fun nestedRegionResult_packedRoundTripsFailure() {
        val r = NestedRegionResult.failure(KompactDecodeError.BoundsError)
        assertEquals(
            encodeLongFailure(KompactDecodeError.BoundsError),
            callGetPacked(r),
        )
    }

    // --- isFailure = false + error = null on success (closes branch gaps) ---

    @Test
    fun allResultTypes_isFailureFalseAndErrorNullOnSuccess() {
        assertFalse(ByteResult.success(0).isFailure)
        assertFalse(ShortResult.success(0).isFailure)
        assertFalse(IntResult.success(0).isFailure)
        assertFalse(FloatResult.success(0f).isFailure)
        assertFalse(BooleanResult.success(false).isFailure)
        assertFalse(LongResult.success(0L).isFailure)
        assertFalse(DoubleResult.success(0.0).isFailure)
        assertFalse(NestedRegionResult.success(0, 0).isFailure)

        assertNull(ByteResult.success(0).error)
        assertNull(ShortResult.success(0).error)
        assertNull(IntResult.success(0).error)
        assertNull(FloatResult.success(0f).error)
        assertNull(BooleanResult.success(false).error)
        assertNull(LongResult.success(0L).error)
        assertNull(DoubleResult.success(0.0).error)
        assertNull(NestedRegionResult.success(0, 0).error)
    }

    // --- isFailure = true on failure (ShortResult was missing this) ---

    @Test
    fun shortResult_isFailureTrueOnFailure() {
        val r = ShortResult.failure(KompactDecodeError.BoundsError)
        assertTrue(r.isFailure)
    }

    @Test
    fun floatResult_isFailureTrueOnFailure() {
        val r = FloatResult.failure(KompactDecodeError.BoundsError)
        assertTrue(r.isFailure)
    }

    // --- ShortResult.getOrThrow on failure (covered the throw path) ---

    @Test
    fun shortResult_getOrThrow_throwsOnFailure() {
        val r = ShortResult.failure(KompactDecodeError.BoundsError)
        val ex = assertFailsWith<KompactDecodeException> { r.getOrThrow() }
        assertEquals(KompactDecodeError.BoundsError, ex.error)
    }

    // --- ByteResult.getOrThrow on failure (verify throwDecodeErrorFromSmallBits) ---

    @Test
    fun byteResult_getOrThrow_throwsOnFailure() {
        val r = ByteResult.failure(KompactDecodeError.UnknownEnumCode(42))
        val ex = assertFailsWith<KompactDecodeException> { r.getOrThrow() }
        assertEquals(KompactDecodeError.UnknownEnumCode(42), ex.error)
    }
}
