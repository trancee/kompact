package ch.trancee.kompact.benchmark

import ch.trancee.kompact.runtime.KompactCursor
import ch.trancee.kompact.runtime.KompactStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatWorkloadTest {
    @Test
    fun fixedWorkloadIndexesEveryCheckpointAndPreservesElementValues() {
        for (count in listOf(0, 1, 63, 64, 65, 4096, 65536)) {
            val workload = RepeatWorkload(count)

            workload.fixedIndex()

            assertEquals(count, workload.fixedWorkspace.count)
            assertEquals(32 + count * 13, workload.fixedCursor.position)
            workload.fixedCheckpoints.forEachIndexed { checkpoint, offset ->
                assertEquals(32 + checkpoint * 64 * 13, offset, "count=$count, checkpoint=$checkpoint")
            }
            if (count > 0) {
                val element = KompactCursor(ByteArray(0))
                assertEquals(
                    KompactStatus.OK,
                    workload.fixedCursor.readRepeatedElement(count - 1, workload.fixedWorkspace, element),
                )
                assertEquals(KompactStatus.OK, element.readBits(13))
                assertEquals((count - 1).toLong() and 0x1fff, element.valueBits)
            }
        }
    }

    @Test
    fun variableWorkloadIndexesAllPayloadBoundaries() {
        for (count in listOf(0, 1, 63, 64, 65, 4096, 65536)) {
            val workload = RepeatWorkload(count)
            val payloadBytes = (0 until count).sumOf { it % 9 }

            workload.variableIndex()

            assertEquals(count, workload.variableWorkspace.count)
            assertEquals(32 + count * 8 + payloadBytes * 8, workload.variableCursor.position)
            if (count > 0) {
                val element = KompactCursor(ByteArray(0))
                assertEquals(
                    KompactStatus.OK,
                    workload.variableCursor.readRepeatedElement(count - 1, workload.variableWorkspace, element),
                )
                assertEquals((count - 1) % 9 * 8, element.endBit - element.startBit)
                if (element.position < element.endBit) {
                    assertEquals(KompactStatus.OK, element.readBits(8))
                    assertEquals((count - 1).toLong() and 0xff, element.valueBits)
                }
            }
        }
    }
}
