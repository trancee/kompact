package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ticket 08: exercises every [ScalarType] companion constant so the property
 * initializers in the `actual` companion object are covered by JaCoCo/Kover.
 * Each constant is a pre-bound (bitWidth, signed) pair used by generated models
 * and checked accessors.
 */
class ScalarTypeCoverageTest {
    @Test
    fun companionConstants_encodeCorrectBitWidthAndSignedness() {
        assertEquals(1, ScalarType.BOOL.bitWidth)
        assertEquals(false, ScalarType.BOOL.signed)

        assertEquals(8, ScalarType.INT_8.bitWidth)
        assertEquals(true, ScalarType.INT_8.signed)

        assertEquals(8, ScalarType.UINT_8.bitWidth)
        assertEquals(false, ScalarType.UINT_8.signed)

        assertEquals(16, ScalarType.INT_16.bitWidth)
        assertEquals(true, ScalarType.INT_16.signed)

        assertEquals(16, ScalarType.UINT_16.bitWidth)
        assertEquals(false, ScalarType.UINT_16.signed)

        assertEquals(32, ScalarType.INT_32.bitWidth)
        assertEquals(true, ScalarType.INT_32.signed)

        assertEquals(32, ScalarType.UINT_32.bitWidth)
        assertEquals(false, ScalarType.UINT_32.signed)

        assertEquals(64, ScalarType.INT_64.bitWidth)
        assertEquals(true, ScalarType.INT_64.signed)

        assertEquals(64, ScalarType.UINT_64.bitWidth)
        assertEquals(false, ScalarType.UINT_64.signed)
    }
}
