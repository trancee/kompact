package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Edge-case tests for the internal error-decode helpers — the `else` branches
 * in `decodeErrorFromSmallBits`, `decodePackedError`, and `decodeDoubleError`
 * that map unknown error kinds (4–7) to `BoundsError` (defensive default).
 *
 * The public `failure(error)` APIs always encode kinds 0–4, so the `else`
 * branch is unreachable from the normal API surface. We construct raw packed
 * values directly to exercise the defensive fallback.
 */
class KompactResultDecoderTest {
    // --- decodeErrorFromSmallBits else branch (kind 5-7 → BoundsError) ---

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
        val unknownPacked = 5L shl RESULT_ERROR_KIND_SHIFT
        val r = BooleanResult(unknownPacked)
        assertEquals(false, r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun nestedRegionResult_constructedWithUnknownErrorKind_decodesAsBoundsError() {
        val unknownPacked = encodePackedFailure(KompactDecodeError.BoundsError) or 5L
        val r = NestedRegionResult(unknownPacked)
        assertEquals(false, r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun nestedRegionResult_failurePreservesUnknownEnumCode() {
        val result = NestedRegionResult.failure(KompactDecodeError.UnknownEnumCode(42))
        assertEquals(KompactDecodeError.UnknownEnumCode(42), result.error)
    }

    // --- decodeDoubleError else branch (payload kind 5-7 → BoundsError) ---

    @Test
    fun doubleResult_constructedWithUnknownErrorKind_decodesAsBoundsError() {
        // DoubleResult failure layout: canonical NaN | (kind + 1) in bits 3..0.
        // kind = 5 → payload = 6 → packed = DOUBLE_NAN_CANONICAL or 6
        val unknownPacked = DOUBLE_NAN_CANONICAL or 6L
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

    // --- throwDecodeErrorFromDouble ---
    @Test
    fun doubleResult_unknownKind_getOrThrow_throwsBoundsError() {
        val unknownPacked = DOUBLE_NAN_CANONICAL or 7L
        val r = DoubleResult(unknownPacked)
        val ex = assertFailsWith<KompactDecodeException> { r.getOrThrow() }
        assertEquals(KompactDecodeError.BoundsError, ex.error)
    }
}
