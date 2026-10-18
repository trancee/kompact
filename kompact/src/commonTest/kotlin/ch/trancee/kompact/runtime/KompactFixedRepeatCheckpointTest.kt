package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KompactFixedRepeatCheckpointTest {
    @Test
    fun fixedIndexPreservesExactCheckpointsAndUnusedStorageAcrossWidthsAndBlockBoundaries() {
        for (prefixWidth in listOf(8, 16, 32)) {
            for (elementWidth in listOf(1, 13, 16, 64)) {
                for (startBit in listOf(0, 40)) {
                    for (count in listOf(0, 1, 63, 64, 65, 127, 128, 129, 255, 256, 257, 4096, 65535, 65536)) {
                        if (count.toLong() >= 1L shl prefixWidth) continue
                        val repeatStart = startBit + prefixWidth
                        val repeatEnd = repeatStart + count * elementWidth
                        val bytes = ByteArray((repeatEnd + 7) / 8)
                        KompactRuntime.writeBitsLong(bytes, startBit, prefixWidth, count.toLong())
                        val original = bytes.copyOf()
                        val needed = (count + 63) / 64
                        val checkpoints = IntArray(needed + 2) { -1 }
                        val workspace = KompactRepeatWorkspace(checkpoints)
                        val cursor = KompactCursor(bytes)
                        assertEquals(
                            KompactCursor.STATUS_OK,
                            cursor.reset(bytes, startBit = startBit, endBit = repeatEnd),
                        )
                        val context = "count=$count prefix=$prefixWidth width=$elementWidth start=$startBit"

                        val status = cursor.readFixedRepeat(prefixWidth, elementWidth, workspace)

                        assertEquals(KompactCursor.STATUS_OK, status, context)
                        assertEquals(repeatEnd, cursor.position, context)
                        assertEquals(count.toLong(), cursor.valueBits, context)
                        assertEquals(count, workspace.count, context)
                        assertEquals(repeatStart, workspace.startBit, context)
                        assertEquals(repeatEnd, workspace.endBit, context)
                        assertContentEquals(
                            IntArray(needed + 2) { index ->
                                if (index < needed) repeatStart + index * 64 * elementWidth else -1
                            },
                            checkpoints,
                            context,
                        )
                        assertContentEquals(original, bytes, context)
                    }
                }
            }
        }
    }

    @Test
    fun fixedCountCapacityDiagnosticsPreserveEveryPrefixByte() {
        val cases =
            listOf(
                byteArrayOf(-1, 0, 0, 0) to 4,
                byteArrayOf(0, -1, 0, 0) to 1020,
                byteArrayOf(0, 0, -1, 0) to 261120,
                byteArrayOf(0, 0, 0, 1) to 262144,
                byteArrayOf(-1, -1, -1, 0x7F) to 33554432,
            )
        for ((bytes, expectedCapacity) in cases) {
            val cursor = KompactCursor(bytes)
            val workspace = KompactRepeatWorkspace(IntArray(0))

            val status = cursor.readFixedRepeat(32, 1, workspace)

            assertEquals(KompactCursor.STATUS_WORKSPACE_TOO_SMALL, status, "capacity=$expectedCapacity")
            assertEquals(expectedCapacity, cursor.errorDetail)
            assertEquals(0, cursor.errorBitOffset)
            assertEquals(0, cursor.position)
            assertEquals(0L, cursor.valueBits)
        }
    }

    @Test
    fun oneBitTruncationPreservesPriorWorkspaceBindingAndEveryCheckpoint() {
        val original = byteArrayOf(1, 0)
        val checkpoints = IntArray(5) { -1 }
        val workspace = KompactRepeatWorkspace(checkpoints)
        assertEquals(KompactCursor.STATUS_OK, KompactCursor(original).readFixedRepeat(8, 8, workspace))
        val priorCheckpoints = checkpoints.copyOf()
        val count = 129
        val startBit = 40
        val repeatStart = startBit + 16
        val bytes = ByteArray((repeatStart + count * 13 + 7) / 8)
        KompactRuntime.writeBitsLong(bytes, startBit, 16, count.toLong())
        val cursor = KompactCursor(bytes)
        assertEquals(
            KompactCursor.STATUS_OK,
            cursor.reset(bytes, startBit = startBit, endBit = repeatStart + count * 13 - 1),
        )

        val status = cursor.readFixedRepeat(16, 13, workspace)

        assertEquals(KompactCursor.STATUS_TRUNCATED_INPUT, status)
        assertEquals(startBit, cursor.position)
        assertEquals(repeatStart, cursor.errorBitOffset)
        assertEquals(count, cursor.errorDetail)
        assertEquals(1, workspace.count)
        assertEquals(8, workspace.startBit)
        assertEquals(16, workspace.endBit)
        assertTrue(workspace.isBoundTo(original))
        assertContentEquals(priorCheckpoints, checkpoints)
    }
}
