package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Edge-case tests for getOrElse / map on result types whose failure branches
 * were not exercised by KompactRuntimeCheckedApiTest:
 *
 *  - ShortResult.getOrElse / ShortResult.map (missing entirely)
 *  - FloatResult.map (success map + failure propagation)
 *  - DoubleResult.map (success map + failure propagation)
 */
class KompactResultExtensionsEdgeCaseTest {
    // --- ShortResult ---

    @Test
    fun shortResult_getOrElse_returnsValueOrFallback() {
        val ok = ShortResult.success(42)
        assertEquals(42, ok.getOrElse { 0 })

        val bad = ShortResult.failure(KompactDecodeError.BoundsError)
        assertEquals(
            -1,
            bad.getOrElse { err ->
                assertEquals(KompactDecodeError.BoundsError, err)
                -1
            },
        )
    }

    @Test
    fun shortResult_map_transformsSuccessAndPropagatesFailure() {
        val ok = ShortResult.success(5)
        val mapped = ok.map { (it * 2).toShort() }
        assertTrue(mapped.isSuccess)
        assertEquals(10, mapped.getOrThrow())

        val bad = ShortResult.failure(KompactDecodeError.BoundsError)
        val mappedBad = bad.map { (it + 1).toShort() }
        assertTrue(mappedBad.isFailure)
        assertEquals(KompactDecodeError.BoundsError, mappedBad.error)
    }

    // --- FloatResult.map (getOrElse already covered by CheckedApiTest) ---

    @Test
    fun floatResult_map_transformsSuccessAndPropagatesFailure() {
        val ok = FloatResult.success(2.5f)
        val mapped = ok.map { it * 2 }
        assertTrue(mapped.isSuccess)
        assertEquals(5.0f, mapped.getOrThrow(), 0.0001f)

        val bad = FloatResult.failure(KompactDecodeError.BoundsError)
        val mappedBad = bad.map { it + 1f }
        assertTrue(mappedBad.isFailure)
        assertEquals(KompactDecodeError.BoundsError, mappedBad.error)
    }

    @Test
    fun floatResult_map_preservesCanonicalNan() {
        val nanOk = FloatResult.success(Float.NaN)
        val mapped = nanOk.map { it + 1f }
        assertTrue(mapped.isSuccess)
        assertTrue(mapped.getOrThrow().isNaN())
    }

    // --- DoubleResult.map (getOrElse already covered by CheckedApiTest) ---

    @Test
    fun doubleResult_map_transformsSuccessAndPropagatesFailure() {
        val ok = DoubleResult.success(1.5)
        val mapped = ok.map { it * 2 }
        assertTrue(mapped.isSuccess)
        assertEquals(3.0, mapped.getOrThrow(), 0.0)

        val bad = DoubleResult.failure(KompactDecodeError.BoundsError)
        val mappedBad = bad.map { it + 1.0 }
        assertTrue(mappedBad.isFailure)
        assertEquals(KompactDecodeError.BoundsError, mappedBad.error)
    }

    @Test
    fun doubleResult_map_preservesCanonicalNan() {
        val nanOk = DoubleResult.success(Double.NaN)
        val mapped = nanOk.map { it + 1.0 }
        assertTrue(mapped.isSuccess)
        assertTrue(mapped.getOrThrow().isNaN())
    }
}
