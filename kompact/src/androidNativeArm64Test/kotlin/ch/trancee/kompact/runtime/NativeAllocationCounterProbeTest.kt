package ch.trancee.kompact.runtime

import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class AllocationProbe(val value: Int)

private var retainedAllocationProbes: List<AllocationProbe>? = null

@OptIn(NativeRuntimeApi::class, ExperimentalStdlibApi::class)
class NativeAllocationCounterProbeTest {
    @Test
    fun garbageCollectorSweepStatisticsObserveKnownAllocations() {
        val allocationCount = 2_048

        retainedAllocationProbes = List(allocationCount, ::AllocationProbe)
        assertEquals(allocationCount - 1, requireNotNull(retainedAllocationProbes).last().value)
        GC.collect()
        val retainedStatistics = requireNotNull(GC.lastGCInfo).sweepStatistics
        val keptCount = retainedStatistics.values.sumOf { it.keptCount }
        println("Native allocation probe: keptCount=$keptCount; pools=$retainedStatistics")
        assertTrue(
            keptCount >= allocationCount,
            "Expected GC statistics to retain at least $allocationCount control objects, found $retainedStatistics",
        )

        retainedAllocationProbes = null
        GC.collect()
        val releasedStatistics = requireNotNull(GC.lastGCInfo).sweepStatistics
        val sweptCount = releasedStatistics.values.sumOf { it.sweptCount }
        println("Native allocation probe: sweptCount=$sweptCount; pools=$releasedStatistics")
        assertTrue(
            sweptCount >= allocationCount,
            "Expected GC statistics to sweep at least $allocationCount control objects, found $releasedStatistics",
        )
    }
}
