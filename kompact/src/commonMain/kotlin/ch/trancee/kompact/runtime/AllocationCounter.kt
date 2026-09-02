package ch.trancee.kompact.runtime

/**
 * Spec: .scratch/kompact-spec/issues/11-perf-evidence-plan.md
 *
 * Allocation counter for verifying the zero-allocation read path
 * (Ticket 03). The platform `actual` records the number of heap
 * allocations on the current thread. Reset/measure is OUTSIDE the
 * timed read region (the timed read is the only call we care about;
 * reset+count are themselves allocations).
 */
public expect class AllocationCounter() {
    public fun reset()
    public fun count(): Long
}
