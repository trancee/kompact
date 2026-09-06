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
        assertTrue(KompactRuntime.readBool(buf, 0).isSuccess, "readBool must succeed on a 256 MiB buffer")
        assertTrue(KompactRuntime.readScalar(buf, 0, ScalarType.of(8, signed = false)).isSuccess, "readScalar/8 unsigned must succeed")
        assertTrue(KompactRuntime.readScalar(buf, 0, ScalarType.of(32, signed = true)).isSuccess, "readScalar/32 signed must succeed")
        assertTrue(KompactRuntime.readFloat(buf, 0).isSuccess, "readFloat must succeed")
        assertTrue(KompactRuntime.readDouble(buf, 0).isSuccess, "readDouble must succeed")
    }
}
