package ch.trancee.kompact.runtime

import ch.trancee.kompact.generated.MutableVehicleTelemetry
import ch.trancee.kompact.generated.VehicleTelemetry
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class AllocationProbe(val value: Int)

private var retainedAllocationProbes: List<AllocationProbe>? = null

private fun retainAllocationProbes(count: Int) {
    retainedAllocationProbes = List(count, ::AllocationProbe)
}

private fun lastRetainedAllocationProbeValue(): Int =
    requireNotNull(retainedAllocationProbes).last().value

private fun releaseAllocationProbes() {
    retainedAllocationProbes = null
}

private fun allocateAndReleaseProbes(count: Int) {
    retainAllocationProbes(count)
    releaseAllocationProbes()
}

private var allocationMeasurementChecksum = 0

private fun runPrimitiveBaseline(buffer: ByteArray, count: Int) {
    var checksum = 0
    repeat(count) {
        buffer[0] = (buffer[0].toInt() + 1).toByte()
        checksum += buffer[0].toInt()
    }
    allocationMeasurementChecksum = checksum
}

private fun runSpeedReads(buffer: ByteArray, count: Int) {
    val telemetry = VehicleTelemetry(buffer)
    var checksum = 0
    repeat(count) {
        buffer[0] = (buffer[0].toInt() + 1).toByte()
        checksum += telemetry.speed
    }
    allocationMeasurementChecksum = checksum
}

private fun runSpeedWrites(buffer: ByteArray, count: Int) {
    val telemetry = MutableVehicleTelemetry(buffer)
    var checksum = 0
    repeat(count) {
        telemetry.speed = it and 0x3ff
        checksum += buffer[1].toInt() and 0xff
    }
    allocationMeasurementChecksum = checksum
}

@OptIn(NativeRuntimeApi::class, ExperimentalStdlibApi::class)
class NativeAllocationCounterProbeTest {
    private fun sweptObjectsAfterLastCollection(): Long =
        requireNotNull(GC.lastGCInfo).sweepStatistics.values.sumOf { it.sweptCount }

    private fun collectAfterPrimitiveBaseline(buffer: ByteArray, count: Int): Long {
        GC.collect()
        runPrimitiveBaseline(buffer, count)
        GC.collect()
        return sweptObjectsAfterLastCollection()
    }

    private fun collectAfterSpeedReads(buffer: ByteArray, count: Int): Long {
        GC.collect()
        runSpeedReads(buffer, count)
        GC.collect()
        return sweptObjectsAfterLastCollection()
    }

    private fun collectAfterSpeedWrites(buffer: ByteArray, count: Int): Long {
        GC.collect()
        runSpeedWrites(buffer, count)
        GC.collect()
        return sweptObjectsAfterLastCollection()
    }

    private fun collectAfterAllocationControl(count: Int): Long {
        GC.collect()
        allocateAndReleaseProbes(count)
        GC.collect()
        return sweptObjectsAfterLastCollection()
    }

    @Test
    fun garbageCollectorSweepStatisticsObserveKnownAllocations() {
        val allocationCount = 2_048

        retainAllocationProbes(allocationCount)
        assertEquals(allocationCount - 1, lastRetainedAllocationProbeValue())
        GC.collect()
        val retainedStatistics = requireNotNull(GC.lastGCInfo).sweepStatistics
        val keptCount = retainedStatistics.values.sumOf { it.keptCount }
        assertTrue(
            keptCount >= allocationCount,
            "Expected GC statistics to retain at least $allocationCount control objects, found $retainedStatistics",
        )

        releaseAllocationProbes()
        GC.collect()
        val releasedStatistics = requireNotNull(GC.lastGCInfo).sweepStatistics
        val sweptCount = releasedStatistics.values.sumOf { it.sweptCount }
        println("Native allocation probe: keptCount=$keptCount; sweptCount=$sweptCount")
        assertTrue(
            sweptCount >= allocationCount,
            "Expected GC statistics to sweep at least $allocationCount control objects, found $releasedStatistics",
        )
    }

    @Test
    fun directGeneratedSpeedReadsStayWithinPrimitiveBaselineNoise() {
        val iterations = 4_096
        val sampleCount = 3
        val buffer = byteArrayOf(0, 0)
        val baselineSwept = LongArray(sampleCount)
        val readsSwept = LongArray(sampleCount)
        val allocationControlSwept = LongArray(sampleCount)

        repeat(sampleCount) { sample ->
            baselineSwept[sample] = collectAfterPrimitiveBaseline(buffer, iterations)
            readsSwept[sample] = collectAfterSpeedReads(buffer, iterations)
            allocationControlSwept[sample] = collectAfterAllocationControl(iterations)
        }

        val baselineMaximum = requireNotNull(baselineSwept.maxOrNull())
        val readsMaximum = requireNotNull(readsSwept.maxOrNull())
        val controlMinimum = requireNotNull(allocationControlSwept.minOrNull())
        val positiveControlDelta = controlMinimum - baselineMaximum
        println(
            "Native speed-read allocation samples: baseline=${baselineSwept.contentToString()}, " +
                "reads=${readsSwept.contentToString()}, control=${allocationControlSwept.contentToString()}",
        )
        assertTrue(
            positiveControlDelta >= iterations,
            "Allocation control did not separate from baseline by $iterations objects: " +
                "baseline=${baselineSwept.contentToString()}, control=${allocationControlSwept.contentToString()}",
        )
        assertTrue(
            readsMaximum <= baselineMaximum,
            "Generated speed reads exceeded the primitive baseline: " +
                "baseline=${baselineSwept.contentToString()}, reads=${readsSwept.contentToString()}, " +
                "control=${allocationControlSwept.contentToString()}",
        )
        assertTrue(allocationMeasurementChecksum != 0)
    }

    @Test
    fun directGeneratedSpeedWritesStayWithinPrimitiveBaselineNoise() {
        val iterations = 4_096
        val sampleCount = 3
        val buffer = byteArrayOf(0, 0)
        val baselineSwept = LongArray(sampleCount)
        val writesSwept = LongArray(sampleCount)
        val allocationControlSwept = LongArray(sampleCount)

        repeat(sampleCount) { sample ->
            baselineSwept[sample] = collectAfterPrimitiveBaseline(buffer, iterations)
            writesSwept[sample] = collectAfterSpeedWrites(buffer, iterations)
            allocationControlSwept[sample] = collectAfterAllocationControl(iterations)
        }

        val baselineMaximum = requireNotNull(baselineSwept.maxOrNull())
        val writesMaximum = requireNotNull(writesSwept.maxOrNull())
        val controlMinimum = requireNotNull(allocationControlSwept.minOrNull())
        val positiveControlDelta = controlMinimum - baselineMaximum
        println(
            "Native speed-write allocation samples: baseline=${baselineSwept.contentToString()}, " +
                "writes=${writesSwept.contentToString()}, control=${allocationControlSwept.contentToString()}",
        )
        assertTrue(
            positiveControlDelta >= iterations,
            "Allocation control did not separate from baseline by $iterations objects: " +
                "baseline=${baselineSwept.contentToString()}, control=${allocationControlSwept.contentToString()}",
        )
        assertTrue(
            writesMaximum <= baselineMaximum,
            "Generated speed writes exceeded the primitive baseline: " +
                "baseline=${baselineSwept.contentToString()}, writes=${writesSwept.contentToString()}, " +
                "control=${allocationControlSwept.contentToString()}",
        )
        assertTrue(allocationMeasurementChecksum != 0)
    }

}
