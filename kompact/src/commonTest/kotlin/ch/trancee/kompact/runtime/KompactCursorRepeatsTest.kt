package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

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
        val workspace = KompactRepeatWorkspace(IntArray(2))
        assertEquals(KompactCursor.STATUS_OK, parent.readFixedRepeat(8, 8, workspace))

        val element = KompactCursor(ByteArray(0))
        assertEquals(KompactCursor.STATUS_OK, parent.readRepeatedElement(64, workspace, element))
        assertEquals(520, element.startBit)
        assertEquals(528, element.endBit)
    }
}
