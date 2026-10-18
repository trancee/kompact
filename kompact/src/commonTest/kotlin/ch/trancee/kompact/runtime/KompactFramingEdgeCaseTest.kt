package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Edge-case tests for KompactFraming error paths not covered by KompactFramingTest
 * or KompactRuntimeCheckedApiTest:
 *
 *  - readLengthPrefix with overrun (valid width, buffer too small) → INVALID_LENGTH_PREFIX
 *  - readLengthPrefix / readNested / readLengthPrefixOrThrow / writeLengthPrefix with an
 *    invalid caller width (7) → IllegalArgumentException
 *  - readLengthPrefixOrThrow with overrun → KompactDecodeException
 */
class KompactFramingEdgeCaseTest {
    // --- readLengthPrefix: invalid width and overrun paths ---

    @Test
    fun readLengthPrefix_invalidWidth_throwsIllegalArgument() {
        assertFailsWith<IllegalArgumentException> {
            KompactFraming.readLengthPrefix(ByteArray(4), 0, 7)
        }
    }

    @Test
    fun readLengthPrefix_validWidthButOverrun_returnsInvalidPrefix() {
        // 16-bit prefix on a 1-byte buffer → doesn't fit → INVALID_LENGTH_PREFIX
        assertEquals(
            KompactFraming.INVALID_LENGTH_PREFIX,
            KompactFraming.readLengthPrefix(ByteArray(1), 0, 16),
        )
    }

    // --- writeLengthPrefix: invalid width throws ---

    @Test
    fun writeLengthPrefix_invalidWidth_throws() {
        assertFailsWith<IllegalArgumentException> {
            KompactFraming.writeLengthPrefix(ByteArray(4), 0, 7, 1)
        }
    }

    @Test
    fun writeLengthPrefix_negativeValue_throwsWithoutMutation() {
        val raw = byteArrayOf(0x55, 0x66)
        val original = raw.copyOf()
        assertFailsWith<IllegalArgumentException> {
            KompactFraming.writeLengthPrefix(raw, 0, 16, -1)
        }
        assertContentEquals(original, raw)
    }

    @Test
    fun writeLengthPrefix_acceptsMaximum8BitValue() {
        val raw = ByteArray(1)
        KompactFraming.writeLengthPrefix(raw, 0, 8, 255)
        assertEquals(255, KompactFraming.readLengthPrefix(raw, 0, 8))
    }

    @Test
    fun writeLengthPrefix_acceptsMaximum16BitValue() {
        val raw = ByteArray(2)
        KompactFraming.writeLengthPrefix(raw, 0, 16, 65_535)
        assertEquals(65_535, KompactFraming.readLengthPrefix(raw, 0, 16))
    }

    @Test
    fun writeLengthPrefix_acceptsMaximum32BitApiValue() {
        val raw = ByteArray(4)
        KompactFraming.writeLengthPrefix(raw, 0, 32, Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, KompactFraming.readLengthPrefix(raw, 0, 32))
    }

    // --- readNested: invalid prefix width is a caller-contract violation ---

    @Test
    fun readNested_invalidPrefixWidth_throwsIllegalArgument() {
        val buf = byteArrayOf(1, 0, 0)
        assertFailsWith<IllegalArgumentException> { KompactFraming.readNested(buf, 0, 7) }
    }

    @Test
    fun readNested_prefixOverrun_returnsBadLengthPrefix() {
        assertEquals(KompactDecodeError.BadLengthPrefix, KompactFraming.readNested(ByteArray(1), 0, 16).error)
    }

    // --- readLengthPrefixOrThrow: overrun path ---

    @Test
    fun readLengthPrefixOrThrow_overrun_throwsTyped() {
        val ex =
            assertFailsWith<KompactDecodeException> {
                // 16-bit prefix on a 1-byte buffer
                KompactFraming.readLengthPrefixOrThrow(ByteArray(1), 0, 16)
            }
        assertEquals(KompactDecodeError.BadLengthPrefix, ex.error)
    }

    @Test
    fun readLengthPrefixOrThrow_invalidWidth_throwsIllegalArgument() {
        assertFailsWith<IllegalArgumentException> {
            KompactFraming.readLengthPrefixOrThrow(ByteArray(4), 0, 7)
        }
    }
}
