package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KompactResultStateTest {
    @Test
    fun successResultsReportNoFailureAndNoError() {
        assertFalse(IntResult.success(0).isFailure)
        assertFalse(FloatResult.success(0f).isFailure)
        assertFalse(BooleanResult.success(false).isFailure)
        assertFalse(LongResult.success(0L).isFailure)
        assertFalse(DoubleResult.success(0.0).isFailure)
        assertFalse(NestedRegionResult.success(0, 0).isFailure)

        assertNull(IntResult.success(0).error)
        assertNull(FloatResult.success(0f).error)
        assertNull(BooleanResult.success(false).error)
        assertNull(LongResult.success(0L).error)
        assertNull(DoubleResult.success(0.0).error)
        assertNull(NestedRegionResult.success(0, 0).error)
    }

    @Test
    fun failureResultsReportFailure() {
        assertTrue(FloatResult.failure(KompactDecodeError.BoundsError).isFailure)
    }

    @Test
    fun longResultValuePreservesTheFullLongRange() {
        assertEquals(Long.MIN_VALUE, LongResult.success(Long.MIN_VALUE).value)
        assertNull(LongResult.failure(KompactDecodeError.BoundsError).value)
    }

    @Test
    fun floatResultPreservesNaNPayloadBits() {
        val payloadNaN = Float.fromBits(0x7FC0_1234)

        val result = FloatResult.success(payloadNaN)

        assertEquals(0x7FC0_1234, result.getOrThrow().toRawBits())
    }

    @Test
    fun nestedRegionResultExposesRegionOnSuccess() {
        val result = NestedRegionResult.success(Int.MAX_VALUE, Int.MAX_VALUE)

        assertEquals(Int.MAX_VALUE, result.startBit)
        assertEquals(Int.MAX_VALUE, result.bitLength)
    }

    @Test
    fun nestedRegionResultRegionAccessorsThrowOnFailure() {
        val result = NestedRegionResult.failure(KompactDecodeError.TruncatedNested)

        val start = assertFailsWith<KompactDecodeException> { result.startBit }
        val length = assertFailsWith<KompactDecodeException> { result.bitLength }

        assertEquals(KompactDecodeError.TruncatedNested, start.error)
        assertEquals(KompactDecodeError.TruncatedNested, length.error)
    }

    @Test
    fun nestedRegionResultRejectsNegativeRegions() {
        assertFailsWith<IllegalArgumentException> { NestedRegionResult.success(-1, 0) }
        assertFailsWith<IllegalArgumentException> { NestedRegionResult.success(0, -1) }
    }

    @Test
    fun scalarTypeRejectsOutOfRangeWidths() {
        assertFailsWith<IllegalArgumentException> { ScalarType.of(0, signed = false) }
        assertFailsWith<IllegalArgumentException> { ScalarType.of(65, signed = true) }
    }

    @Test
    fun decodeErrorsPrintTheirCaseName() {
        assertEquals("BoundsError", KompactDecodeError.BoundsError.toString())
        assertEquals("LongResult(error=InvalidUtf8)", LongResult.failure(KompactDecodeError.InvalidUtf8).toString())
    }
}
