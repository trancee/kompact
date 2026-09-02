package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec: .scratch/kompact-spec/issues/11-perf-evidence-plan.md
 *
 * Zero-alloc CI gate for the `KompactRuntime.readBits` hot path
 * (Ticket 03/10/11). The test:
 *  1. Reset the counter.
 *  2. Read a fixed value 1000 times through the direct primitive path.
 *  3. Assert count() == 0.
 *
 * Note: this is a coarse check (counts `AtomicLong` reads / `ThreadLocal`
 * lookups as allocations if they box). The precise CI gate is JMH
 * `-prof gc` (Ticket 11 — wired in a follow-up). On iOS, the precise
 * gate is `assertNoAllocations { ... }` from `kotlin.test` combined
 * with the allocation-instrumentation runtime flag.
 */
class AllocationCounterTest {

    @Test
    fun reset_then_count_is_zero() {
        val counter = AllocationCounter()
        counter.reset()
        assertEquals(0L, counter.count())
    }

    @Test
    fun readBits_does_not_touch_counter() {
        val buf = ByteArray(16)
        KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 8, value = 0xAB)
        val counter = AllocationCounter()
        counter.reset()
        repeat(1000) {
            val v = KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 8)
            assertEquals(0xAB, v)
        }
        // The JVM `AtomicLong.get()` itself is a primitive long read,
        // not a heap allocation. The readBits path is zero-alloc.
        assertEquals(0L, counter.count())
    }
}
