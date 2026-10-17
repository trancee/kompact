package ch.trancee.kompact.benchmark

import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertTrue

private class AllocationControl

private var retainedControls: List<AllocationControl>? = null
private var retainedChecksum: Int = 0

private fun allocateControls(count: Int) {
    retainedControls = List(count) { AllocationControl() }
    retainedChecksum = requireNotNull(retainedControls).size
    retainedControls = null
}

@OptIn(NativeRuntimeApi::class, ExperimentalStdlibApi::class)
class RepeatAllocationTest {
    @Test
    fun repeatIndexingMatchesPrimitiveSweepBaselineWithPositiveControls() {
        for (variable in listOf(false, true)) {
            val workload = RepeatWorkload(4096)
            val iterations = 1024
            val baseline = LongArray(3)
            val measured = LongArray(3)
            val control = LongArray(3)

            repeat(3) { sample ->
                GC.collect()
                var checksum = 0
                repeat(iterations) { checksum = checksum xor workload.fixedBytes[it].toInt() }
                retainedChecksum = checksum
                GC.collect()
                baseline[sample] = sweptObjects()

                GC.collect()
                checksum = 0
                repeat(iterations) {
                    checksum = checksum xor if (variable) workload.variableIndex() else workload.fixedIndex()
                }
                retainedChecksum = checksum
                GC.collect()
                measured[sample] = sweptObjects()

                GC.collect()
                allocateControls(iterations)
                GC.collect()
                control[sample] = sweptObjects()
            }

            println(
                "Native repeat allocation variable=$variable: baseline=${baseline.contentToString()}, " +
                    "measured=${measured.contentToString()}, control=${control.contentToString()}",
            )
            assertTrue(
                requireNotNull(control.minOrNull()) - requireNotNull(baseline.maxOrNull()) >= iterations,
                "The allocation control must be distinguishable from the primitive baseline.",
            )
            assertTrue(
                requireNotNull(measured.maxOrNull()) <= requireNotNull(baseline.maxOrNull()),
                "Repeat indexing exceeded its primitive sweep baseline: ${measured.contentToString()}",
            )
        }
    }

    private fun sweptObjects(): Long = requireNotNull(GC.lastGCInfo).sweepStatistics.values.sumOf { it.sweptCount }
}
