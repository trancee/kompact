package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KompactResultTest {

    // === KompactDecodeError (ticket 06) ===

    @Test
    fun boundsError_isSingleton() {
        assertSame(KompactDecodeError.BoundsError, KompactDecodeError.BoundsError)
    }

    @Test
    fun badLengthPrefix_isSingleton() {
        assertSame(KompactDecodeError.BadLengthPrefix, KompactDecodeError.BadLengthPrefix)
    }

    @Test
    fun truncatedNested_isSingleton() {
        assertSame(KompactDecodeError.TruncatedNested, KompactDecodeError.TruncatedNested)
    }

    @Test
    fun unknownEnumCode_preservesRawCode() {
        val err = KompactDecodeError.UnknownEnumCode(17)
        assertEquals(17, err.rawCode)
    }

    @Test
    fun unknownEnumCode_equalityByRawCode() {
        assertEquals(KompactDecodeError.UnknownEnumCode(5), KompactDecodeError.UnknownEnumCode(5))
        assertNotEquals(KompactDecodeError.UnknownEnumCode(5), KompactDecodeError.UnknownEnumCode(6))
    }

    // === ByteResult — representative ≤32-bit packed-Long type ===

    @Test
    fun byteResult_success_positiveValue() {
        val r = ByteResult.success(0x7F)
        assertTrue(r.isSuccess)
        assertFalse(r.isFailure)
        assertNull(r.error)
        assertEquals(0x7F, r.getOrThrow())
    }

    @Test
    fun byteResult_success_negativeValue() {
        val r = ByteResult.success(-1)
        assertTrue(r.isSuccess)
        assertEquals(-1, r.getOrThrow())
    }

    @Test
    fun byteResult_success_maxByte() {
        val r = ByteResult.success(Byte.MAX_VALUE)
        assertTrue(r.isSuccess)
        assertEquals(Byte.MAX_VALUE, r.getOrThrow())
    }

    @Test
    fun byteResult_success_minByte() {
        val r = ByteResult.success(Byte.MIN_VALUE)
        assertTrue(r.isSuccess)
        assertEquals(Byte.MIN_VALUE, r.getOrThrow())
    }

    @Test
    fun byteResult_roundTrip_allSignedBytes() {
        for (b in Byte.MIN_VALUE..Byte.MAX_VALUE) {
            val r = ByteResult.success(b.toByte())
            assertTrue(r.isSuccess, "byte=$b")
            assertEquals(b.toByte(), r.getOrThrow(), "byte=$b")
        }
    }

    @Test
    fun byteResult_failure_boundsError() {
        val r = ByteResult.failure(KompactDecodeError.BoundsError)
        assertFalse(r.isSuccess)
        assertTrue(r.isFailure)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun byteResult_failure_badLengthPrefix() {
        val r = ByteResult.failure(KompactDecodeError.BadLengthPrefix)
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BadLengthPrefix, r.error)
    }

    @Test
    fun byteResult_failure_truncatedNested() {
        val r = ByteResult.failure(KompactDecodeError.TruncatedNested)
        assertEquals(KompactDecodeError.TruncatedNested, r.error)
    }

    @Test
    fun byteResult_failure_unknownEnumCode_preservesRawCode() {
        val r = ByteResult.failure(KompactDecodeError.UnknownEnumCode(17))
        assertEquals(KompactDecodeError.UnknownEnumCode(17), r.error)
    }

    @Test
    fun byteResult_getOrThrow_throwsOnFailure() {
        val r = ByteResult.failure(KompactDecodeError.BoundsError)
        assertFailsWith<KompactDecodeException> { r.getOrThrow() }
    }

    @Test
    fun byteResult_equality() {
        assertEquals(ByteResult.success(42), ByteResult.success(42))
        assertEquals(ByteResult.failure(KompactDecodeError.BoundsError), ByteResult.failure(KompactDecodeError.BoundsError))
        assertNotEquals(ByteResult.success(42), ByteResult.failure(KompactDecodeError.BoundsError))
    }

    // === ShortResult ===

    @Test
    fun shortResult_roundTrip() {
        val values: List<Short> = listOf(0, 1, -1, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE)
        for (v in values) {
            val r = ShortResult.success(v)
            assertTrue(r.isSuccess, "value=$v")
            assertEquals(v, r.getOrThrow(), "value=$v")
        }
    }

    @Test
    fun shortResult_failure() {
        val r = ShortResult.failure(KompactDecodeError.BadLengthPrefix)
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BadLengthPrefix, r.error)
    }

    // === IntResult ===

    @Test
    fun intResult_roundTrip() {
        val values = listOf(0, 1, -1, 1008, -1008, Int.MAX_VALUE, Int.MIN_VALUE)
        for (v in values) {
            val r = IntResult.success(v)
            assertTrue(r.isSuccess, "value=$v")
            assertEquals(v, r.getOrThrow(), "value=$v")
        }
    }

    @Test
    fun intResult_failure_unknownEnumCode() {
        val r = IntResult.failure(KompactDecodeError.UnknownEnumCode(255))
        assertEquals(KompactDecodeError.UnknownEnumCode(255), r.error)
    }

    // === FloatResult ===

    @Test
    fun floatResult_roundTrip() {
        val r = FloatResult.success(3.14f)
        assertTrue(r.isSuccess)
        assertEquals(3.14f, r.getOrThrow(), 0.0001f)
    }

    @Test
    fun floatResult_negativeInfinity() {
        val r = FloatResult.success(Float.NEGATIVE_INFINITY)
        assertTrue(r.isSuccess)
        assertEquals(Float.NEGATIVE_INFINITY, r.getOrThrow())
    }

    @Test
    fun floatResult_positiveInfinity() {
        val r = FloatResult.success(Float.POSITIVE_INFINITY)
        assertTrue(r.isSuccess)
        assertEquals(Float.POSITIVE_INFINITY, r.getOrThrow())
    }

    @Test
    fun floatResult_canonicalNan() {
        val r = FloatResult.success(Float.NaN)
        assertTrue(r.isSuccess)
        assertTrue(r.getOrThrow().isNaN())
    }

    @Test
    fun floatResult_zero() {
        val r = FloatResult.success(0.0f)
        assertTrue(r.isSuccess)
        assertEquals(0.0f, r.getOrThrow())
    }

    @Test
    fun floatResult_failure() {
        val r = FloatResult.failure(KompactDecodeError.TruncatedNested)
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.TruncatedNested, r.error)
    }

    // === BooleanResult ===

    @Test
    fun booleanResult_success_true() {
        val r = BooleanResult.success(true)
        assertTrue(r.isSuccess)
        assertEquals(true, r.getOrThrow())
    }

    @Test
    fun booleanResult_success_false() {
        val r = BooleanResult.success(false)
        assertTrue(r.isSuccess)
        assertEquals(false, r.getOrThrow())
    }

    @Test
    fun booleanResult_failure() {
        val r = BooleanResult.failure(KompactDecodeError.BoundsError)
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    // === LongResult — 64-bit sentinel encoding ===

    @Test
    fun longResult_success_packsValue() {
        val r = LongResult.success(42L)
        assertTrue(r.isSuccess)
        assertFalse(r.isFailure)
        assertNull(r.error)
        assertEquals(42L, r.getOrThrow())
    }

    @Test
    fun longResult_success_negativeValue() {
        val r = LongResult.success(-1L)
        assertTrue(r.isSuccess)
        assertEquals(-1L, r.getOrThrow())
    }

    @Test
    fun longResult_success_maxValue() {
        val r = LongResult.success(Long.MAX_VALUE)
        assertTrue(r.isSuccess)
        assertEquals(Long.MAX_VALUE, r.getOrThrow())
    }

    @Test
    fun longResult_success_firstValueAboveSentinelRange() {
        // Sentinel range: bits 63 set + 62..58 clear (0x8000_0000_0000_0000
        // through 0x07FF_FFFF_FFFF_FFFF as signed). First representable success
        // value with bit 63 set: bit 58 also set → outside the sentinel mask.
        val firstAfter = Long.MIN_VALUE + (1L shl 58)
        val r = LongResult.success(firstAfter)
        assertTrue(r.isSuccess, "value falls outside sentinel range")
        assertEquals(firstAfter, r.getOrThrow())
    }

    @Test
    fun longResult_sentinelRangeNotRepresentableAsSuccess() {
        // Long.MIN_VALUE collides with the failure sentinel base — documented tradeoff.
        val r = LongResult.success(Long.MIN_VALUE)
        assertFalse(r.isSuccess, "Long.MIN_VALUE is in the failure sentinel range")
    }

    @Test
    fun longResult_roundTrip_arbitraryLongs() {
        val values = listOf(0L, 1L, -1L, 42L, -42L,
            Long.MAX_VALUE, 0x4000_0000_0000_0000L,
            Long.MIN_VALUE + (1L shl 58))
        for (v in values) {
            val r = LongResult.success(v)
            assertTrue(r.isSuccess, "value=$v packed=${v.toString(16)}")
            assertEquals(v, r.getOrThrow(), "value=$v")
        }
    }

    @Test
    fun longResult_failure_boundsError() {
        val r = LongResult.failure(KompactDecodeError.BoundsError)
        assertFalse(r.isSuccess)
        assertTrue(r.isFailure)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun longResult_failure_badLengthPrefix() {
        val r = LongResult.failure(KompactDecodeError.BadLengthPrefix)
        assertEquals(KompactDecodeError.BadLengthPrefix, r.error)
    }

    @Test
    fun longResult_failure_truncatedNested() {
        val r = LongResult.failure(KompactDecodeError.TruncatedNested)
        assertEquals(KompactDecodeError.TruncatedNested, r.error)
    }

    @Test
    fun longResult_failure_unknownEnumCode() {
        val r = LongResult.failure(KompactDecodeError.UnknownEnumCode(200))
        assertEquals(KompactDecodeError.UnknownEnumCode(200), r.error)
    }

    @Test
    fun longResult_getOrThrow_throwsOnFailure() {
        val r = LongResult.failure(KompactDecodeError.BoundsError)
        assertFailsWith<KompactDecodeException> { r.getOrThrow() }
    }

    @Test
    fun longResult_allFourErrorKinds_distinguishable() {
        val bounds = LongResult.failure(KompactDecodeError.BoundsError)
        val badLen = LongResult.failure(KompactDecodeError.BadLengthPrefix)
        val trunc = LongResult.failure(KompactDecodeError.TruncatedNested)
        val unknown = LongResult.failure(KompactDecodeError.UnknownEnumCode(42))

        assertEquals(KompactDecodeError.BoundsError, bounds.error)
        assertEquals(KompactDecodeError.BadLengthPrefix, badLen.error)
        assertEquals(KompactDecodeError.TruncatedNested, trunc.error)
        assertEquals(KompactDecodeError.UnknownEnumCode(42), unknown.error)
    }

    // === DoubleResult — 64-bit NaN error encoding ===

    @Test
    fun doubleResult_success_packsValue() {
        val r = DoubleResult.success(3.14)
        assertTrue(r.isSuccess)
        assertFalse(r.isFailure)
        assertNull(r.error)
        assertEquals(3.14, r.getOrThrow(), 0.0001)
    }

    @Test
    fun doubleResult_roundTrip_arbitraryDoubles() {
        val values = listOf(0.0, 1.0, -1.0, 3.14159, -2.71828,
            Double.MAX_VALUE, Double.MIN_VALUE, Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY, Double.NaN, -0.0)
        for (v in values) {
            val r = DoubleResult.success(v)
            assertTrue(r.isSuccess, "value=$v")
            if (v.isNaN()) {
                assertTrue(r.getOrThrow().isNaN(), "value=$v")
            } else {
                assertEquals(v, r.getOrThrow(), 0.0, "value=$v")
            }
        }
    }

    @Test
    fun doubleResult_failure_boundsError() {
        val r = DoubleResult.failure(KompactDecodeError.BoundsError)
        assertFalse(r.isSuccess)
        assertEquals(KompactDecodeError.BoundsError, r.error)
    }

    @Test
    fun doubleResult_failure_badLengthPrefix() {
        val r = DoubleResult.failure(KompactDecodeError.BadLengthPrefix)
        assertEquals(KompactDecodeError.BadLengthPrefix, r.error)
    }

    @Test
    fun doubleResult_failure_truncatedNested() {
        val r = DoubleResult.failure(KompactDecodeError.TruncatedNested)
        assertEquals(KompactDecodeError.TruncatedNested, r.error)
    }

    @Test
    fun doubleResult_failure_unknownEnumCode() {
        val r = DoubleResult.failure(KompactDecodeError.UnknownEnumCode(42))
        assertEquals(KompactDecodeError.UnknownEnumCode(42), r.error)
    }

    @Test
    fun doubleResult_getOrThrow_throwsOnFailure() {
        val r = DoubleResult.failure(KompactDecodeError.BoundsError)
        assertFailsWith<KompactDecodeException> { r.getOrThrow() }
    }

    @Test
    fun doubleResult_allFourErrorKinds_distinguishable() {
        val bounds = DoubleResult.failure(KompactDecodeError.BoundsError)
        val badLen = DoubleResult.failure(KompactDecodeError.BadLengthPrefix)
        val trunc = DoubleResult.failure(KompactDecodeError.TruncatedNested)
        val unknown = DoubleResult.failure(KompactDecodeError.UnknownEnumCode(42))

        assertEquals(KompactDecodeError.BoundsError, bounds.error)
        assertEquals(KompactDecodeError.BadLengthPrefix, badLen.error)
        assertEquals(KompactDecodeError.TruncatedNested, trunc.error)
        assertEquals(KompactDecodeError.UnknownEnumCode(42), unknown.error)
    }
}
