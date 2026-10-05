package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class KompactCursorRepeatsTest {
    @Test
    fun variableRepeatSkipRejectsInvalidWidthsAlignmentAndMalformedPrefixes() {
        val cursor = KompactCursor(ByteArray(0))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.skipVariableRepeat(7, 8, 0))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.skipVariableRepeat(8, 7, 0))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.skipVariableRepeat(8, 8, -1))

        cursor.reset(byteArrayOf(0))
        cursor.reset(cursor.buffer, position = 1)
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, cursor.skipVariableRepeat(8, 8, 0))
        cursor.reset(ByteArray(0))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.skipVariableRepeat(8, 8, 0))
        cursor.reset(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.skipVariableRepeat(32, 8, 0))
        cursor.reset(byteArrayOf(1))
        assertEquals(KompactCursor.STATUS_WORKSPACE_TOO_SMALL, cursor.skipVariableRepeat(8, 8, 0))
        cursor.reset(byteArrayOf(1))
        assertEquals(KompactCursor.STATUS_TRUNCATED_INPUT, cursor.skipVariableRepeat(8, 8, 1))
        cursor.reset(byteArrayOf(1, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.skipVariableRepeat(8, 32, 1))
        cursor.reset(byteArrayOf(1, 2, 0x55))
        assertEquals(KompactCursor.STATUS_TRUNCATED_INPUT, cursor.skipVariableRepeat(8, 8, 1))
        cursor.reset(byteArrayOf(0))
        assertEquals(KompactCursor.STATUS_OK, cursor.skipVariableRepeat(8, 8, 0))
        assertEquals(8, cursor.position)
        val nonempty = KompactCursor(byteArrayOf(1, 1, 0x55))
        assertEquals(KompactCursor.STATUS_OK, nonempty.skipVariableRepeat(8, 8, 1))
        assertEquals(24, nonempty.position)
        val wideCount = KompactCursor(byteArrayOf(0, 0))
        assertEquals(KompactCursor.STATUS_OK, wideCount.skipVariableRepeat(16, 8, 0))
    }

    @Test
    fun fixedRepeatSkipChecksCountWidthCapacityAndPayloadBounds() {
        val cursor = KompactCursor(ByteArray(0))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.skipFixedRepeat(7, 8, 0))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.skipFixedRepeat(8, 0, 0))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.skipFixedRepeat(8, 65, 0))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.skipFixedRepeat(8, 8, -1))

        cursor.reset(byteArrayOf(0))
        cursor.reset(cursor.buffer, position = 1)
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, cursor.skipFixedRepeat(8, 8, 0))
        cursor.reset(ByteArray(0))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.skipFixedRepeat(8, 8, 0))
        cursor.reset(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.skipFixedRepeat(32, 8, 0))
        cursor.reset(byteArrayOf(1))
        assertEquals(KompactCursor.STATUS_WORKSPACE_TOO_SMALL, cursor.skipFixedRepeat(8, 8, 0))
        cursor.reset(byteArrayOf(2))
        assertEquals(KompactCursor.STATUS_TRUNCATED_INPUT, cursor.skipFixedRepeat(8, 8, 1))
        cursor.reset(byteArrayOf(0))
        assertEquals(KompactCursor.STATUS_OK, cursor.skipFixedRepeat(8, 8, 0))
        assertEquals(8, cursor.position)
        val wideCount = KompactCursor(byteArrayOf(0, 0))
        assertEquals(KompactCursor.STATUS_OK, wideCount.skipFixedRepeat(16, 8, 0))
    }

    @Test
    fun variableRepeatReadIsFailureAtomicAndIndexesEmptyAndNonemptyRepeats() {
        val workspace = KompactRepeatWorkspace(IntArray(1))
        val cursor = KompactCursor(ByteArray(0))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.readVariableRepeat(7, 8, workspace))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.readVariableRepeat(8, 7, workspace))
        cursor.reset(byteArrayOf(0))
        cursor.reset(cursor.buffer, position = 1)
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, cursor.readVariableRepeat(8, 8, workspace))
        cursor.reset(ByteArray(0))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.readVariableRepeat(8, 8, workspace))
        cursor.reset(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.readVariableRepeat(32, 8, workspace))

        val empty = KompactCursor(byteArrayOf(0))
        assertEquals(KompactCursor.STATUS_OK, empty.readVariableRepeat(8, 8, workspace))
        assertEquals(0, workspace.count)

        val malformed = KompactCursor(byteArrayOf(1))
        assertEquals(KompactCursor.STATUS_TRUNCATED_INPUT, malformed.readVariableRepeat(8, 8, workspace))
        assertEquals(0, malformed.position)
        assertEquals(0, workspace.count)

        val unrepresentableLength =
            KompactCursor(byteArrayOf(1, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, unrepresentableLength.readVariableRepeat(8, 32, workspace))
        val truncatedPayload = KompactCursor(byteArrayOf(1, 2, 0x55))
        assertEquals(KompactCursor.STATUS_TRUNCATED_INPUT, truncatedPayload.readVariableRepeat(8, 8, workspace))

        val valid = KompactCursor(byteArrayOf(1, 1, 0x66))
        assertEquals(KompactCursor.STATUS_OK, valid.readVariableRepeat(8, 8, workspace))
        assertEquals(1, workspace.count)
        assertEquals(24, valid.position)
    }

    @Test
    fun fixedRepeatReadValidatesArgumentsCapacityAndElementBounds() {
        val workspace = KompactRepeatWorkspace(IntArray(1))
        val cursor = KompactCursor(ByteArray(0))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.readFixedRepeat(7, 8, workspace))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.readFixedRepeat(8, 0, workspace))
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.readFixedRepeat(8, 65, workspace))
        cursor.reset(byteArrayOf(0))
        cursor.reset(cursor.buffer, position = 1)
        assertEquals(KompactCursor.STATUS_UNALIGNED_REGION, cursor.readFixedRepeat(8, 8, workspace))
        cursor.reset(ByteArray(0))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.readFixedRepeat(8, 8, workspace))
        cursor.reset(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.readFixedRepeat(32, 8, workspace))
        cursor.reset(byteArrayOf(1))
        assertEquals(KompactCursor.STATUS_TRUNCATED_INPUT, cursor.readFixedRepeat(8, 8, workspace))
        cursor.reset(byteArrayOf(0))
        assertEquals(KompactCursor.STATUS_OK, cursor.readFixedRepeat(8, 8, workspace))
        assertEquals(0, workspace.count)
    }

    @Test
    fun repeatedElementReadRejectsWrongBindingAndOutOfRangeIndexes() {
        val raw = byteArrayOf(1, 0x44)
        val cursor = KompactCursor(raw)
        val workspace = KompactRepeatWorkspace(IntArray(1))
        assertEquals(KompactCursor.STATUS_OK, cursor.readFixedRepeat(8, 8, workspace))
        assertEquals(KompactCursor.STATUS_INVALID_ARGUMENT, cursor.readRepeatedElement(0, workspace, cursor))
        assertEquals(
            KompactCursor.STATUS_INVALID_ARGUMENT,
            cursor.readRepeatedElement(0, KompactRepeatWorkspace(IntArray(0)), KompactCursor(ByteArray(0))),
        )
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, cursor.readRepeatedElement(-1, workspace, KompactCursor(ByteArray(0))))
        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, cursor.readRepeatedElement(1, workspace, KompactCursor(ByteArray(0))))
        assertEquals(
            KompactCursor.STATUS_OK,
            cursor.readRepeatedElement(0, workspace, KompactCursor(ByteArray(0))),
        )
    }

    @Test
    fun indexedElementReadChecksFixedAndVariableOffsetsAgainstWorkspaceBounds() {
        val source = byteArrayOf(0, 0, 0, 0, 0)
        val parent = KompactCursor(source)
        val element = KompactCursor(ByteArray(0))
        val fixed = KompactRepeatWorkspace(IntArray(1))
        fixed.bind(source, count = 1, startBit = 0, endBit = 0, elementPrefixWidth = 0, elementBitWidth = 8)

        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, parent.readRepeatedElement(0, fixed, element))

        val noPrefix = KompactRepeatWorkspace(IntArray(1))
        noPrefix.bind(source, count = 1, startBit = 0, endBit = 0, elementPrefixWidth = 8, elementBitWidth = 0)
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, parent.readRepeatedElement(0, noPrefix, element))

        val tooLargeLength = KompactRepeatWorkspace(IntArray(1))
        tooLargeLength.bind(source, count = 1, startBit = 0, endBit = 32, elementPrefixWidth = 32, elementBitWidth = 0)
        source.fill(0xFF.toByte(), toIndex = 4)
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, parent.readRepeatedElement(0, tooLargeLength, element))

        val truncatedPayload = KompactRepeatWorkspace(IntArray(1))
        truncatedPayload.bind(source, count = 1, startBit = 0, endBit = 8, elementPrefixWidth = 8, elementBitWidth = 0)
        source[0] = 1
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, parent.readRepeatedElement(0, truncatedPayload, element))

        val truncatedSkippedPrefix = KompactRepeatWorkspace(IntArray(1))
        truncatedSkippedPrefix.bind(source, count = 2, startBit = 0, endBit = 0, elementPrefixWidth = 8, elementBitWidth = 0)
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, parent.readRepeatedElement(1, truncatedSkippedPrefix, element))

        val tooLargeSkippedLength = KompactRepeatWorkspace(IntArray(1))
        tooLargeSkippedLength.bind(source, count = 2, startBit = 0, endBit = 32, elementPrefixWidth = 32, elementBitWidth = 0)
        source.fill(0xFF.toByte(), toIndex = 4)
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, parent.readRepeatedElement(1, tooLargeSkippedLength, element))

        val truncatedSkippedPayload = KompactRepeatWorkspace(IntArray(1))
        truncatedSkippedPayload.bind(source, count = 2, startBit = 0, endBit = 8, elementPrefixWidth = 8, elementBitWidth = 0)
        source[0] = 1
        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, parent.readRepeatedElement(1, truncatedSkippedPayload, element))
    }

    @Test
    fun fixedRepeatIndexingStoresSparseCheckpointsAcrossBlocks() {
        val bytes = ByteArray(66)
        bytes[0] = 65
        val parent = KompactCursor(bytes)
        val checkpointStorage = IntArray(2) { -1 }
        val workspace = KompactRepeatWorkspace(checkpointStorage)
        assertEquals(KompactCursor.STATUS_OK, parent.readFixedRepeat(8, 8, workspace))
        assertContentEquals(intArrayOf(8, 520), checkpointStorage)

        val element = KompactCursor(ByteArray(0))
        assertEquals(KompactCursor.STATUS_OK, parent.readRepeatedElement(64, workspace, element))
        assertEquals(520, element.startBit)
        assertEquals(528, element.endBit)
    }

    @Test
    fun corruptedCheckpointAtTheEndReportsTheNextMissingPrefixOffset() {
        val source = byteArrayOf(2, 0, 0)
        val checkpointStorage = IntArray(1)
        val cursor = KompactCursor(source)
        val workspace = KompactRepeatWorkspace(checkpointStorage)
        assertEquals(KompactCursor.STATUS_OK, cursor.readVariableRepeat(8, 8, workspace))
        checkpointStorage[0] = workspace.endBit - 8
        val element = KompactCursor(ByteArray(0))

        val status = cursor.readRepeatedElement(1, workspace, element)

        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, status)
        assertEquals(workspace.endBit, cursor.errorBitOffset)
        assertEquals(8, cursor.errorDetail)
        assertEquals(24, cursor.position)
    }

    @Test
    fun corruptedCheckpointElementEndingAtTheRegionBoundaryReportsTheNextPrefix() {
        val source = byteArrayOf(2, 0, 0)
        val checkpointStorage = IntArray(1)
        val cursor = KompactCursor(source)
        val workspace = KompactRepeatWorkspace(checkpointStorage)
        assertEquals(KompactCursor.STATUS_OK, cursor.readVariableRepeat(8, 8, workspace))
        checkpointStorage[0] = 0
        val element = KompactCursor(ByteArray(0))

        val status = cursor.readRepeatedElement(1, workspace, element)

        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, status)
        assertEquals(workspace.endBit, cursor.errorBitOffset)
        assertEquals(8, cursor.errorDetail)
        assertEquals(24, cursor.position)
    }

    @Test
    fun repeatedElementLookupAtIntMaxSkippedLengthKeepsBadPrefixDiagnostics() {
        val source = byteArrayOf(2, 0, 0, 0, 4, 0, 0, 0, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F, 0, 0, 0, 0)
        val checkpointStorage = IntArray(1)
        val cursor = KompactCursor(source)
        val workspace = KompactRepeatWorkspace(checkpointStorage)
        assertEquals(KompactCursor.STATUS_OK, cursor.readVariableRepeat(32, 32, workspace))
        checkpointStorage[0] = 64
        val element = KompactCursor(ByteArray(0))

        val status = cursor.readRepeatedElement(1, workspace, element)

        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, status)
        assertEquals(64, cursor.errorBitOffset)
        assertEquals(32, cursor.errorDetail)
        assertEquals(128, cursor.position)
    }

    @Test
    fun repeatedElementLookupAtIntMaxPayloadLengthKeepsBadPrefixDiagnostics() {
        val source = byteArrayOf(2, 0, 0, 0, 4, 0, 0, 0, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F, 0, 0, 0, 0)
        val checkpointStorage = IntArray(1)
        val cursor = KompactCursor(source)
        val workspace = KompactRepeatWorkspace(checkpointStorage)
        assertEquals(KompactCursor.STATUS_OK, cursor.readVariableRepeat(32, 32, workspace))
        checkpointStorage[0] = 64
        val element = KompactCursor(ByteArray(0))

        val status = cursor.readRepeatedElement(0, workspace, element)

        assertEquals(KompactCursor.STATUS_BAD_LENGTH_PREFIX, status)
        assertEquals(64, cursor.errorBitOffset)
        assertEquals(32, cursor.errorDetail)
        assertEquals(128, cursor.position)
    }

    @Test
    fun variableRepeatAcceptsEmptyElementEndingExactlyAtTheRegionBoundary() {
        val encoded = byteArrayOf(1, 0)
        val skipper = KompactCursor(encoded)

        assertEquals(KompactCursor.STATUS_OK, skipper.skipVariableRepeat(8, 8, workspaceCapacity = 1))
        assertEquals(16, skipper.position)
        assertEquals(1L, skipper.valueBits)

        val reader = KompactCursor(encoded)
        val workspace = KompactRepeatWorkspace(IntArray(1))
        assertEquals(KompactCursor.STATUS_OK, reader.readVariableRepeat(8, 8, workspace))
        assertEquals(16, reader.position)
        assertEquals(1, workspace.count)

        val element = KompactCursor(byteArrayOf(0x55))
        assertEquals(KompactCursor.STATUS_OK, reader.readRepeatedElement(0, workspace, element))
        assertSame(encoded, element.buffer)
        assertEquals(16, element.startBit)
        assertEquals(16, element.position)
        assertEquals(16, element.endBit)
    }

    @Test
    fun repeatCountsAndLengthsAtIntMaximumKeepTheirSpecificFailureStatus() {
        val maxCount = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F)
        val variableSkip = KompactCursor(maxCount)
        assertEquals(
            KompactCursor.STATUS_WORKSPACE_TOO_SMALL,
            variableSkip.skipVariableRepeat(32, 8, workspaceCapacity = 0),
        )
        val fixedSkip = KompactCursor(maxCount)
        assertEquals(
            KompactCursor.STATUS_WORKSPACE_TOO_SMALL,
            fixedSkip.skipFixedRepeat(32, 1, workspaceCapacity = 0),
        )
        val variableRead = KompactCursor(maxCount)
        assertEquals(
            KompactCursor.STATUS_WORKSPACE_TOO_SMALL,
            variableRead.readVariableRepeat(32, 8, KompactRepeatWorkspace(IntArray(0))),
        )
        val fixedRead = KompactCursor(maxCount)
        assertEquals(
            KompactCursor.STATUS_WORKSPACE_TOO_SMALL,
            fixedRead.readFixedRepeat(32, 1, KompactRepeatWorkspace(IntArray(0))),
        )

        val maxElementLength = KompactCursor(byteArrayOf(1, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F))
        assertEquals(
            KompactCursor.STATUS_TRUNCATED_INPUT,
            maxElementLength.skipVariableRepeat(8, 32, workspaceCapacity = 1),
        )
        assertEquals(0, maxElementLength.position)
        val maxElementRead = KompactCursor(byteArrayOf(1, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F))
        assertEquals(
            KompactCursor.STATUS_TRUNCATED_INPUT,
            maxElementRead.readVariableRepeat(8, 32, KompactRepeatWorkspace(IntArray(1))),
        )
        assertEquals(0, maxElementRead.position)
    }

    @Test
    fun repeatArgumentFailuresReportTheFirstInvalidWidth() {
        val cursor = KompactCursor(ByteArray(0))
        val workspace = KompactRepeatWorkspace(IntArray(0))

        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.skipVariableRepeat(7, 9, 0))
        assertEquals(7, cursor.errorDetail)
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.skipVariableRepeat(8, 7, 0))
        assertEquals(7, cursor.errorDetail)
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.skipFixedRepeat(7, 0, 0))
        assertEquals(7, cursor.errorDetail)
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.skipFixedRepeat(8, 0, 0))
        assertEquals(0, cursor.errorDetail)
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.readVariableRepeat(7, 9, workspace))
        assertEquals(7, cursor.errorDetail)
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.readVariableRepeat(8, 7, workspace))
        assertEquals(7, cursor.errorDetail)
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.readFixedRepeat(7, 0, workspace))
        assertEquals(7, cursor.errorDetail)
        assertEquals(KompactCursor.STATUS_INVALID_WIDTH, cursor.readFixedRepeat(8, 0, workspace))
        assertEquals(0, cursor.errorDetail)
    }

    @Test
    fun variableRepeatCheckpointCapacityHandlesZeroAndExactSixtyFourCount() {
        val empty = KompactCursor(byteArrayOf(0))
        val noCheckpoints = KompactRepeatWorkspace(IntArray(0))
        assertEquals(KompactCursor.STATUS_OK, empty.readVariableRepeat(8, 8, noCheckpoints))
        assertEquals(0, noCheckpoints.count)
        assertEquals(8, empty.position)

        val sixtyFourEmptyElements = KompactCursor(byteArrayOf(64) + ByteArray(64))
        val oneCheckpoint = KompactRepeatWorkspace(IntArray(1))
        assertEquals(KompactCursor.STATUS_OK, sixtyFourEmptyElements.readVariableRepeat(8, 8, oneCheckpoint))
        assertEquals(64, oneCheckpoint.count)
        assertEquals(520, sixtyFourEmptyElements.position)
        val lastElement = KompactCursor(ByteArray(0))
        assertEquals(KompactCursor.STATUS_OK, sixtyFourEmptyElements.readRepeatedElement(63, oneCheckpoint, lastElement))
        assertEquals(520, lastElement.startBit)
        assertEquals(520, lastElement.endBit)
    }

    @Test
    fun fixedRepeatCheckpointLoopAcceptsAnExactSixtyFourElementCapacity() {
        val raw = ByteArray(9)
        raw[0] = 64
        val cursor = KompactCursor(raw)
        val workspace = KompactRepeatWorkspace(IntArray(1))

        assertEquals(KompactCursor.STATUS_OK, cursor.readFixedRepeat(8, 1, workspace))
        assertEquals(64, workspace.count)
        assertEquals(72, cursor.position)

        val element = KompactCursor(ByteArray(0))
        assertEquals(KompactCursor.STATUS_OK, cursor.readRepeatedElement(63, workspace, element))
        assertEquals(71, element.startBit)
        assertEquals(72, element.endBit)
    }

    @Test
    fun variableRepeatLookupAtIndex63ReturnsItsPayload() {
        assertVariableRepeatLookup(63)
    }

    @Test
    fun variableRepeatLookupAtIndex64ReturnsItsPayload() {
        assertVariableRepeatLookup(64)
    }

    @Test
    fun variableRepeatLookupAtIndex65ReturnsItsPayload() {
        assertVariableRepeatLookup(65)
    }

    @Test
    fun fixedRepeatLookupAtIndex63ReturnsItsPayload() {
        assertFixedRepeatLookup(63)
    }

    @Test
    fun fixedRepeatLookupAtIndex64ReturnsItsPayload() {
        assertFixedRepeatLookup(64)
    }

    @Test
    fun fixedRepeatLookupAtIndex65ReturnsItsPayload() {
        assertFixedRepeatLookup(65)
    }

    @Test
    fun outOfRangeRepeatLookupRetainsElementBindingAndReportsDiagnostics() {
        val source = byteArrayOf(1, 0x44)
        val parent = KompactCursor(source)
        val workspace = KompactRepeatWorkspace(IntArray(1))
        assertEquals(KompactCursor.STATUS_OK, parent.readFixedRepeat(8, 8, workspace))

        val previousBuffer = byteArrayOf(0x55, 0x66)
        val element = KompactCursor(previousBuffer)
        element.reset(previousBuffer, startBit = 1, position = 3, endBit = 15)
        val previousParentPosition = parent.position

        val status = parent.readRepeatedElement(1, workspace, element)

        assertEquals(KompactCursor.STATUS_BOUNDS_ERROR, status)
        assertEquals(workspace.startBit, parent.errorBitOffset)
        assertEquals(1, parent.errorDetail)
        assertEquals(previousParentPosition, parent.position)
        assertSame(previousBuffer, element.buffer)
        assertEquals(1, element.startBit)
        assertEquals(3, element.position)
        assertEquals(15, element.endBit)
    }

    private fun assertFixedRepeatLookup(index: Int) {
        val bytes = ByteArray(67)
        bytes[0] = 66
        repeat(66) { elementIndex -> bytes[elementIndex + 1] = (elementIndex + 1).toByte() }
        val parent = KompactCursor(bytes)
        val workspace = KompactRepeatWorkspace(IntArray(2))
        assertEquals(KompactCursor.STATUS_OK, parent.readFixedRepeat(8, 8, workspace))
        val element = KompactCursor(ByteArray(0))

        val status = parent.readRepeatedElement(index, workspace, element)

        assertEquals(KompactCursor.STATUS_OK, status)
        assertSame(bytes, element.buffer)
        assertEquals(8 + index * 8, element.startBit)
        assertEquals(8 + (index + 1) * 8, element.endBit)
        assertEquals((index + 1).toByte(), bytes[element.startBit / 8])
    }

    private fun assertVariableRepeatLookup(index: Int) {
        val payloadLength = { elementIndex: Int -> elementIndex % 3 + 1 }
        val bytes = ArrayList<Byte>()
        bytes.add(66)
        repeat(66) { elementIndex ->
            val length = payloadLength(elementIndex)
            bytes.add(length.toByte())
            repeat(length) { payloadIndex -> bytes.add((elementIndex + payloadIndex + 1).toByte()) }
        }
        val source = bytes.toByteArray()
        val parent = KompactCursor(source)
        val workspace = KompactRepeatWorkspace(IntArray(2))
        assertEquals(KompactCursor.STATUS_OK, parent.readVariableRepeat(8, 8, workspace))
        val element = KompactCursor(ByteArray(0))
        var expectedStartBit = 8
        for (elementIndex in 0 until index) {
            expectedStartBit += 8 + payloadLength(elementIndex) * 8
        }
        val expectedEndBit = expectedStartBit + 8 + payloadLength(index) * 8

        val status = parent.readRepeatedElement(index, workspace, element)

        assertEquals(KompactCursor.STATUS_OK, status)
        assertSame(source, element.buffer)
        assertEquals(expectedStartBit + 8, element.startBit)
        assertEquals(expectedEndBit, element.endBit)
        assertEquals((index + 1).toByte(), source[element.startBit / 8])
    }

}
