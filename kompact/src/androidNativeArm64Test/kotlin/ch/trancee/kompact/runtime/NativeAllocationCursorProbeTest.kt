package ch.trancee.kompact.runtime

import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class CursorAllocationProbe

private var retainedCursorAllocationProbes: List<CursorAllocationProbe>? = null

private fun retainCursorAllocationProbes(count: Int) {
    retainedCursorAllocationProbes = List(count) { CursorAllocationProbe() }
}

private fun releaseCursorAllocationProbes() {
    retainedCursorAllocationProbes = null
}

private fun allocateAndReleaseCursorProbes(count: Int) {
    retainCursorAllocationProbes(count)
    releaseCursorAllocationProbes()
}

private var cursorAllocationChecksum = 0

private fun runPrimitiveByteReads(buffer: ByteArray, count: Int) {
    var checksum = 0
    repeat(count) {
        checksum += buffer[it].toInt() and 0xff
    }
    cursorAllocationChecksum = checksum
}

private fun runCursorByteReads(cursor: KompactCursor, count: Int) {
    var checksum = 0
    repeat(count) {
        cursor.readBits(Byte.SIZE_BITS)
        checksum += cursor.valueBits.toInt()
    }
    cursorAllocationChecksum = checksum
}

private fun runPrimitiveByteWrites(buffer: ByteArray, count: Int) {
    var checksum = 0
    repeat(count) {
        buffer[it] = it.toByte()
        checksum += buffer[it].toInt() and 0xff
    }
    cursorAllocationChecksum = checksum
}

private fun runCursorByteWrites(cursor: KompactCursor, count: Int) {
    var checksum = 0
    repeat(count) {
        cursor.writeUnsigned(Byte.SIZE_BITS, (it and 0xff).toULong())
        checksum += cursor.buffer[it].toInt() and 0xff
    }
    cursorAllocationChecksum = checksum
}

@OptIn(NativeRuntimeApi::class, ExperimentalStdlibApi::class)
class NativeAllocationCursorProbeTest {
    private fun sweptObjectsAfterLastCollection(): Long =
        requireNotNull(GC.lastGCInfo).sweepStatistics.values.sumOf { it.sweptCount }

    private fun collectAfterAllocationControl(count: Int): Long {
        GC.collect()
        allocateAndReleaseCursorProbes(count)
        GC.collect()
        return sweptObjectsAfterLastCollection()
    }

    @Test
    fun callerOwnedCursorByteReadsStayWithinPrimitiveBaseline() {
        val iterations = 4_096
        val sampleCount = 3
        val initialBytes = ByteArray(iterations) { it.toByte() }
        val baselineBuffer = initialBytes.copyOf()
        val measuredBuffer = initialBytes.copyOf()
        val cursor = KompactCursor(measuredBuffer)
        val baselineSwept = LongArray(sampleCount)
        val readsSwept = LongArray(sampleCount)
        val allocationControlSwept = LongArray(sampleCount)

        repeat(sampleCount) { sample ->
            GC.collect()
            runPrimitiveByteReads(baselineBuffer, iterations)
            GC.collect()
            baselineSwept[sample] = sweptObjectsAfterLastCollection()

            assertEquals(KompactCursor.STATUS_OK, cursor.reset(measuredBuffer, endBit = iterations * Byte.SIZE_BITS))
            GC.collect()
            runCursorByteReads(cursor, iterations)
            GC.collect()
            readsSwept[sample] = sweptObjectsAfterLastCollection()
            assertEquals(KompactCursor.STATUS_OK, cursor.status)

            allocationControlSwept[sample] = collectAfterAllocationControl(iterations)
        }

        val baselineMaximum = requireNotNull(baselineSwept.maxOrNull())
        val readsMaximum = requireNotNull(readsSwept.maxOrNull())
        val controlMinimum = requireNotNull(allocationControlSwept.minOrNull())
        val positiveControlDelta = controlMinimum - baselineMaximum
        println(
            "Native cursor byte-read allocation samples: baseline=${baselineSwept.contentToString()}, " +
                "reads=${readsSwept.contentToString()}, control=${allocationControlSwept.contentToString()}",
        )
        assertTrue(
            positiveControlDelta >= iterations,
            "Allocation control did not separate from baseline by $iterations objects: " +
                "baseline=${baselineSwept.contentToString()}, control=${allocationControlSwept.contentToString()}",
        )
        assertTrue(
            readsMaximum <= baselineMaximum,
            "Caller-owned cursor reads exceeded the primitive baseline: " +
                "baseline=${baselineSwept.contentToString()}, reads=${readsSwept.contentToString()}, " +
                "control=${allocationControlSwept.contentToString()}",
        )
        assertTrue(cursorAllocationChecksum != 0)
    }

    @Test
    fun callerOwnedCursorByteWritesStayWithinPrimitiveBaseline() {
        val iterations = 4_096
        val sampleCount = 3
        val baselineBuffer = ByteArray(iterations)
        val measuredBuffer = ByteArray(iterations)
        val cursor = KompactCursor(measuredBuffer)
        val baselineSwept = LongArray(sampleCount)
        val writesSwept = LongArray(sampleCount)
        val allocationControlSwept = LongArray(sampleCount)

        repeat(sampleCount) { sample ->
            GC.collect()
            runPrimitiveByteWrites(baselineBuffer, iterations)
            GC.collect()
            baselineSwept[sample] = sweptObjectsAfterLastCollection()

            assertEquals(KompactCursor.STATUS_OK, cursor.reset(measuredBuffer, endBit = iterations * Byte.SIZE_BITS))
            GC.collect()
            runCursorByteWrites(cursor, iterations)
            GC.collect()
            writesSwept[sample] = sweptObjectsAfterLastCollection()
            assertEquals(KompactCursor.STATUS_OK, cursor.status)

            allocationControlSwept[sample] = collectAfterAllocationControl(iterations)
        }

        val baselineMaximum = requireNotNull(baselineSwept.maxOrNull())
        val writesMaximum = requireNotNull(writesSwept.maxOrNull())
        val controlMinimum = requireNotNull(allocationControlSwept.minOrNull())
        val positiveControlDelta = controlMinimum - baselineMaximum
        println(
            "Native cursor byte-write allocation samples: baseline=${baselineSwept.contentToString()}, " +
                "writes=${writesSwept.contentToString()}, control=${allocationControlSwept.contentToString()}",
        )
        assertTrue(
            positiveControlDelta >= iterations,
            "Allocation control did not separate from baseline by $iterations objects: " +
                "baseline=${baselineSwept.contentToString()}, control=${allocationControlSwept.contentToString()}",
        )
        assertTrue(
            writesMaximum <= baselineMaximum,
            "Caller-owned cursor writes exceeded the primitive baseline: " +
                "baseline=${baselineSwept.contentToString()}, writes=${writesSwept.contentToString()}, " +
                "control=${allocationControlSwept.contentToString()}",
        )
        assertTrue(cursorAllocationChecksum != 0)
    }
}
