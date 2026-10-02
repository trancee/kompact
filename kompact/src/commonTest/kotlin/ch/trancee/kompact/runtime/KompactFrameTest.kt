package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KompactFrameTest {
    @Test
    fun framedValuesShareBufferAndAdvanceAcrossFields() {
        val writer = KompactWriter()
        writer.writeString(8, "é")
        writer.writeBlob(8, byteArrayOf(7, 8))
        writer.writeNested(8) { writeBitsLong(8, 42) }
        val raw = writer.build()
        val frame = KompactFrame.decode(raw).getOrThrow()

        val text = frame.readString(8)
        val blob = frame.readBlob(8)
        val nested = frame.readNested(8)
        frame.requireComplete()

        assertEquals("é", text)
        assertSame(raw, blob.raw)
        assertContentEquals(byteArrayOf(7, 8), blob.toByteArray())
        assertSame(raw, nested.raw)
        assertEquals(42L, KompactFrame.decode(nested.raw, nested.start, nested.end).getOrThrow().readBits(8))
    }

    @Test
    fun repeatedViewsDecodeOnAccessAndPreserveFollowingField() {
        val writer = KompactWriter()
        writer.writeRepeated(2, 8) {
            writeString(8, if (bitCursor == 8) "a" else "b")
        }
        writer.writeBool(true)
        val frame = KompactFrame.decode(writer.build()).getOrThrow()

        val values = frame.readRepeated(8, 0, 8) { element -> element.readString(8) }
        val after = frame.readBits(1)
        frame.requireComplete()

        assertEquals(listOf("a", "b"), values)
        assertEquals(1L, after)
    }

    @Test
    fun malformedPrefixAndBoundsAreTyped() {
        val result = KompactFrame.decode(byteArrayOf(3, 1)) { frame -> frame.readBlob(8) }

        assertEquals(KompactDecodeError.BadLengthPrefix, result.error)
        assertEquals(KompactDecodeError.BoundsError, KompactFrame.decode(byteArrayOf(), 1, 2).error)
    }

    @Test
    fun truncatedRepeatedElementAndTrailingDataAreTyped() {
        val truncated =
            KompactFrame.decode(byteArrayOf(2, 1)) { frame ->
                frame.readRepeated(8, 8, 0) { element -> element.readBits(8) }
            }
        val trailing =
            KompactFrame.decode(byteArrayOf(1, 2)) { frame ->
                frame.readBits(8)
                frame.requireComplete()
            }

        assertEquals(KompactDecodeError.TruncatedNested, truncated.error)
        assertEquals(KompactDecodeError.BoundsError, trailing.error)
    }

    @Test
    fun nestedBoundCannotReadIntoNextField() {
        val result =
            KompactFrame.decode(byteArrayOf(1, 5, 99)) { frame ->
                val nested = frame.readNested(8)
                KompactFrame.decode(nested.raw, nested.start, nested.end).getOrThrow().readBits(16)
            }

        assertTrue(result.error is KompactDecodeError.BoundsError)
    }

    @Test
    fun malformedUtf8AndNonzeroTailPaddingReturnTypedErrors() {
        val utf8 = KompactFrame.decode(byteArrayOf(1, 0xFF.toByte())) { it.readString(8) }
        val padding =
            KompactFrame.decode(byteArrayOf(0xFE.toByte())) {
                it.readBits(1)
                it.requireComplete()
            }

        assertEquals(KompactDecodeError.InvalidUtf8, utf8.error)
        assertEquals(KompactDecodeError.BoundsError, padding.error)
    }

    @Test
    fun decodeBlockRejectsUnreadTrailingData() {
        val result =
            KompactFrame.decode(byteArrayOf(1, 2)) {
                it.readBits(8)
            }

        assertEquals(KompactDecodeError.BoundsError, result.error)
    }

    @Test
    fun malformedRepeatedPayloadReportsTypedErrorWhenAccessed() {
        val view =
            KompactFrame.decode(byteArrayOf(1, 1, 0xFF.toByte())) {
                it.readRepeated(8, 0, 8) { element -> element.readString(8) }
            }.getOrThrow()

        val result = view.getResult(0)

        assertEquals(KompactDecodeError.InvalidUtf8, result.error)
    }

    @Test
    fun mutatedRepeatedPrefixReturnsTypedFailure() {
        val raw = byteArrayOf(2, 1, 'a'.code.toByte(), 1, 'b'.code.toByte())
        val view =
            KompactFrame.decode(raw) {
                it.readRepeated(8, 0, 8) { element -> element.readString(8) }
            }.getOrThrow()
        raw[1] = 0xFF.toByte()

        val result = view.getResult(1)

        assertEquals(KompactDecodeError.BadLengthPrefix, result.error)
    }

    @Test
    fun mutatedRepeatedPrefixCannotReadIntoFollowingField() {
        val raw = byteArrayOf(1, 1, 'a'.code.toByte(), 0x7F)
        val view =
            KompactFrame.decode(raw, end = 3) {
                it.readRepeated(8, 0, 8) { element -> element.readBlob(8).toByteArray() }
            }.getOrThrow()
        raw[1] = 2

        val result = view.getResult(0)

        assertEquals(KompactDecodeError.BadLengthPrefix, result.error)
    }

    @Test
    fun mutatedThirtyTwoBitRepeatedPrefixReturnsTypedFailure() {
        val raw = byteArrayOf(1, 1, 0, 0, 0, 'a'.code.toByte())
        val view =
            KompactFrame.decode(raw) {
                it.readRepeated(8, 0, 32) { element -> element }
            }.getOrThrow()
        raw[1] = 0xFF.toByte()
        raw[2] = 0xFF.toByte()
        raw[3] = 0xFF.toByte()
        raw[4] = 0xFF.toByte()

        val result = view.getResult(0)

        assertEquals(KompactDecodeError.BadLengthPrefix, result.error)
    }

    @Test
    fun repeatedVariableElementSliceBorrowsOriginalBuffer() {
        val raw = byteArrayOf(1, 2, 8, 9)
        val view =
            KompactFrame.decode(raw) {
                it.readRepeated(8, 0, 8) { element -> element.readBlob(8).toByteArray() }
            }.getOrThrow()

        val slice = view.getElementSlice(0).getOrThrow()

        assertSame(raw, slice.raw)
        assertEquals(2, slice.start)
        assertContentEquals(byteArrayOf(8, 9), slice.toByteArray())
    }

    @Test
    fun fixedWidthRepeatCannotBeReadAsLengthPrefixedSlice() {
        val view =
            KompactFrame.decode(byteArrayOf(1, 0)) {
                it.readRepeated(8, 8, 0) { element -> element.readBits(8) }
            }.getOrThrow()

        val result = view.getElementSlice(0)

        assertEquals(KompactDecodeError.BoundsError, result.error)
    }

    @Test
    fun repeatedViewIndexesAcrossSparseCheckpoint() {
        val writer = KompactWriter()
        var position = 0
        writer.writeRepeated(65, 8) {
            writeString(8, if (position++ == 64) "last" else "")
        }
        val frame = KompactFrame.decode(writer.build()).getOrThrow()

        val values = frame.readRepeated(8, 0, 8) { it.readString(8) }
        frame.requireComplete()

        assertEquals(65, values.size)
        assertEquals("", values[63])
        assertEquals("last", values[64])
        assertFailsWith<IndexOutOfBoundsException> { values[65] }
        assertFailsWith<IndexOutOfBoundsException> { values[-1] }
    }

    @Test
    fun emptyVariableRepeatHasNoCheckpoints() {
        val frame = KompactFrame.decode(byteArrayOf(0)).getOrThrow()

        val values = frame.readRepeated(8, 0, 8) { it.readString(8) }
        frame.requireComplete()

        assertTrue(values.isEmpty())
    }

    @Test
    fun malformedPrefixesAndRepeatedShapesReturnTypedFailures() {
        val malformed =
            listOf(
                KompactFrame.decode(byteArrayOf()) { it.readString(8) },
                KompactFrame.decode(byteArrayOf(1)) { it.readBlob(7) },
                KompactFrame.decode(byteArrayOf(1)) { it.readNested(16) },
                KompactFrame.decode(byteArrayOf(1, 0)) { it.readRepeated(8, 0, 0) { 1 } },
                KompactFrame.decode(byteArrayOf(1, 0)) { it.readRepeated(8, 65, 0) { 1 } },
                KompactFrame.decode(byteArrayOf(1, 0)) { it.readRepeated(8, -1, 0) { 1 } },
                KompactFrame.decode(byteArrayOf(1, 0)) { it.readRepeated(8, 0, 7) { 1 } },
                KompactFrame.decode(byteArrayOf(1, 0)) { it.readRepeated(8, 0, -1) { 1 } },
                KompactFrame.decode(byteArrayOf(1, 0)) { it.readRepeated(8, 8, 8) { 1 } },
                KompactFrame.decode(byteArrayOf(2, 1)) { it.readRepeated(8, 0, 8) { 1 } },
                KompactFrame.decode(byteArrayOf(1, 1)) { it.readRepeated(8, 0, 8) { 1 } },
                KompactFrame.decode(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())) {
                    it.readRepeated(32, 8, 0) { 1 }
                },
            )

        assertTrue(malformed.all { it.error != null })
    }

    @Test
    fun unalignedRepeatedVariablePayloadIsTyped() {
        val decoded =
            KompactFrame.decode(byteArrayOf(2, 0, 0)) {
                it.readBits(1)
                it.readRepeated(8, 0, 8) { element -> element.readBlob(8) }
            }

        assertEquals(KompactDecodeError.TruncatedNested, decoded.error)
    }

    @Test
    fun invalidBoundedRegionsAndScalarWidthsAreTyped() {
        val raw = byteArrayOf(0)
        val failures =
            listOf(
                KompactFrame.decode(raw, -1, 1),
                KompactFrame.decode(raw, 1, 0),
                KompactFrame.decode(raw, 0, 2),
                KompactFrame.decode(raw) { it.readBits(0) },
                KompactFrame.decode(raw) { it.readBits(65) },
                KompactFrame.decode(raw) { it.readBits(16) },
            )

        assertTrue(failures.all { it.error == KompactDecodeError.BoundsError })
    }

    @Test
    fun oversizedFrameReturnsTypedBoundsFailure() {
        val oversized = ByteArray(Int.MAX_VALUE / 8 + 1)

        val result = KompactFrame.decode(oversized)

        assertEquals(KompactDecodeError.BoundsError, result.error)
    }

    @Test
    fun repeatedScalarsReadAcrossByteBoundaryWithoutEagerValues() {
        val writer = KompactWriter()
        writer.writeRepeated(3, 8) { writeBool(bitCursor != 9) }
        val frame = KompactFrame.decode(writer.build()).getOrThrow()

        val view = frame.readRepeated(8, 1, 0) { it.readBits(1) != 0L }
        frame.requireComplete()

        assertEquals(3, view.size)
        assertEquals(listOf(true, false, true), view)
        assertEquals(null, view.getResult(1).error)
    }

    @Test
    fun invalidPrefixAndUnalignedPayloadAreTyped() {
        val unreadable =
            KompactFrame.decode(byteArrayOf(-1, -1, -1, -1)) {
                it.readBlob(32)
            }
        val unaligned =
            KompactFrame.decode(byteArrayOf(0, 0)) {
                it.readBits(1)
                it.readBlob(8)
            }
        val failure = KompactFrameResult.Failure(KompactDecodeError.BadLengthPrefix)

        assertEquals(KompactDecodeError.BadLengthPrefix, unreadable.error)
        assertEquals(KompactDecodeError.BadLengthPrefix, unaligned.error)
        assertEquals(KompactDecodeError.BadLengthPrefix, assertFailsWith<KompactDecodeException> {
            failure.getOrThrow()
        }.error)
    }
}
