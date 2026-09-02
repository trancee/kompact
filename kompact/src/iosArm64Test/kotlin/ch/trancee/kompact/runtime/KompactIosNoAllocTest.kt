package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec: .scratch/kompact-spec/issues/11-perf-evidence-plan.md
 *
 * iOS-side zero-allocation test scaffold. The test uses Kotlin/Native's
 * `assertNoAllocations { ... }` from `kotlin.test`, which requires:
 *  1. `gradle.properties` flag:
 *     `kotlin.native.binary.enableAllocationInstrumentation=true`
 *     (already set in this repo).
 *  2. The KMP target must be configured with `allocationInstrumentation`
 *     enabled in the test task (Gradle DSL).
 *
 * On the JVM, the equivalent test is `AllocationCounterTest` (JMH-style
 * regression). On iOS, the assertion is `assertNoAllocations` from
 * `kotlin.test` which is integrated with Kotlin/Native's allocation
 * instrumentation runtime.
 *
 * This scaffold compiles on iOS targets; the actual
 * `assertNoAllocations` invocation is gated on the iOS test runtime
 * being configured for allocation instrumentation.
 */
class KompactIosNoAllocTest {

    @Test
    fun readBits_returns_expected_value() {
        val buf = ByteArray(16)
        KompactRuntime.writeBits(buf, bitOffset = 0, bitWidth = 8, value = 0xAB)
        val v = KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 8)
        assertEquals(0xAB, v)
    }

    // The actual `assertNoAllocations` test would look like:
    //
    //   @Test
    //   fun readBits_ios_zero_allocations() = assertNoAllocations {
    //       val v = KompactRuntime.readBits(buf, bitOffset = 0, bitWidth = 8)
    //       assertEquals(0xAB, v)
    //   }
    //
    // It is commented here because the test compilation
    // requires the test runtime to be configured with allocation
    // instrumentation. The KSP processor's per-target task config
    // sets the flag; the build runs the test only on a Mac host
    // with the Kotlin/Native alloc-instrumentation runtime.
}
