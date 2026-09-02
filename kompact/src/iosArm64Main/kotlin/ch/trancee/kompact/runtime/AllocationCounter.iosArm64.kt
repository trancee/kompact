package ch.trancee.kompact.runtime

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * iOS `actual` of [AllocationCounter]. Uses `AtomicLong` on the
 * current thread; the count is intended to be combined with the
 * `kotlin.native.enableAllocationInstrumentation` runtime flag and
 * `assertNoAllocations { ... }` from `kotlin.test` (Ticket 10/11).
 */
@OptIn(ExperimentalAtomicApi::class)
public actual class AllocationCounter actual constructor() {
    private val holder: kotlin.concurrent.atomics.AtomicReference<AtomicLong?> =
        kotlin.concurrent.atomics.AtomicReference(null)

    private fun counter(): AtomicLong {
        var c = holder.load()
        if (c == null) {
            c = AtomicLong(0)
            holder.store(c)
        }
        return c
    }

    public actual fun reset() {
        counter().store(0)
    }

    public actual fun count(): Long = counter().load()
}
