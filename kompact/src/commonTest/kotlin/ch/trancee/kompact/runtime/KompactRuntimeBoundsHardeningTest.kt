package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertTrue

class KompactRuntimeBoundsHardeningTest {
    // === F-002: integer-overflow hardening (Ticket 06) ===
    // A buffer >= 2^28 bytes (256 MiB) makes `raw.size * 8` overflow signed Int to a
    // negative value, so Int-arithmetic bounds checks in fits()/readFloat()/
    // readDouble() false-negative in-bounds reads as BoundsError. The Long-
    // promoted checks must succeed. (Boundary test; requires -Xmx1g — see
    // kompact/build.gradle.kts.)
    @Test
    fun boundsChecks_survive256MiBBuffer() {
        val buf = ByteArray(1 shl 28) // 268_435_456 bytes -> 2^31 bits (Int-overflow threshold)
        // Extract results into locals before asserting, so Power-Assert's expression
        // diagram doesn't capture and attempt to stringify the 256 MiB ByteArray.
        val boolResult = KompactRuntime.readBool(buf, 0)
        val scalar8Result = KompactRuntime.readScalar(buf, 0, ScalarType.of(8, signed = false))
        val scalar32Result = KompactRuntime.readScalar(buf, 0, ScalarType.of(32, signed = true))
        val floatResult = KompactRuntime.readFloat(buf, 0)
        val doubleResult = KompactRuntime.readDouble(buf, 0)
        assertTrue(boolResult.isSuccess, "readBool must succeed on a 256 MiB buffer")
        assertTrue(scalar8Result.isSuccess, "readScalar/8 unsigned must succeed")
        assertTrue(scalar32Result.isSuccess, "readScalar/32 signed must succeed")
        assertTrue(floatResult.isSuccess, "readFloat must succeed")
        assertTrue(doubleResult.isSuccess, "readDouble must succeed")
    }
}
