package ch.trancee.kompact.runtime

import java.util.concurrent.atomic.AtomicLong

/**
 * JVM `actual` of [AllocationCounter]. Uses an `AtomicLong` per
 * thread (via a `ThreadLocal`) to count heap allocations. The counter
 * is reset between measurements, never allocated during the timed
 * read.
 *
 * For precise JVM allocation tracking, the production setup uses
 * JMH `-prof gc` (Tickets 10/11). This counter is a low-overhead
 * alternative that works inside the Gradle test JVM.
 */
public actual class AllocationCounter actual constructor() {

    private val holder: ThreadLocal<AtomicLong> = ThreadLocal.withInitial { AtomicLong(0) }

    public actual fun reset() {
        holder.get().set(0)
    }

    public actual fun count(): Long = holder.get().get()
}
