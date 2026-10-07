package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidHostRuntimeBehaviorTest {
    @Test
    fun checkedScalarReadReturnsTheValueOrTheTypedBoundsFailure() {
        val value =
            KompactRuntime.readScalarOrThrow(
                byteArrayOf(0xCD.toByte(), 0xAB.toByte()),
                0,
                ScalarType.of(16, signed = false),
            )

        assertEquals(0xABCD, value)

        val failure =
            assertFailsWith<KompactDecodeException> {
                KompactRuntime.readScalarOrThrow(byteArrayOf(), 0, ScalarType.of(16, signed = false))
            }

        assertEquals(KompactDecodeError.BoundsError, failure.error)
    }
}
