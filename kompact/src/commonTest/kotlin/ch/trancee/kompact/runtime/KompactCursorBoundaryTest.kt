package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class KompactCursorBoundaryTest {
    @Test
    fun resetReportsEachInvalidBoundAndRetainsThePreviousRegion() {
        val original = byteArrayOf(0x12, 0x34)
        val cursor = KompactCursor(original)
        cursor.reset(original, startBit = 1, position = 2, endBit = 12)
        val invalidBounds =
            listOf(
                Triple(-1, 0, 8) to -1,
                Triple(4, 3, 8) to 3,
                Triple(0, 8, 7) to 7,
                Triple(0, 0, 17) to 17,
            )

        invalidBounds.forEach { (bounds, expectedErrorDetail) ->
            val (start, position, end) = bounds
            val status = cursor.reset(byteArrayOf(0), start, position, end)

            assertEquals(KompactCursor.STATUS_INVALID_BOUNDS, status)
            assertEquals(expectedErrorDetail, cursor.errorDetail)
            assertEquals(position, cursor.errorBitOffset)
            assertSame(original, cursor.buffer)
            assertEquals(1, cursor.startBit)
            assertEquals(2, cursor.position)
            assertEquals(12, cursor.endBit)
        }
    }

    @Test
    fun resetKeepsNegativePositionDiagnosticWhenStartIsZero() {
        val previous = byteArrayOf(0x12, 0x34)
        val cursor = KompactCursor(previous)
        cursor.reset(previous, startBit = 1, position = 2, endBit = 12)

        val status = cursor.reset(byteArrayOf(0x55), startBit = 0, position = -1, endBit = 8)

        assertEquals(KompactCursor.STATUS_INVALID_BOUNDS, status)
        assertEquals(0, cursor.errorBitOffset)
        assertEquals(-1, cursor.errorDetail)
        assertSame(previous, cursor.buffer)
        assertEquals(1, cursor.startBit)
        assertEquals(2, cursor.position)
        assertEquals(12, cursor.endBit)
    }

    @Test
    fun resetAcceptsValidSubrangeAndClearsPriorDiagnostics() {
        val cursor = KompactCursor(byteArrayOf(0x7F))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.skipBits(-1))

        val raw = byteArrayOf(0x12, 0x34, 0x56)
        val status = cursor.reset(raw, startBit = 3, position = 7, endBit = 19)

        assertEquals(KompactCursor.STATUS_OK, status)
        assertSame(raw, cursor.buffer)
        assertEquals(3, cursor.startBit)
        assertEquals(7, cursor.position)
        assertEquals(19, cursor.endBit)
        assertEquals(KompactCursor.STATUS_OK, cursor.status)
        assertEquals(0, cursor.errorBitOffset)
        assertEquals(0, cursor.errorDetail)
    }

    @Test
    fun resetByteRangeOverflowReportsByteBoundWithoutReplacingRegion() {
        val previous = byteArrayOf(0x12, 0x34)
        val cursor = KompactCursor(previous)
        cursor.reset(previous, startBit = 1, position = 3, endBit = 15)
        val endByte = Int.MAX_VALUE / 8 + 1

        val status = cursor.resetByteRange(byteArrayOf(0x55), 0, endByte)

        assertEquals(KompactCursor.STATUS_INVALID_BOUNDS, status)
        assertSame(previous, cursor.buffer)
        assertEquals(1, cursor.startBit)
        assertEquals(3, cursor.position)
        assertEquals(15, cursor.endBit)
        assertEquals(3, cursor.errorBitOffset)
        assertEquals(endByte, cursor.errorDetail)
    }

    @Test
    fun resetByteRangeRejectsInvalidOrderingAndBufferBounds() {
        val raw = byteArrayOf(1, 2)
        val cursor = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_INVALID_BOUNDS, cursor.resetByteRange(raw, -1, 1))
        assertEquals(KompactCursor.STATUS_INVALID_BOUNDS, cursor.resetByteRange(raw, 2, 1))
        assertEquals(KompactCursor.STATUS_INVALID_BOUNDS, cursor.resetByteRange(raw, 0, 3))
        assertEquals(KompactCursor.STATUS_INVALID_BOUNDS, cursor.resetByteRange(raw, 0, Int.MAX_VALUE / 8 + 1))
        assertEquals(0, cursor.position)
    }

    @Test
    fun resetByteRangeAcceptsZeroStartAndKeepsOriginalNegativeBoundDiagnostic() {
        val raw = byteArrayOf(0x41, 0x42)
        val cursor = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_OK, cursor.resetByteRange(raw, startByte = 0, endByte = 1))
        assertSame(raw, cursor.buffer)
        assertEquals(0, cursor.startBit)
        assertEquals(0, cursor.position)
        assertEquals(8, cursor.endBit)

        assertEquals(KompactCursor.STATUS_INVALID_BOUNDS, cursor.resetByteRange(raw, startByte = -1, endByte = 1))
        assertSame(raw, cursor.buffer)
        assertEquals(0, cursor.startBit)
        assertEquals(0, cursor.position)
        assertEquals(8, cursor.endBit)
        assertEquals(1, cursor.errorDetail)
    }

    @Test
    fun resetDiagnosticForEqualStartAndPositionUsesTheOutOfBufferEnd() {
        val raw = ByteArray(3)
        val cursor = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_INVALID_BOUNDS, cursor.reset(raw, startBit = 2, position = 2, endBit = 25))
        assertEquals(2, cursor.errorBitOffset)
        assertEquals(25, cursor.errorDetail)
    }

    @Test
    fun resetByteRangeAcceptsEmptyRegionAtExactBufferEnd() {
        val raw = byteArrayOf(1, 2)
        val cursor = KompactCursor(raw)

        val status = cursor.resetByteRange(raw, startByte = raw.size, endByte = raw.size)

        assertEquals(KompactCursor.STATUS_OK, status)
        assertSame(raw, cursor.buffer)
        assertEquals(16, cursor.startBit)
        assertEquals(16, cursor.position)
        assertEquals(16, cursor.endBit)
    }

    @Test
    fun checkedReadsAndSkipsRejectInvalidWidthsAndBoundsWithoutAdvancing() {
        val cursor = KompactCursor(byteArrayOf(0xFF.toByte()))

        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.readBits(0))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.readBits(65))
        assertEquals(KompactCursor.STATUS_OK, cursor.readBits(8))
        assertEquals(8, cursor.position)
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, cursor.readBits(1))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.ensureAvailable(-1))
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, cursor.ensureAvailable(1))
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, cursor.skipBits(1))

        cursor.reset(cursor.buffer)
        assertEquals(KompactCursor.STATUS_OK, cursor.skipBits(0))
        assertEquals(KompactCursor.STATUS_OK, cursor.skipBits(8))
        assertEquals(8, cursor.position)
    }

    @Test
    fun rawWritesRejectInvalidWidthsAndPreserveDataOnBoundsFailure() {
        val bytes = byteArrayOf(0x55, 0x66)
        val cursor = KompactCursor(bytes)
        cursor.reset(bytes, startBit = 4, position = 4, endBit = 8)

        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.writeBitsUnchecked(0, 1L))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.writeBitsUnchecked(65, 1L))
        assertEquals(KompactCursor.STATUS_OK, cursor.writeBitsUnchecked(4, 0xAL))
        val encoded = bytes.copyOf()
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, cursor.writeBitsUnchecked(1, 1L))

        assertContentEquals(encoded, bytes)
        assertEquals(8, cursor.position)
    }

    @Test
    fun successfulWriteClearsPriorCursorDiagnostics() {
        val bytes = ByteArray(1)
        val cursor = KompactCursor(bytes)
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.writeZeros(-1))
        assertEquals(-1, cursor.errorDetail)

        val status = cursor.writeZeros(1)

        assertEquals(KompactCursor.STATUS_OK, status)
        assertEquals(1, cursor.position)
        assertEquals(0, cursor.errorBitOffset)
        assertEquals(0, cursor.errorDetail)
    }

    @Test
    fun signedValidationCoversNarrowAndFullWidthValues() {
        val cursor = KompactCursor(ByteArray(8))

        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.validateSigned(0, 0L))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.validateSigned(65, 0L))
        assertEquals(KompactCursor.STATUS_OK, cursor.validateSigned(3, -4L))
        assertEquals(KompactCursor.STATUS_OK, cursor.validateSigned(3, 3L))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.validateSigned(3, -5L))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.validateSigned(3, 4L))
        assertEquals(KompactCursor.STATUS_OK, cursor.writeSigned(64, Long.MIN_VALUE))
        assertEquals(KompactCursor.STATUS_OK, cursor.validateSigned(64, Long.MAX_VALUE))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.writeSigned(3, 4L))
    }

    @Test
    fun signedAndUnsignedWritesValidateBeforeChangingTheBuffer() {
        val bytes = ByteArray(2)
        val cursor = KompactCursor(bytes)

        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.writeSigned(4, 8L))
        assertEquals(0, cursor.position)
        assertEquals(KompactCursor.STATUS_OK, cursor.writeSigned(4, -8L))
        val signedBytes = bytes.copyOf()
        cursor.reset(bytes)
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.writeUnsigned(4, -1L))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.writeUnsigned(4, 16L))
        assertContentEquals(signedBytes, bytes)
        assertEquals(KompactCursor.STATUS_OK, cursor.writeUnsigned(4, 15L))
        val fullWidth = KompactCursor(ByteArray(8))
        assertEquals(KompactCursor.STATUS_OK, fullWidth.writeUnsigned(64, ULong.MAX_VALUE))
    }

    @Test
    fun unsignedLongValidationDistinguishesWidth62FromWidth63AndAcceptsZero() {
        val cursor = KompactCursor(ByteArray(8))

        val zeroStatus = cursor.validateUnsigned(1, 0L)
        val narrowOverflowStatus = cursor.validateUnsigned(62, Long.MAX_VALUE)
        val width63Status = cursor.validateUnsigned(63, Long.MAX_VALUE)

        assertEquals(KompactCursor.STATUS_OK, zeroStatus)
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, narrowOverflowStatus)
        assertEquals(KompactCursor.STATUS_OK, width63Status)
    }

    @Test
    fun fullWidthUnsignedLongValidationAcceptsEveryBitPattern() {
        val cursor = KompactCursor(ByteArray(8))

        val status = cursor.validateUnsigned(64, ULong.MAX_VALUE)

        assertEquals(KompactCursor.STATUS_OK, status)
        assertEquals(0, cursor.errorDetail)
    }

    @Test
    fun signedLongOverloadRejectsNegativeUnsignedValuesAtFullWidth() {
        val cursor = KompactCursor(ByteArray(8))

        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.validateUnsigned(63, -1L))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.validateUnsigned(64, -1L))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.writeUnsigned(64, -1L))
        assertEquals(0, cursor.position)
    }

    @Test
    fun unsignedValidationCoversLongAndUnsignedLongDomains() {
        val cursor = KompactCursor(ByteArray(8))

        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.validateUnsigned(0, 0L))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.validateUnsigned(4, -1L))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.validateUnsigned(4, 16L))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.validateUnsigned(65, 0L))
        assertEquals(KompactCursor.STATUS_OK, cursor.validateUnsigned(63, Long.MAX_VALUE))
        assertEquals(KompactCursor.STATUS_OK, cursor.validateUnsigned(64, Long.MAX_VALUE))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.validateUnsigned(0, 0uL))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.validateUnsigned(65, 0uL))
        assertEquals(KompactCursor.STATUS_OK, cursor.validateUnsigned(7, 127uL))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.validateUnsigned(7, 128uL))
        assertEquals(KompactCursor.STATUS_OK, cursor.validateUnsigned(64, ULong.MAX_VALUE))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.writeUnsigned(7, 128uL))
    }

    @Test
    fun signedAndUnsignedValuesRespectTheSixtyThreeBitBoundary() {
        val cursor = KompactCursor(ByteArray(8))
        val signedMinimum = -(1L shl 62)
        val signedMaximum = (1L shl 62) - 1L
        val unsignedMaximum = (1uL shl 63) - 1uL

        assertEquals(KompactCursor.STATUS_OK, cursor.validateSigned(63, signedMinimum))
        assertEquals(KompactCursor.STATUS_OK, cursor.validateSigned(63, signedMaximum))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.validateSigned(63, signedMinimum - 1L))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.validateSigned(63, signedMaximum + 1L))
        assertEquals(KompactCursor.STATUS_OK, cursor.validateUnsigned(63, unsignedMaximum))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.validateUnsigned(63, 1uL shl 63))
        assertEquals(KompactCursor.STATUS_OK, cursor.validateUnsigned(62, (1L shl 62) - 1L))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.validateUnsigned(62, 1L shl 62))
    }

    @Test
    fun zeroWritesHandleEmptyLargeAndInsufficientRegions() {
        val bytes = ByteArray(17) { 0x7F }
        val cursor = KompactCursor(bytes)

        assertEquals(KompactCursor.STATUS_OK, cursor.writeZeros(0))
        assertEquals(KompactCursor.STATUS_OK, cursor.writeZeros(129))
        assertEquals(129, cursor.position)
        assertContentEquals(ByteArray(16) + byteArrayOf(0x7E), bytes)
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.writeZeros(-1))
        val before = bytes.copyOf()
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, cursor.writeZeros(8))

        assertContentEquals(before, bytes)
        assertEquals(129, cursor.position)
    }
}
