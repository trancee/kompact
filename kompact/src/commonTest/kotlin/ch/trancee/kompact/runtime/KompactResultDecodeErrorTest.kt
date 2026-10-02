package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The three per-shape failure decoders (Ticket 08: small / Long-sentinel /
 * Double-NaN) each reconstruct a [KompactDecodeError] with the SAME kind->case
 * mapping. ADR-0005's simplification consolidates that mapping into one internal
 * `decodeError(kind, rawCode)` mapper so the kinds cannot drift apart.
 *
 * This pins the unified mapper directly; the existing `*Result` failure tests
 * pin each shape's bit-extraction, which now routes through it.
 */
class KompactResultDecodeErrorTest {
    @Test
    fun decodeError_boundsKind_mapsToBoundsError() {
        // Arrange
        val kind = ERROR_BOUNDS

        // Act
        val error = decodeError(kind, rawCode = 0)

        // Assert
        assertEquals(KompactDecodeError.BoundsError, error)
    }

    @Test
    fun decodeError_badLengthKind_mapsToBadLengthPrefix() {
        val kind = ERROR_BAD_LENGTH

        val error = decodeError(kind, rawCode = 0)

        assertEquals(KompactDecodeError.BadLengthPrefix, error)
    }

    @Test
    fun decodeError_truncatedKind_mapsToTruncatedNested() {
        val kind = ERROR_TRUNCATED

        val error = decodeError(kind, rawCode = 0)

        assertEquals(KompactDecodeError.TruncatedNested, error)
    }

    @Test
    fun decodeError_unknownEnumKind_preservesRawCode() {
        val kind = ERROR_UNKNOWN_ENUM

        val error = decodeError(kind, rawCode = 42)

        assertEquals(KompactDecodeError.UnknownEnumCode(42), error)
    }

    @Test
    fun decodeError_invalidUtf8Kind_mapsToInvalidUtf8() {
        val error = decodeError(ERROR_INVALID_UTF8, rawCode = 0)

        assertEquals(KompactDecodeError.InvalidUtf8, error)
    }

    @Test
    fun intResult_failurePreservesInvalidUtf8() {
        val result = IntResult.failure(KompactDecodeError.InvalidUtf8)

        assertEquals(KompactDecodeError.InvalidUtf8, result.error)
    }

    @Test
    fun longResult_formatsInvalidUtf8Failure() {
        val result = LongResult.failure(KompactDecodeError.InvalidUtf8)

        assertEquals("LongResult(error=InvalidUtf8)", result.toString())
    }

    @Test
    fun decodeError_unrecognizedKind_defaultsToBoundsError() {
        // 7-bit kind field can carry 0..7; 99 is outside the known set.
        val kind = 99

        val error = decodeError(kind, rawCode = 0)

        assertEquals(KompactDecodeError.BoundsError, error)
    }
}
