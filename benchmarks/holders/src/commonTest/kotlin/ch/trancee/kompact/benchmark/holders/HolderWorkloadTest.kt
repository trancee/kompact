@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package ch.trancee.kompact.benchmark.holders

import ch.trancee.kompact.runtime.KompactCursor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class HolderWorkloadTest {
    @Test
    fun scalarDecodeConsumesTheEntirePreparedFrame() {
        val workload = HolderWorkload(0)

        val checksum = workload.decodeScalar()

        assertEquals(KompactCursor.STATUS_OK, workload.lastStatus)
        assertEquals(7, workload.scalarHolder.id)
        assertEquals(17, workload.scalarHolder.sequence)
        assertEquals(123456789L, workload.scalarHolder.timestamp)
        assertEquals(workload.scalarInputs[0].size * 8, workload.cursor.position)
        assertNotEquals(0L, checksum)
    }

    @Test
    fun fixedDecodeBindsPayloadAndRepeatWithoutMaterializingElements() {
        for (count in listOf(0, 1, 63, 64, 65, 4096, 65536)) {
            val workload = HolderWorkload(count)

            val checksum = workload.decodeFixed()

            assertEquals(KompactCursor.STATUS_OK, workload.lastStatus, "count=$count")
            assertEquals(7, workload.fixedHolder.id)
            assertEquals(17, workload.fixedHolder.sequence)
            assertEquals(128, workload.fixedHolder.payload.size)
            assertEquals(count, workload.fixedHolder.samplesWorkspace.count)
            assertEquals(4 + count * 2, workload.fixedHolder.samplesBytes.size)
            assertSame(workload.fixedInputs[0], workload.fixedHolder.payload.buffer)
            assertEquals(workload.fixedInputs[0].size * 8, workload.cursor.position)
            assertNotEquals(0L, checksum)
        }
    }

    @Test
    fun variableDecodeValidatesEveryBoundaryAndBorrowsTheOriginalFrame() {
        for (count in listOf(0, 1, 63, 64, 65, 4096, 65536)) {
            val workload = HolderWorkload(count)

            val checksum = workload.decodeVariable()

            assertEquals(KompactCursor.STATUS_OK, workload.lastStatus, "count=$count")
            assertEquals(7, workload.variableHolder.id)
            assertEquals(17, workload.variableHolder.sequence)
            assertEquals(128, workload.variableHolder.payload.size)
            assertEquals(count, workload.variableHolder.chunksWorkspace.count)
            assertSame(workload.variableInputs[0], workload.variableHolder.payload.buffer)
            assertEquals(workload.variableInputs[0].size * 8, workload.cursor.position)
            assertNotEquals(0L, checksum)
        }
    }

    @Test
    fun repeatedFixedDecodeRebindsTheReusableHolderToTheNextPreparedInput() {
        val workload = HolderWorkload(65)
        workload.decodeFixed()

        workload.decodeFixed()

        assertEquals(KompactCursor.STATUS_OK, workload.lastStatus)
        assertEquals(8, workload.fixedHolder.id)
        assertEquals(18, workload.fixedHolder.sequence)
        assertSame(workload.fixedInputs[1], workload.fixedHolder.payload.buffer)
    }

    @Test
    fun scalarDecodeReusesTheHolderForTheNextPreparedInput() {
        val workload = HolderWorkload(0)
        workload.decodeScalar()

        val checksum = workload.decodeScalar()

        assertEquals(8, workload.scalarHolder.id)
        assertEquals(18, workload.scalarHolder.sequence)
        assertEquals(123456790L, workload.scalarHolder.timestamp)
        assertEquals(123456928L, checksum)
    }

    @Test
    fun variableDecodeRebindsTheReusableHolderToTheNextPreparedInput() {
        val workload = HolderWorkload(65)
        workload.decodeVariable()

        workload.decodeVariable()

        assertEquals(KompactCursor.STATUS_OK, workload.lastStatus)
        assertEquals(8, workload.variableHolder.id)
        assertEquals(18, workload.variableHolder.sequence)
        assertSame(workload.variableInputs[1], workload.variableHolder.payload.buffer)
    }

    @Test
    fun fixedElementDecodingWorksAcrossSparseCheckpointBoundaries() {
        for (index in listOf(0, 63, 64, 65535)) {
            val workload = HolderWorkload(65536)
            workload.decodeFixed()
            val elementCursor = KompactCursor(ByteArray(0))

            val status = workload.fixedHolder.decodeSamplesElement(index, workload.cursor, elementCursor)

            assertEquals(KompactCursor.STATUS_OK, status, "index=$index")
            assertEquals(index, workload.fixedHolder.samplesElement, "index=$index")
        }
    }

    @Test
    fun variableElementDecodingWorksAcrossSparseCheckpointBoundaries() {
        for (index in listOf(0, 63, 64, 65535)) {
            val workload = HolderWorkload(65536)
            workload.decodeVariable()
            val elementCursor = KompactCursor(ByteArray(0))

            val status = workload.variableHolder.decodeChunksElement(index, workload.cursor, elementCursor)

            assertEquals(KompactCursor.STATUS_OK, status, "index=$index")
            assertEquals(index % 9, workload.variableHolder.chunksElement.size, "index=$index")
            assertSame(workload.variableInputs[0], workload.variableHolder.chunksElement.buffer)
        }
    }

    @Test
    fun truncatedFixedFramePreservesThePreviousHolderAndInputPosition() {
        val workload = HolderWorkload(65)
        workload.decodeFixed()
        val input = workload.fixedInputs[1]
        workload.cursor.reset(input, endBit = input.size * 8 - 1)

        val status = workload.fixedHolder.decodeInto(workload.cursor, workload.probe)

        assertEquals(KompactCursor.STATUS_TRUNCATED_INPUT, status)
        assertEquals(0, workload.cursor.position)
        assertEquals(7, workload.fixedHolder.id)
        assertEquals(17, workload.fixedHolder.sequence)
        assertSame(workload.fixedInputs[0], workload.fixedHolder.payload.buffer)
        assertSame(workload.fixedInputs[0], workload.fixedHolder.samplesBytes.buffer)
        assertEquals(65, workload.fixedHolder.samplesWorkspace.count)
    }

    @Test
    fun truncatedVariableFramePreservesThePreviousHolderAndInputPosition() {
        val workload = HolderWorkload(65)
        workload.decodeVariable()
        val input = workload.variableInputs[1]
        workload.cursor.reset(input, endBit = input.size * 8 - 1)

        val status = workload.variableHolder.decodeInto(workload.cursor, workload.probe)

        assertEquals(KompactCursor.STATUS_TRUNCATED_INPUT, status)
        assertEquals(0, workload.cursor.position)
        assertEquals(7, workload.variableHolder.id)
        assertEquals(17, workload.variableHolder.sequence)
        assertSame(workload.variableInputs[0], workload.variableHolder.payload.buffer)
        assertSame(workload.variableInputs[0], workload.variableHolder.chunksBytes.buffer)
        assertEquals(65, workload.variableHolder.chunksWorkspace.count)
    }

    @Test
    fun invalidFixtureCountFailsBeforeConstructingFrames() {
        for (count in listOf(-1, 65537)) {
            assertFailsWith<IllegalArgumentException> {
                HolderWorkload(count)
            }
        }
    }
}
