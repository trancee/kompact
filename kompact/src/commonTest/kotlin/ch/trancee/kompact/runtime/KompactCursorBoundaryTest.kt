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
                Triple(-1, 0, 8),
                Triple(4, 3, 8),
                Triple(0, 8, 7),
                Triple(0, 0, 17),
            )

        invalidBounds.forEach { (start, position, end) ->
            val status = cursor.reset(byteArrayOf(0), start, position, end)

            assertEquals(KompactCursor.STATUS_INVALID_BOUNDS, status)
            assertSame(original, cursor.buffer)
            assertEquals(1, cursor.startBit)
            assertEquals(2, cursor.position)
            assertEquals(12, cursor.endBit)
        }
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
    fun signedValidationCoversNarrowAndFullWidthValues() {
        val cursor = KompactCursor(ByteArray(8))

        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.validateSigned(0, 0L))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.validateSigned(65, 0L))
        assertEquals(KompactCursor.STATUS_OK, cursor.validateSigned(3, -4L))
        assertEquals(KompactCursor.STATUS_OK, cursor.validateSigned(3, 3L))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.validateSigned(3, -5L))
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.validateSigned(3, 4L))
        assertEquals(KompactCursor.STATUS_OK, cursor.writeSigned(64, Long.MIN_VALUE))
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
    fun zeroWritesHandleEmptyLargeAndInsufficientRegions() {
        val bytes = ByteArray(17) { 0x7F }
        val cursor = KompactCursor(bytes)

        assertEquals(KompactCursor.STATUS_OK, cursor.writeZeros(0))
        assertEquals(KompactCursor.STATUS_OK, cursor.writeZeros(129))
        assertEquals(129, cursor.position)
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.writeZeros(-1))
        val before = bytes.copyOf()
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, cursor.writeZeros(8))

        assertContentEquals(before, bytes)
        assertEquals(129, cursor.position)
    }
}
