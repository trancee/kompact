package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec: .scratch/kompact-spec/issues/11-perf-evidence-plan.md
 *
 * JVM-side zero-allocation test for the read path. The test:
 *  1. Resets the [AllocationCounter].
 *  2. Reads scalars through the direct primitive path.
 *  3. Asserts 0 allocations.
 *
 * This is the JVM-side analog of iOS `assertNoAllocations { ... }`
 * (Ticket 11). On iOS, the precise gate is the alloc-instrumentation
 * runtime flag + `kotlin.test.assertNoAllocations` — the test
 * `KompactIosNoAllocTest` scaffold in `iosArm64Test/` compiles for
 * iOS targets and would assert the same property on a Mac host.
 */
class AllocationCounterViewTest {

    @Test
    fun readBits_does_not_allocate() {
        val buf = ByteArray(16)
        KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 8, value = 0xAB)
        val counter = AllocationCounter()
        counter.reset()
        repeat(1000) {
            assertEquals(0xAB, KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 8))
        }
        assertEquals(0L, counter.count(), "readBits must be zero-alloc on JVM")
    }

    @Test
    fun readBitsLong_does_not_allocate() {
        val buf = ByteArray(16)
        KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 64, value = 0x0123456789ABCDEFL)
        val counter = AllocationCounter()
        counter.reset()
        repeat(1000) {
            assertEquals(0x0123456789ABCDEFL, KompactRuntime.readBitsLong(buf, bitOffset = 0, bitWidth = 64))
        }
        assertEquals(0L, counter.count(), "readBitsLong must be zero-alloc on JVM")
    }

    @Test
    fun readBitsBoolean_does_not_allocate() {
        val buf = ByteArray(2)
        KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 1, value = 1)
        val counter = AllocationCounter()
        counter.reset()
        repeat(1000) { assertEquals(true, KompactRuntime.readBitsBoolean(buf, bitOffset = 0)) }
        assertEquals(0L, counter.count(), "readBitsBoolean must be zero-alloc on JVM")
    }
}
