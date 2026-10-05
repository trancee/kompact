package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class KompactCursorTest {
    @Test
    fun byteRangeConstructorInitiallyBorrowsTheWholeBuffer() {
        val raw = byteArrayOf(3, 5, 8)
        val range = KompactByteRange(raw)

        assertSame(raw, range.buffer)
        assertEquals(0, range.start)
        assertEquals(raw.size, range.end)
        assertEquals(raw.size, range.size)
    }

    @Test
    fun resetRejectsOutOfBoundsWithoutChangingTheBoundRegion() {
        val raw = byteArrayOf(0x12, 0x34)
        val cursor = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_OK, cursor.reset(raw, startBit = 2, position = 3, endBit = 12))

        val status = cursor.reset(raw, startBit = 0, position = 3, endBit = 17)

        assertEquals(KompactCursor.STATUS_INVALID_BOUNDS, status)
        assertSame(raw, cursor.buffer)
        assertEquals(3, cursor.position)
        assertEquals(12, cursor.endBit)
        assertEquals(3, cursor.errorBitOffset)
    }

    @Test
    fun readBitsStopsAtTheBoundAndPreservesTheLastValueOnFailure() {
        val raw = byteArrayOf(0b0110_1101)
        val cursor = KompactCursor(raw)
        cursor.reset(raw, startBit = 1, position = 1, endBit = 5)

        assertEquals(KompactCursor.STATUS_OK, cursor.readBits(3))

        assertEquals(0b110L, cursor.valueBits)
        assertEquals(4, cursor.position)

        val status = cursor.readBits(2)

        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, status)
        assertEquals(0b110L, cursor.valueBits)
        assertEquals(4, cursor.position)
        assertEquals(4, cursor.errorBitOffset)
        assertEquals(2, cursor.errorDetail)
    }

    @Test
    fun uncheckedWritePreservesNeighboringBitsAndRejectsOutOfBoundsAtomically() {
        val raw = byteArrayOf(0b1010_0101.toByte())
        val cursor = KompactCursor(raw)
        cursor.reset(raw, startBit = 2, position = 2, endBit = 5)

        assertEquals(KompactCursor.STATUS_OK, cursor.writeBitsUnchecked(2, 0b11L))
        assertContentEquals(byteArrayOf(0b1010_1101.toByte()), raw)
        assertEquals(4, cursor.position)

        val previous = raw.copyOf()
        val status = cursor.writeBitsUnchecked(2, 0L)

        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, status)
        assertContentEquals(previous, raw)
        assertEquals(4, cursor.position)
        assertEquals(4, cursor.errorBitOffset)
        assertEquals(2, cursor.errorDetail)
    }

    @Test
    fun checkedWritesRejectValuesOutsideTheirDeclaredWidthWithoutMutation() {
        val raw = byteArrayOf(0x55)
        val cursor = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_OK, cursor.writeUnsigned(3, 7L))
        assertEquals(3, cursor.position)

        val previous = raw.copyOf()
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.writeUnsigned(3, 8L))
        assertContentEquals(previous, raw)
        assertEquals(3, cursor.position)

        cursor.reset(raw, position = 3)
        assertEquals(KompactCursor.STATUS_OK, cursor.writeSigned(3, -4L))
        assertEquals(6, cursor.position)
        assertEquals(KompactCursor.STATUS_INVALID_VALUE, cursor.writeSigned(3, 4L))
        assertEquals(6, cursor.position)
    }

    @Test
    fun nestedReadUsesASeparateBoundedCursorAndDoesNotEnterTheFollowingField() {
        val raw = byteArrayOf(2, 0x12, 0x34, 0x55)
        val parent = KompactCursor(raw)
        val nested = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_OK, parent.readNested(8, nested))

        assertEquals(24, parent.position)
        assertEquals(8, nested.startBit)
        assertEquals(24, nested.endBit)
        assertEquals(KompactCursor.STATUS_OK, nested.readBits(8))
        assertEquals(0x12L, nested.valueBits)
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, nested.readBits(16))
        assertEquals(16, nested.position)
    }

    @Test
    fun malformedNestedReadLeavesParentAndChildBindingsUnchanged() {
        val raw = byteArrayOf(2, 0x12)
        val other = byteArrayOf(0x33)
        val parent = KompactCursor(raw)
        val nested = KompactCursor(other)
        nested.reset(other, startBit = 1, position = 2, endBit = 7)

        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, parent.readNested(8, nested))

        assertEquals(0, parent.position)
        assertSame(other, nested.buffer)
        assertEquals(2, nested.position)
        assertEquals(7, nested.endBit)
    }

    @Test
    fun nestedWriteReservesKnownLengthAndBoundsTheChildCursor() {
        val raw = ByteArray(3)
        val parent = KompactCursor(raw)
        val nested = KompactCursor(raw)

        assertEquals(KompactCursor.STATUS_OK, parent.writeNested(8, 1, nested))
        assertEquals(1, raw[0].toInt())
        assertEquals(16, parent.position)
        assertEquals(16, nested.endBit)
        assertEquals(KompactCursor.STATUS_OK, nested.writeBitsUnchecked(8, 0x5AL))
        assertContentEquals(byteArrayOf(1, 0x5A, 0), raw)
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, nested.writeBitsUnchecked(1, 1L))
        assertContentEquals(byteArrayOf(1, 0x5A, 0), raw)
    }

    @Test
    fun byteRangeReadAndWriteBorrowAndAppendWithoutAResultWrapper() {
        val raw = byteArrayOf(2, 8, 9, 0x42)
        val input = KompactCursor(raw)
        val range = KompactByteRange(ByteArray(0))

        assertEquals(KompactCursor.STATUS_OK, input.readByteRange(8, range))

        assertSame(raw, range.buffer)
        assertEquals(1, range.start)
        assertEquals(3, range.end)
        assertEquals(KompactCursor.STATUS_OK, input.readBits(8))
        assertEquals(0x42L, input.valueBits)

        val outputBytes = ByteArray(4)
        val output = KompactCursor(outputBytes)
        assertEquals(KompactCursor.STATUS_OK, output.writeByteRange(8, range))

        assertContentEquals(byteArrayOf(2, 8, 9, 0), outputBytes)
        assertEquals(24, output.position)
    }

    @Test
    fun borrowedUtf8RangeCanBeCopiedIntoCallerOwnedStorage() {
        val encoded = byteArrayOf(2, 0xC3.toByte(), 0xA9.toByte())
        val cursor = KompactCursor(encoded)
        val textBytes = KompactByteRange(ByteArray(0))

        assertEquals(KompactCursor.STATUS_OK, cursor.readUtf8Range(8, textBytes))
        assertEquals(2, textBytes.size)

        val ownedCopy = ByteArray(textBytes.size)
        assertTrue(textBytes.copyTo(ownedCopy))
        assertContentEquals(byteArrayOf(0xC3.toByte(), 0xA9.toByte()), ownedCopy)
    }

    @Test
    fun utf8ByteRangeValidatesBeforeChangingCursorOrRange() {
        val original = byteArrayOf(0x55)
        val range = KompactByteRange(original)
        range.reset(original, end = 1)
        val malformed = KompactCursor(byteArrayOf(1, 0xFF.toByte()))

        assertEquals(KompactCursor.STATUS_INVALID_UTF8, malformed.readUtf8Range(8, range))

        assertEquals(0, malformed.position)
        assertEquals(1, range.size)
        assertSame(original, range.buffer)
        assertEquals(0x55.toByte(), range.buffer[range.start])

        val valid = KompactCursor(byteArrayOf(2, 0xC3.toByte(), 0xA9.toByte()))
        assertEquals(KompactCursor.STATUS_OK, valid.readUtf8Range(8, range))
        assertEquals(2, range.size)
        assertEquals(24, valid.position)
    }

    @Test
    fun variableRepeatReportsInsufficientWorkspaceWithoutAdvancing() {
        val raw = byteArrayOf(2, 1, 0xAA.toByte(), 1, 0xBB.toByte())
        val cursor = KompactCursor(raw)
        val workspace = KompactRepeatWorkspace(IntArray(0))

        assertEquals(KompactCursor.STATUS_WORKSPACE_TOO_SMALL, cursor.readVariableRepeat(8, 8, workspace))

        assertEquals(0, cursor.position)
        assertEquals(0, workspace.count)
        assertEquals(1, cursor.errorDetail)
    }

    @Test
    fun variableRepeatIndexesPayloadsInCallerOwnedWorkspace() {
        val raw = byteArrayOf(2, 1, 0xAA.toByte(), 2, 0xBB.toByte(), 0xCC.toByte())
        val cursor = KompactCursor(raw)
        val workspace = KompactRepeatWorkspace(IntArray(1))
        val element = KompactCursor(ByteArray(0))

        assertEquals(KompactCursor.STATUS_OK, cursor.readVariableRepeat(8, 8, workspace))

        assertEquals(1, workspace.capacity)
        assertEquals(2, workspace.count)
        assertEquals(48, cursor.position)
        assertEquals(KompactCursor.STATUS_OK, cursor.readRepeatedElement(1, workspace, element))
        assertEquals(48, cursor.position)
        assertEquals(32, element.startBit)
        assertEquals(48, element.endBit)
        assertEquals(KompactCursor.STATUS_OK, element.readBits(16))
        assertEquals(0xCCBBL, element.valueBits)
    }

    @Test
    fun variableRepeatRequiresOneCheckpointPer64Elements() {
        val raw = ByteArray(65)
        raw[0] = 64
        val cursor = KompactCursor(raw)
        val workspace = KompactRepeatWorkspace(IntArray(1))

        assertEquals(KompactCursor.STATUS_OK, cursor.readVariableRepeat(8, 8, workspace))

        assertEquals(64, workspace.count)
        assertEquals(520, cursor.position)
    }

    @Test
    fun fixedRepeatIndexesElementsWithoutAllocatingAnIndexArray() {
        val raw = byteArrayOf(3, 0x2A, 0x11, 0x22)
        val cursor = KompactCursor(raw)
        val workspace = KompactRepeatWorkspace(IntArray(1))
        val element = KompactCursor(ByteArray(0))

        assertEquals(KompactCursor.STATUS_OK, cursor.readFixedRepeat(8, 8, workspace))

        assertEquals(3, workspace.count)
        assertEquals(32, cursor.position)
        assertEquals(KompactCursor.STATUS_OK, cursor.readRepeatedElement(2, workspace, element))
        assertEquals(24, element.startBit)
        assertEquals(32, element.endBit)
        assertEquals(KompactCursor.STATUS_OK, element.readBits(8))
        assertEquals(0x22L, element.valueBits)
    }

    @Test
    fun fixedRepeatRejectsInsufficientWorkspaceWithoutChangingWorkspaceOrCursor() {
        val previous = byteArrayOf(1, 0x44)
        val workspace = KompactRepeatWorkspace(IntArray(1))
        val previousCursor = KompactCursor(previous)
        assertEquals(KompactCursor.STATUS_OK, previousCursor.readFixedRepeat(8, 8, workspace))

        val current = ByteArray(66)
        current[0] = 65
        val cursor = KompactCursor(current)

        assertEquals(KompactCursor.STATUS_WORKSPACE_TOO_SMALL, cursor.readFixedRepeat(8, 8, workspace))

        assertEquals(0, cursor.position)
        assertEquals(1, workspace.count)
        assertEquals(KompactCursor.STATUS_OK, previousCursor.readRepeatedElement(0, workspace, KompactCursor(ByteArray(0))))
    }

    @Test
    fun variableRepeatRejects65ElementsWhenOnlyOneCheckpointIsAvailable() {
        val raw = ByteArray(66)
        raw[0] = 65
        val cursor = KompactCursor(raw)
        val workspace = KompactRepeatWorkspace(IntArray(1))

        assertEquals(KompactCursor.STATUS_WORKSPACE_TOO_SMALL, cursor.readVariableRepeat(8, 8, workspace))

        assertEquals(0, cursor.position)
        assertEquals(0, workspace.count)
        assertEquals(2, cursor.errorDetail)
    }

    @Test
    fun truncatedVariableRepeatDoesNotAdvanceOrReplaceWorkspace() {
        val existing = byteArrayOf(1, 1, 0x44)
        val workspace = KompactRepeatWorkspace(IntArray(1))
        val existingCursor = KompactCursor(existing)
        assertEquals(KompactCursor.STATUS_OK, existingCursor.readVariableRepeat(8, 8, workspace))

        val truncated = KompactCursor(byteArrayOf(1, 2, 0xAA.toByte()))
        val element = KompactCursor(ByteArray(0))

        assertEquals(KompactCursor.STATUS_TRUNCATED_INPUT, truncated.readVariableRepeat(8, 8, workspace))

        assertEquals(0, truncated.position)
        assertEquals(1, workspace.count)
        assertEquals(KompactCursor.STATUS_OK, existingCursor.readRepeatedElement(0, workspace, element))
        assertEquals(KompactCursor.STATUS_OK, element.readBits(8))
        assertEquals(0x44L, element.valueBits)

    }
}
