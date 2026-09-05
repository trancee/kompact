package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ticket 04: IEEE-754 floats carry a canonicalized NaN.
 * Ticket 08: LongResult reserves a sentinel band near Long.MIN_VALUE (documented tradeoff).
 */
class KompactResultCanonicalizationTest {

    // --- FloatResult NaN canonicalization (Ticket 04) — RED until fixed ---

    @Test
    fun floatResult_success_canonicalizesAnyNanToSinglePayload() {
        val canonicalBits = 0x7FC00000
        for (bits in listOf(0x7FC00001, 0x7FA00000, 0x7FF80001, 0xFFC00000L.toInt())) {
            val nan = Float.fromBits(bits)
            val r = FloatResult.success(nan)
            assertTrue(r.isSuccess, "bits=${bits.toString(16)}")
            assertTrue(r.getOrThrow().isNaN(), "bits=${bits.toString(16)}")
            assertEquals(
                canonicalBits,
                r.getOrThrow().toBits(),
                "non-canonical NaN bits=${bits.toString(16)}"
            )
        }
    }

    @Test
    fun floatResult_success_preservesNonNanValue() {
        val values = listOf(0.0f, -0.0f, 1.5f, -1.5f, Float.MAX_VALUE, Float.MIN_VALUE)
        for (v in values) {
            val r = FloatResult.success(v)
            assertTrue(r.isSuccess, "value=$v")
            assertEquals(v, r.getOrThrow(), "value=$v")
        }
    }

    // --- DoubleResult already canonicalizes; assert that contract holds ---

    @Test
    fun doubleResult_success_canonicalizesAnyNanToSinglePayload() {
        val canonical = Double.fromBits(0x7FF8000000000000L)
        for (bits in listOf(0x7FF8000000000001L, 0x7FF0000000000001L, 0x7FFA000000000001L)) {
            val nan = Double.fromBits(bits)
            val r = DoubleResult.success(nan)
            assertTrue(r.isSuccess, "bits=${bits.toString(16)}")
            assertTrue(r.getOrThrow().isNaN(), "bits=${bits.toString(16)}")
            assertEquals(
                canonical.toBits(),
                r.getOrThrow().toBits(),
                "non-canonical NaN bits=${bits.toString(16)}"
            )
        }
    }

    @Test
    fun doubleResult_success_preservesNonNanValue() {
        val values = listOf(0.0, -0.0, 1.5, -1.5, Double.MAX_VALUE, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY)
        for (v in values) {
            val r = DoubleResult.success(v)
            assertTrue(r.isSuccess, "value=$v")
            assertEquals(v, r.getOrThrow(), "value=$v")
        }
    }

    // --- LongResult sentinel band (Ticket 08: documented tradeoff) ---

    @Test
    fun longResult_sentinelRangeReservesLowBandNearMinValue() {
        // The failure sentinel band is Long.MIN_VALUE .. (Long.MIN_VALUE + (1L<<58) - 1):
        // bit 63 set (negative) with bits 62..58 clear. The first representable
        // success value with bit 63 set has bit 58 also set.
        val firstSuccessBelowMin = Long.MIN_VALUE + (1L shl 58)
        val r = LongResult.success(firstSuccessBelowMin)
        assertTrue(r.isSuccess, "first value past sentinel band must be representable")
        assertEquals(firstSuccessBelowMin, r.getOrThrow())
    }

    @Test
    fun longResult_sentinelRangeLongMinValueUnrepresentableAsSuccess() {
        val r = LongResult.success(Long.MIN_VALUE)
        assertFalse(r.isSuccess, "Long.MIN_VALUE is in the failure sentinel range (documented)")
    }

    // --- Read-path NaN canonicalization ---

    @Test
    fun readFloat_canonicalizesNanOnReadPath() {
        val buf = ByteArray(4)
        // 0x7FC00001 is a non-canonical NaN payload; canonical is 0x7FC00000.
        KompactRuntime.writeBitsLong(buf, 0, 32, 0x7FC00001L)
        val r = KompactRuntime.readFloat(buf, 0)
        assertTrue(r.isSuccess)
        assertTrue(r.getOrThrow().isNaN())
        assertEquals(0x7FC00000, r.getOrThrow().toBits(), "readFloat must canonicalize NaN")
    }
}
