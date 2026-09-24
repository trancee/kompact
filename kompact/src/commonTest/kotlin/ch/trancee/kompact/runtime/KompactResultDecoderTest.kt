package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Edge-case tests for the internal error-decode helpers — the `else` branches
 * in `decodeErrorFromSmallBits`, `decodeLongError`, and `decodeDoubleError`
 * that map unknown error kinds (4–7) to `BoundsError` (defensive default).
 *
 * The public `failure(error)` APIs always encode kinds 0–3, so the `else`
 * branch is unreachable from the normal API surface. We construct raw packed
 * values directly to exercise the defensive fallback.
 */
class KompactResultDecoderTest {
    // --- decodeErrorFromSmallBits else branch (kind 4-7 → BoundsError) ---

    @Test
    fun intResult_constructedWithUnknownErrorKind_decodesAsBoundsError() {
        val unknownPacked = 6L shl RESULT_ERROR_KIND_SHIFT
        val r = IntResult(unknownPacked)
        assertEquals(false, r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun floatResult_constructedWithUnknownErrorKind_decodesAsBoundsError() {
        val unknownPacked = 7L shl RESULT_ERROR_KIND_SHIFT
        val r = FloatResult(unknownPacked)
        assertEquals(false, r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun booleanResult_constructedWithUnknownErrorKind_decodesAsBoundsError() {
        val unknownPacked = 4L shl RESULT_ERROR_KIND_SHIFT
        val r = BooleanResult(unknownPacked)
        assertEquals(false, r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    // --- decodeLongError else branch (kind 4-7 → BoundsError) ---

    @Test
    fun longResult_constructedWithUnknownErrorKind_decodesAsBoundsError() {
        // LongResult failure layout: Long.MIN_VALUE | kind (kind in bits 2..0).
        val unknownPacked = encodeLongFailure(KompactDecodeError.BoundsError) or 4L
        val r = LongResult(unknownPacked)
        assertEquals(false, r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun nestedRegionResult_constructedWithUnknownErrorKind_decodesAsBoundsError() {
        val unknownPacked = encodeLongFailure(KompactDecodeError.BoundsError) or 5L
        val r = NestedRegionResult(unknownPacked)
        assertEquals(false, r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    // --- decodeDoubleError else branch (payload kind 4-7 → BoundsError) ---

    @Test
    fun doubleResult_constructedWithUnknownErrorKind_decodesAsBoundsError() {
        // DoubleResult failure layout: canonical NaN | (kind + 1) in bits 3..0.
        // kind = 4 → payload = 5 → packed = DOUBLE_NAN_CANONICAL or 5
        val unknownPacked = DOUBLE_NAN_CANONICAL or 5L
        val r = DoubleResult(unknownPacked)
        assertEquals(false, r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun doubleResult_constructedWithMaxUnknownErrorKind_decodesAsBoundsError() {
        val unknownPacked = DOUBLE_NAN_CANONICAL or 15L
        val r = DoubleResult(unknownPacked)
        assertEquals(false, r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    // --- throwDecodeErrorFromLong / Double ---

    @Test
    fun longResult_unknownKind_getOrThrow_throwsBoundsError() {
        val unknownPacked = encodeLongFailure(KompactDecodeError.BoundsError) or 6L
        val r = LongResult(unknownPacked)
        val ex = assertFailsWith<KompactDecodeException> { r.getOrThrow() }
        assertEquals(KompactDecodeError.BoundsError, ex.error)
    }

    @Test
    fun doubleResult_unknownKind_getOrThrow_throwsBoundsError() {
        val unknownPacked = DOUBLE_NAN_CANONICAL or 7L
        val r = DoubleResult(unknownPacked)
        val ex = assertFailsWith<KompactDecodeException> { r.getOrThrow() }
        assertEquals(KompactDecodeError.BoundsError, ex.error)
    }
}
