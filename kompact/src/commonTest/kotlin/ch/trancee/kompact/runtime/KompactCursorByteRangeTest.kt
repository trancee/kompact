package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KompactCursorByteRangeTest {
    @Test
    fun byteRangeResetAndCopyRejectInvalidBoundsWithoutChangingTheView() {
        val original = byteArrayOf(4, 5, 6)
        val range = KompactByteRange(original)

        assertFalse(range.reset(original, start = -1))
        assertFalse(range.reset(original, start = 2, end = 1))
        assertFalse(range.reset(original, end = 4))
        assertSame(original, range.buffer)
        assertEquals(0, range.start)
        assertEquals(3, range.end)

        assertFalse(range.copyTo(ByteArray(3), destinationOffset = -1))
        assertFalse(range.copyTo(ByteArray(2)))
        assertTrue(range.reset(original, start = 1, end = 3))
        val copied = ByteArray(4)
        assertTrue(range.copyTo(copied, destinationOffset = 1))
        assertContentEquals(byteArrayOf(0, 5, 6, 0), copied)
    }

    @Test
    fun rawRangePreflightAndCopyRejectAlignmentAliasingAndBounds() {
        val source = byteArrayOf(3, 7)
        val range = KompactByteRange(source)
        range.reset(source, start = 1, end = 2)
        val destinationBytes = ByteArray(2)
        val destination = KompactCursor(destinationBytes)

        assertEquals(KompactCursor.STATUS_OK, destination.preflightRawByteRange(range))
        assertEquals(8, destination.position)

        val aligned = KompactCursor(destinationBytes)
        assertEquals(KompactCursor.STATUS_INVALID_ARGUMENT, aligned.preflightRawByteRange(KompactByteRange(destinationBytes)))
        val unaligned = KompactCursor(destinationBytes)
        unaligned.reset(destinationBytes, position = 1)
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, unaligned.preflightRawByteRange(range))
        val tooSmall = KompactCursor(ByteArray(0))
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, tooSmall.writeRawByteRange(range))

        val output = ByteArray(2)
        val outputCursor = KompactCursor(output)
        assertEquals(KompactCursor.STATUS_OK, outputCursor.writeRawByteRange(range))
        assertContentEquals(byteArrayOf(7, 0), output)
    }

    @Test
    fun regionCaptureRequiresByteAlignmentAndOptionallyValidUtf8() {
        val source = byteArrayOf(0x41, 0xFF.toByte())
        val range = KompactByteRange(byteArrayOf(9))
        val unaligned = KompactCursor(source)
        unaligned.reset(source, startBit = 1, position = 1, endBit = 8)

        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, unaligned.captureRegion(range))
        val unalignedEnd = KompactCursor(source)
        unalignedEnd.reset(source, startBit = 0, position = 0, endBit = 9)
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, unalignedEnd.captureRegion(range))
        val invalidUtf8 = KompactCursor(source)
        assertEquals(KompactCursor.STATUS_INVALID_UTF8, invalidUtf8.captureRegion(range, validateUtf8 = true))
        val captured = KompactCursor(source)
        assertEquals(KompactCursor.STATUS_OK, captured.captureRegion(range))

        assertSame(source, range.buffer)
        assertEquals(0, range.start)
        assertEquals(2, range.end)
    }

    @Test
    fun nestedReadsRejectInvalidCursorsWidthsAlignmentAndTruncation() {
        val bytes = byteArrayOf(1, 0x44)
        val cursor = KompactCursor(bytes)
        val child = KompactCursor(ByteArray(0))

        assertEquals(KompactCursor.STATUS_INVALID_ARGUMENT, cursor.readNested(8, cursor))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.readNested(7, child))
        cursor.reset(bytes, position = 1)
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, cursor.readNested(8, child))
        cursor.reset(ByteArray(0))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.readNested(8, child))
        cursor.reset(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.readNested(32, child))
        cursor.reset(byteArrayOf(2, 0x55))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.readNested(8, child))
        cursor.reset(byteArrayOf(0))
        assertEquals(KompactCursor.STATUS_OK, cursor.readNested(8, child))
        assertEquals(8, cursor.position)
        assertEquals(child.position, child.endBit)
    }

    @Test
    fun nestedWritesPreflightLengthAlignmentAndCapacityBeforeMutation() {
        val bytes = ByteArray(3)
        val cursor = KompactCursor(bytes)
        val child = KompactCursor(ByteArray(0))

        assertEquals(KompactCursor.STATUS_INVALID_ARGUMENT, cursor.writeNested(8, 0, cursor))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.writeNested(7, 0, child))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.writeNested(8, -1, child))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.writeNested(8, 256, child))
        cursor.reset(bytes, position = 1)
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, cursor.writeNested(8, 0, child))
        val noSpace = KompactCursor(ByteArray(1))
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, noSpace.writeNested(16, 0, child))
        val bounded = KompactCursor(ByteArray(1))
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, bounded.writeNested(8, 1, child))

        val successful = KompactCursor(ByteArray(2))
        assertEquals(KompactCursor.STATUS_OK, successful.writeNested(8, 1, child))
        assertEquals(1, successful.buffer[0].toInt())
        assertEquals(16, child.endBit)
    }

    @Test
    fun prefixedReadsValidateWidthAlignmentLengthUtf8AndRangeAtomicity() {
        val previous = byteArrayOf(0x66)
        val range = KompactByteRange(previous)
        val cursor = KompactCursor(byteArrayOf(1, 0x41))

        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.readByteRange(7, range))
        cursor.reset(cursor.buffer, position = 1)
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, cursor.readByteRange(8, range))
        cursor.reset(ByteArray(0))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.readByteRange(8, range))
        cursor.reset(byteArrayOf(2, 0x41))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.readByteRange(8, range))
        cursor.reset(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.readByteRange(32, range))
        cursor.reset(byteArrayOf(1, 0xFF.toByte()))
        assertEquals(KompactCursor.STATUS_INVALID_UTF8, cursor.readUtf8Range(8, range))

        assertSame(previous, range.buffer)
        assertEquals(0, cursor.position)
        cursor.reset(byteArrayOf(1, 0x41))
        assertEquals(KompactCursor.STATUS_OK, cursor.readByteRange(8, range))
        assertEquals(1, range.size)
    }

    @Test
    fun byteRangeSkipAndWritePreflightRejectMalformedArgumentsAtomically() {
        val data = byteArrayOf(1, 0x41)
        val cursor = KompactCursor(data)
        val external = KompactByteRange(byteArrayOf(0x41))

        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.skipByteRange(7))
        cursor.reset(data, position = 1)
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, cursor.skipByteRange(8))
        cursor.reset(ByteArray(0))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.skipByteRange(8))
        cursor.reset(byteArrayOf(2, 0x41))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.skipByteRange(8))
        cursor.reset(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.skipByteRange(32))
        cursor.reset(byteArrayOf(1, 0xFF.toByte()))
        assertEquals(KompactCursor.STATUS_INVALID_UTF8, cursor.skipByteRange(8, validateUtf8 = true))
        cursor.reset(byteArrayOf(1, 0xFF.toByte()))
        assertEquals(KompactCursor.STATUS_OK, cursor.skipByteRange(8))
        cursor.reset(data)
        assertEquals(KompactCursor.STATUS_OK, cursor.skipByteRange(8, validateUtf8 = true))

        val output = KompactCursor(ByteArray(3))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, output.preflightByteRangeWrite(7, external))
        output.reset(output.buffer, position = 1)
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, output.preflightByteRangeWrite(8, external))
        val tooLarge = KompactByteRange(ByteArray(256))
        output.reset(ByteArray(260))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, output.preflightByteRangeWrite(8, tooLarge))
        val malformedUtf8 = KompactByteRange(byteArrayOf(0xFF.toByte()))
        assertEquals(
            KompactCursor.STATUS_INVALID_UTF8,
            output.preflightByteRangeWrite(8, malformedUtf8, validateUtf8 = true),
        )
        val validUtf8 = KompactByteRange(byteArrayOf(0xC3.toByte(), 0xA9.toByte()))
        assertEquals(KompactCursor.STATUS_OK, output.preflightByteRangeWrite(8, validUtf8, validateUtf8 = true))
        val aliased = KompactByteRange(output.buffer)
        aliased.reset(output.buffer, end = 1)
        assertEquals(KompactCursor.STATUS_INVALID_ARGUMENT, output.preflightByteRangeWrite(8, aliased))
        val insufficient = KompactCursor(ByteArray(1))
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, insufficient.preflightByteRangeWrite(8, external))
        val widePrefix = KompactCursor(ByteArray(6))
        assertEquals(KompactCursor.STATUS_OK, widePrefix.preflightByteRangeWrite(32, external))
        assertEquals(KompactCursor.STATUS_OK, output.preflightByteRangeWrite(8, external))

        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, output.writeByteRange(7, external))
        output.reset(output.buffer, position = 1)
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, output.writeByteRange(8, external))
        output.reset(output.buffer)
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, output.writeByteRange(8, tooLarge))
        val overlapBuffer = byteArrayOf(1, 0x44, 0x55)
        val overlapCursor = KompactCursor(overlapBuffer)
        val overlapRange = KompactByteRange(overlapBuffer)
        overlapRange.reset(overlapBuffer, start = 1, end = 2)
        assertEquals(
            KompactCursor.STATUS_INVALID_ARGUMENT,
            overlapCursor.writeByteRange(8, overlapRange),
        )
        val tooSmall = KompactCursor(ByteArray(1))
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, tooSmall.writeByteRange(8, external))
        val adjacentBuffer = byteArrayOf(0, 0, 0x55, 0)
        val adjacentCursor = KompactCursor(adjacentBuffer)
        val adjacentRange = KompactByteRange(adjacentBuffer)
        adjacentRange.reset(adjacentBuffer, start = 2, end = 3)
        assertEquals(KompactCursor.STATUS_OK, adjacentCursor.writeByteRange(8, adjacentRange))
        val precedingCursor = KompactCursor(adjacentBuffer)
        precedingCursor.reset(adjacentBuffer, startBit = 16, position = 16, endBit = 32)
        val precedingRange = KompactByteRange(adjacentBuffer)
        precedingRange.reset(adjacentBuffer, end = 1)
        assertEquals(KompactCursor.STATUS_OK, precedingCursor.writeByteRange(8, precedingRange))
        val sixteenBitPrefix = KompactCursor(ByteArray(3))
        assertEquals(KompactCursor.STATUS_OK, sixteenBitPrefix.writeByteRange(16, external))
    }

    @Test
    fun byteRangeWritesAndCursorDiagnosticsPreserveTheCheckedContract() {
        val range = KompactByteRange(byteArrayOf(0x22, 0x33))
        val output = ByteArray(4)
        val cursor = KompactCursor(output)

        assertEquals(KompactCursor.STATUS_OK, cursor.writeByteRange(8, range))
        assertContentEquals(byteArrayOf(2, 0x22, 0x33, 0), output)
        assertEquals(KompactCursor.STATUS_OK, cursor.ensureDistinct(KompactCursor(ByteArray(0))))
        assertEquals(KompactCursor.STATUS_INVALID_ARGUMENT, cursor.ensureDistinct(cursor))

        val source = KompactCursor(ByteArray(0))
        source.resetByteRange(source.buffer, -1, 0)
        assertEquals(KompactCursor.STATUS_INVALID_BOUNDS, cursor.copyErrorFrom(source))
        assertEquals(source.errorBitOffset, cursor.errorBitOffset)
        assertEquals(source.errorDetail, cursor.errorDetail)
        assertEquals(KompactCursor.STATUS_INVALID_BOUNDS, cursor.copyErrorFrom(cursor))
    }
}
