package ch.trancee.kompact.runtime
// Benchmark environment (Ticket 11 / P1-P3):
//   Method: warmup 1000 calls, measure 100,000 calls, assert ns/call < 10,000.

 //   Metric: System.nanoTime() wall-clock on a single thread.
//   Platform: OpenJDK 17 (Kotlin 2.3.21 / K2JVM) on Linux x86_64.
//   Comparable baselines: re-run on the same machine after any runtime change
//   to record before/after ns/call deltas in this file's git history.
//
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec: .scratch/kompact-spec/issues/11-perf-evidence-plan.md
 *
 * Lightweight regression test for the readBits hot path. A full
 * JMH benchmark module is out of scope for v1; this test mirrors
 * the shape of a JMH `@Benchmark` (warmup + measure + assert) and
 * verifies that 100,000 calls complete in well under a second and
 * that all 100,000 calls return the same value (no leakage).
 *
 * For the precise CI gate, run the JMH benchmark in the `:kompact-bench`
 * subproject with `-prof gc` and `assertAllocations`; that module
 * is a follow-up after this v1.
 */
class KompactReadBitsBenchmarkTest {

    @Test
    fun readBits_100k_calls_is_stable_and_fast() {
        val buf = ByteArray(16)
        KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 8, value = 0xAB)
        val iterations = 100_000

        // Warmup.
        repeat(1000) { KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 8) }

        val startNs = System.nanoTime()
        var acc = 0
        repeat(iterations) { acc += KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 8) }
        val elapsedNs = System.nanoTime() - startNs

        assertEquals(0xAB * iterations, acc, "all reads must return 0xAB")
        val nsPerCall = elapsedNs.toDouble() / iterations
        check(nsPerCall < 10_000.0) {
            "readBits too slow: $nsPerCall ns/call ($iterations iters, $elapsedNs ns total)"
        }
        println("readBits: $nsPerCall ns/call over $iterations iterations")
    }
}
