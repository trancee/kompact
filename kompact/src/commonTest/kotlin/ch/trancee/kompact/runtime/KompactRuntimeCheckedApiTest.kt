package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for the checked-accessor ergonomics that the migrated
 * CheckedReadTest/WriterTest/GettingStartedTest do NOT cover (Tickets 04/05/08):
 *
 *  - the five `…OrThrow` wrappers (return the decoded value, or throw
 *    `KompactDecodeException(BoundsError)` when the buffer overruns),
 *  - `getOrElse` / `map` extensions on `IntResult` and `NestedRegionResult`,
 *  - the typed nested framing: `readNested`, `readNestedOrThrow`,
 *    `readLengthPrefixOrThrow`, and `NestedRegionResult`'s success/failure shape.
 *
 * Same package as `ScalarType`/`KompactDecodeError`/`KompactDecodeException`
 * -> no cross-package imports required.
 */
class KompactRuntimeCheckedApiTest {

    // ---- …OrThrow wrappers: success path returns the decoded value ----

    @Test
    fun readBoolOrThrow_returnsValueOnSuccess() {
        assertEquals(true, KompactRuntime.readBoolOrThrow(byteArrayOf(0x01), 0))
        assertEquals(false, KompactRuntime.readBoolOrThrow(byteArrayOf(0x00), 0))
    }

    @Test
    fun readScalarOrThrow_returnsValueOnSuccess() {
        assertEquals(
            0xAB,
            KompactRuntime.readScalarOrThrow(byteArrayOf(0xAB.toByte()), 0, ScalarType.of(8, signed = false)),
        )
    }

    @Test
    fun readScalarAsLongOrThrow_returnsValueOnSuccess() {
        // 0xABCD little-endian across 2 bytes.
        assertEquals(
            0xABCDL,
            KompactRuntime.readScalarAsLongOrThrow(
                byteArrayOf(0xCD.toByte(), 0xAB.toByte()),
                0,
                ScalarType.of(16, signed = false),
            ),
        )
    }

    @Test
    fun readFloatOrThrow_returnsValueOnSuccess() {
        // 1.0f == 0x3F800000, little-endian bytes.
        assertEquals(1.0f, KompactRuntime.readFloatOrThrow(byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F), 0))
    }

    @Test
    fun readDoubleOrThrow_returnsValueOnSuccess() {
        // 1.0 == 0x3FF0000000000000, little-endian bytes.
        assertEquals(1.0, KompactRuntime.readDoubleOrThrow(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0.toByte(), 0x3F), 0))
    }

    // ---- …OrThrow wrappers: a bounds overrun raises KompactDecodeException ----

    @Test
    fun readScalarOrThrow_throwsOnBoundsError() {
        val ex = assertFailsWith<KompactDecodeException> {
            KompactRuntime.readScalarOrThrow(byteArrayOf(), 0, ScalarType.of(8, signed = false))
        }
        assertEquals(KompactDecodeError.BoundsError, ex.error)
    }

    @Test
    fun readScalarAsLongOrThrow_throwsOnBoundsError() {
        val ex = assertFailsWith<KompactDecodeException> {
            KompactRuntime.readScalarAsLongOrThrow(byteArrayOf(), 0, ScalarType.of(16, signed = false))
        }
        assertEquals(KompactDecodeError.BoundsError, ex.error)
    }

    @Test
    fun readBoolOrThrow_throwsOnBoundsError() {
        val ex = assertFailsWith<KompactDecodeException> {
            KompactRuntime.readBoolOrThrow(byteArrayOf(), 0)
        }
        assertEquals(KompactDecodeError.BoundsError, ex.error)
    }

    @Test
    fun readFloatOrThrow_throwsOnBoundsError() {
        val ex = assertFailsWith<KompactDecodeException> {
            KompactRuntime.readFloatOrThrow(byteArrayOf(0x00, 0x00, 0x80.toByte()), 0) // 3 bytes < 4
        }
        assertEquals(KompactDecodeError.BoundsError, ex.error)
    }

    @Test
    fun readDoubleOrThrow_throwsOnBoundsError() {
        val ex = assertFailsWith<KompactDecodeException> {
            KompactRuntime.readDoubleOrThrow(byteArrayOf(0x00, 0x00, 0x00, 0x00), 0) // 4 bytes < 8
        }
        assertEquals(KompactDecodeError.BoundsError, ex.error)
    }

    // ---- getOrElse / map on IntResult ----

    @Test
    fun intResult_getOrElse_returnsValueOrFallback() {
        val ok = KompactRuntime.readScalar(byteArrayOf(0xAB.toByte()), 0, ScalarType.of(8, signed = false))
        assertEquals(0xAB, ok.getOrElse { 0 })

        val bad = KompactRuntime.readScalar(byteArrayOf(), 0, ScalarType.of(8, signed = false))
        assertEquals(
            -1,
            bad.getOrElse { err ->
                // The fallback receives the typed error — not a re-decode.
                assertEquals(KompactDecodeError.BoundsError, err)
                -1
            },
        )
    }

    @Test
    fun intResult_map_transformsSuccessAndPropagatesFailure() {
        val ok = KompactRuntime.readScalar(byteArrayOf(0x05), 0, ScalarType.of(8, signed = false))
        val mapped = ok.map { it * 2 }
        assertTrue(mapped.isSuccess)
        assertEquals(10, mapped.getOrThrow())

        val bad = KompactRuntime.readScalar(byteArrayOf(), 0, ScalarType.of(8, signed = false))
        val mappedBad = bad.map { it * 2 }
        assertTrue(mappedBad.isFailure)
        assertEquals(KompactDecodeError.BoundsError, mappedBad.error)
    }

    // ---- readNested / readNestedOrThrow / readLengthPrefixOrThrow / NestedRegionResult ----

    @Test
    fun readNested_parsesValidRegionAndClassifiedFailures() {
        // 8-bit count prefix (=1) + 1 payload byte (0xAB): region [8, 8).
        val valid = byteArrayOf(0x01, 0xAB.toByte())
        val ok = KompactFraming.readNested(valid, 0, 8)
        assertTrue(ok.isSuccess)
        assertEquals(8, ok.startBit)
        assertEquals(8, ok.bitLength)
        assertEquals(Pair(8, 8), ok.getOrThrow())

        // Bad prefix width (7 ∉ 8/16/32) -> BadLengthPrefix.
        val badWidth = KompactFraming.readNested(valid, 0, 7)
        assertTrue(badWidth.isFailure)
        assertEquals(KompactDecodeError.BadLengthPrefix, badWidth.error)

        // Prefix claims 2 payload bytes but only 1 present -> TruncatedNested.
        val truncated = byteArrayOf(0x02, 0xAB.toByte())
        val trunc = KompactFraming.readNested(truncated, 0, 8)
        assertTrue(trunc.isFailure)
        assertEquals(KompactDecodeError.TruncatedNested, trunc.error)
    }

    @Test
    fun readNestedOrThrow_and_readLengthPrefixOrThrow_succeedOrThrowTyped() {
        val valid = byteArrayOf(0x01, 0xAB.toByte())
        assertEquals(Pair(8, 8), KompactFraming.readNestedOrThrow(valid, 0, 8))
        assertEquals(1, KompactFraming.readLengthPrefixOrThrow(valid, 0, 8))

        // Bad prefix width throws BadLengthPrefix.
        val ex1 = assertFailsWith<KompactDecodeException> {
            KompactFraming.readLengthPrefixOrThrow(valid, 0, 7)
        }
        assertEquals(KompactDecodeError.BadLengthPrefix, ex1.error)

        // Truncated region throws TruncatedNested.
        val ex2 = assertFailsWith<KompactDecodeException> {
            KompactFraming.readNestedOrThrow(byteArrayOf(0x02, 0xAB.toByte()), 0, 8)
        }
        assertEquals(KompactDecodeError.TruncatedNested, ex2.error)
    }

    @Test
    fun nestedRegionResult_getOrElse_and_map() {
        val ok = NestedRegionResult.success(8, 8)
        assertEquals(Pair(8, 8), ok.getOrElse { Pair(-1, -1) })
        val grown = ok.map { Pair(it.first + 4, it.second) }
        assertTrue(grown.isSuccess)
        assertEquals(Pair(12, 8), grown.getOrThrow())

        val bad = NestedRegionResult.failure(KompactDecodeError.TruncatedNested)
        assertEquals(Pair(-1, -1), bad.getOrElse { Pair(-1, -1) })
        val mappedBad = bad.map { Pair(it.first + 1, it.second) }
        assertTrue(mappedBad.isFailure)
        assertEquals(KompactDecodeError.TruncatedNested, mappedBad.error)
    }
}
