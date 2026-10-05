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
        assertFalse(range.copyTo(ByteArray(3), destinationOffset = Int.MAX_VALUE))
        assertFalse(range.copyTo(ByteArray(2)))
        assertTrue(range.reset(original, start = 1, end = 3))
        val copied = ByteArray(4)
        assertTrue(range.copyTo(copied, destinationOffset = 1))
        assertContentEquals(byteArrayOf(0, 5, 6, 0), copied)
    }

    @Test
    fun byteRangeResetAcceptsEmptyRangeAtExactBufferEnd() {
        val raw = byteArrayOf(4, 5, 6)
        val range = KompactByteRange(raw)

        val reset = range.reset(raw, start = raw.size, end = raw.size)

        assertTrue(reset)
        assertSame(raw, range.buffer)
        assertEquals(raw.size, range.start)
        assertEquals(raw.size, range.end)
        assertEquals(0, range.size)
    }

    @Test
    fun byteRangeCopyAcceptsExactCapacity() {
        val raw = byteArrayOf(4, 5, 6)
        val range = KompactByteRange(raw)
        range.reset(raw, start = 1, end = 3)
        val destination = ByteArray(2)

        val copied = range.copyTo(destination)

        assertTrue(copied)
        assertContentEquals(byteArrayOf(5, 6), destination)
    }

    @Test
    fun emptyByteRangeCopiesToAnEmptyDestination() {
        val range = KompactByteRange(ByteArray(0))

        val copied = range.copyTo(ByteArray(0))

        assertTrue(copied)
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
    fun rawRangeCaptureAndWritePreserveNonzeroByteOffsets() {
        val source = byteArrayOf(0x11, 0xC3.toByte(), 0xA9.toByte(), 0x22)
        val range = KompactByteRange(ByteArray(0))
        val capture = KompactCursor(source)
        assertEquals(KompactCursor.STATUS_OK, capture.resetByteRange(source, startByte = 1, endByte = 3))
        assertEquals(KompactCursor.STATUS_OK, capture.captureRegion(range, validateUtf8 = true))
        assertSame(source, range.buffer)
        assertEquals(1, range.start)
        assertEquals(3, range.end)

        val output = byteArrayOf(0x44, 0x55, 0x66)
        val writer = KompactCursor(output)
        assertEquals(KompactCursor.STATUS_OK, writer.resetByteRange(output, startByte = 1, endByte = 3))
        assertEquals(KompactCursor.STATUS_OK, writer.writeRawByteRange(range))
        assertContentEquals(byteArrayOf(0x44, 0xC3.toByte(), 0xA9.toByte()), output)
        assertEquals(24, writer.position)
    }

    @Test
    fun byteRangePreflightRejectsSixteenBitLengthAbovePrefixMaximum() {
        val range = KompactByteRange(ByteArray(65_536))
        val cursor = KompactCursor(ByteArray(65_538))

        val status = cursor.preflightByteRangeWrite(16, range)

        assertEquals(KompactCursor.STATUS_INVALID_VALUE, status)
        assertEquals(0, cursor.position)
    }

    @Test
    fun nestedReadAndWriteAcceptMaximumEightBitPayloadAtExactCapacity() {
        val payloadSize = 255
        val output = ByteArray(payloadSize + 1)
        val writer = KompactCursor(output)
        val child = KompactCursor(ByteArray(0))

        assertEquals(KompactCursor.STATUS_OK, writer.writeNested(8, payloadSize, child))
        assertEquals(payloadSize, output[0].toInt() and 0xFF)
        assertEquals(8, child.startBit)
        assertEquals(output.size * 8, child.endBit)
        assertEquals(output.size * 8, writer.position)

        val reader = KompactCursor(output)
        val decoded = KompactCursor(ByteArray(0))
        assertEquals(KompactCursor.STATUS_OK, reader.readNested(8, decoded))
        assertEquals(8, decoded.startBit)
        assertEquals(output.size * 8, decoded.endBit)
        assertEquals(output.size * 8, reader.position)
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
    fun captureDiagnosticsUseTheSelectedNonzeroByteRegion() {
        val raw = byteArrayOf(0x11, 0xFF.toByte(), 0x22)
        val range = KompactByteRange(ByteArray(0))
        val unaligned = KompactCursor(raw)
        unaligned.reset(raw, startBit = 8, position = 8, endBit = 17)
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, unaligned.captureRegion(range))
        assertEquals(8, unaligned.errorBitOffset)
        assertEquals(9, unaligned.errorDetail)

        val invalidUtf8 = KompactCursor(raw)
        invalidUtf8.resetByteRange(raw, startByte = 1, endByte = 2)
        assertEquals(KompactCursor.STATUS_INVALID_UTF8, invalidUtf8.captureRegion(range, validateUtf8 = true))
        assertEquals(8, invalidUtf8.errorBitOffset)
        assertEquals(1, invalidUtf8.errorDetail)
    }

    @Test
    fun prefixedWriteAllowsAdjacentRangesAndRejectsOverlapAtNonzeroCursorPosition() {
        val adjacentBytes = byteArrayOf(0x55, 0, 0)
        val adjacentCursor = KompactCursor(adjacentBytes)
        adjacentCursor.resetByteRange(adjacentBytes, startByte = 1, endByte = 3)
        val preceding = KompactByteRange(adjacentBytes)
        preceding.reset(adjacentBytes, end = 1)
        assertEquals(KompactCursor.STATUS_OK, adjacentCursor.writeByteRange(8, preceding))
        assertContentEquals(byteArrayOf(0x55, 1, 0x55), adjacentBytes)

        val overlappingBytes = byteArrayOf(0x55, 0x66, 0x77)
        val overlappingCursor = KompactCursor(overlappingBytes)
        overlappingCursor.resetByteRange(overlappingBytes, startByte = 1, endByte = 3)
        val prefixOverlap = KompactByteRange(overlappingBytes)
        prefixOverlap.reset(overlappingBytes, start = 1, end = 2)
        val before = overlappingBytes.copyOf()
        assertEquals(KompactCursor.STATUS_INVALID_ARGUMENT, overlappingCursor.writeByteRange(8, prefixOverlap))
        assertContentEquals(before, overlappingBytes)
        assertEquals(8, overlappingCursor.position)
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
        assertSame(successful.buffer, child.buffer)
        assertEquals(8, child.startBit)
        assertEquals(8, child.position)
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
    fun intMaxLengthPrefixesKeepBadLengthDiagnosticsAndBindingsUnchanged() {
        val malformedPrefix = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F)
        val nestedCursor = KompactCursor(byteArrayOf(0x55))
        nestedCursor.reset(nestedCursor.buffer, position = 1, endBit = 8)
        val nestedParent = KompactCursor(malformedPrefix)

        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, nestedParent.readNested(32, nestedCursor))
        assertEquals(0, nestedParent.errorBitOffset)
        assertEquals(32, nestedParent.errorDetail)
        assertEquals(0, nestedParent.position)
        assertEquals(1, nestedCursor.position)
        assertEquals(8, nestedCursor.endBit)

        val original = byteArrayOf(0x44)
        val range = KompactByteRange(original)
        val rangeReader = KompactCursor(malformedPrefix)
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, rangeReader.readByteRange(32, range))
        assertEquals(0, rangeReader.errorBitOffset)
        assertEquals(32, rangeReader.errorDetail)
        assertEquals(0, rangeReader.position)
        assertSame(original, range.buffer)

        val skipper = KompactCursor(malformedPrefix)
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, skipper.skipByteRange(32))
        assertEquals(0, skipper.errorBitOffset)
        assertEquals(32, skipper.errorDetail)
        assertEquals(0, skipper.position)
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
