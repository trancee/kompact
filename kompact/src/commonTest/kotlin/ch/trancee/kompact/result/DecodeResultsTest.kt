package ch.trancee.kompact.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec: .scratch/kompact-spec/issues/08-runtime-error-model.md
 *
 * The result value classes pack value + ok-flag + error code into a
 * single Long. Success: low 56 bits = value, bit 56 = 1. Failure: low
 * 56 bits = 0, bit 56 = 0, bits 57..60 = error code.
 */
class DecodeResultsTest {

    @Test
    fun byteResult_success_carries_value_and_ok() {
        val r = ByteResult.success(42)
        assertTrue(r.isOk)
        assertFalse(r.isError)
        assertEquals(0, r.errorCode)
        assertEquals(42, r.value)
    }

    @Test
    fun byteResult_failure_carries_error_code() {
        val r = ByteResult.failure(KompactError.BoundsError)
        assertFalse(r.isOk)
        assertTrue(r.isError)
        assertEquals(KompactError.BoundsError, r.errorCode)
    }

    @Test
    fun intResult_handles_full_int_range() {
        val r = IntResult.success(Int.MIN_VALUE)
        assertTrue(r.isOk)
        assertEquals(Int.MIN_VALUE, r.value)

        val r2 = IntResult.success(Int.MAX_VALUE)
        assertTrue(r2.isOk)
        assertEquals(Int.MAX_VALUE, r2.value)
    }
    @Test
    fun longResult_handles_unsigned_56_bit_range() {
        // The packed Long uses 8 high bits for ok-flag + error code, so the
        // value range is the unsigned 56-bit range (0 .. 2^56 - 1). Larger
        // Longs (e.g. negative values, full 64-bit precision) cannot fit
        // alongside the ok-flag and error code; that is the documented
        // tradeoff of Ticket 08's single-Long packing.
        val max = (1L shl 56) - 1L
        assertTrue(LongResult.success(0L).isOk)
        assertEquals(0L, LongResult.success(0L).value)
        assertTrue(LongResult.success(max).isOk)
        assertEquals(max, LongResult.success(max).value)
    }

    @Test
    fun booleanResult_success_true_and_false() {
        assertTrue(BooleanResult.success(true).value)
        assertFalse(BooleanResult.success(false).value)
        assertTrue(BooleanResult.success(true).isOk)
    }

    @Test
    fun booleanResult_failure_is_error() {
        val r = BooleanResult.failure(KompactError.UnknownEnumCode)
        assertTrue(r.isError)
        assertEquals(KompactError.UnknownEnumCode, r.errorCode)
    }

    @Test
    fun result_companions_round_trip_through_long() {
        // The packed representation is the source of truth.
        val r1 = IntResult.success(0x12345678)
        val packed = r1.toLong()
        val r2 = IntResult(packed)
        assertTrue(r2.isOk)
        assertEquals(0x12345678, r2.value)
    }
}
