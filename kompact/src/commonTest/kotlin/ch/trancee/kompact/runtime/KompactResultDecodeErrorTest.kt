package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The per-shape failure decoders (small / packed-region / Double-NaN) all
 * route through one internal `decodeError(kind)` mapper so the kind->case
 * mapping cannot drift. These tests pin the mapper and each shape's
 * defensive fallback for kinds no public `failure(...)` call can produce.
 */
class KompactResultDecodeErrorTest {
    @Test
    fun decodeError_mapsEveryKnownKind() {
        assertEquals(KompactDecodeError.BoundsError, decodeError(ERROR_BOUNDS))
        assertEquals(KompactDecodeError.BadLengthPrefix, decodeError(ERROR_BAD_LENGTH))
        assertEquals(KompactDecodeError.TruncatedNested, decodeError(ERROR_TRUNCATED))
        assertEquals(KompactDecodeError.InvalidUtf8, decodeError(ERROR_INVALID_UTF8))
    }

    @Test
    fun decodeError_unrecognizedKind_defaultsToBoundsError() {
        assertEquals(KompactDecodeError.BoundsError, decodeError(99))
    }

    @Test
    fun everyErrorRoundTripsThroughEveryPackedShape() {
        val errors =
            listOf(
                KompactDecodeError.BoundsError,
                KompactDecodeError.BadLengthPrefix,
                KompactDecodeError.TruncatedNested,
                KompactDecodeError.InvalidUtf8,
            )

        for (error in errors) {
            assertEquals(error, IntResult.failure(error).error)
            assertEquals(error, FloatResult.failure(error).error)
            assertEquals(error, BooleanResult.failure(error).error)
            assertEquals(error, DoubleResult.failure(error).error)
            assertEquals(error, NestedRegionResult.failure(error).error)
        }
    }

    @Test
    fun unknownKindsInEachPackedShapeDecodeAsBoundsError() {
        assertEquals(KompactDecodeError.BoundsError, decodeErrorFromSmallBits(7L shl RESULT_ERROR_KIND_SHIFT))
        assertEquals(KompactDecodeError.BoundsError, decodePackedError(PACKED_FAILURE_BASE or 5L))
        assertEquals(KompactDecodeError.BoundsError, decodeDoubleError(DOUBLE_NAN_CANONICAL or 15L))
    }
}
