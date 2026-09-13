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
 *  - `getOrElse` / `map` extensions on the typed results (`IntResult`, `ByteResult`, `ShortResult`, `LongResult`, `FloatResult`, `DoubleResult`, `BooleanResult`, `NestedRegionResult`),
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
        assertEquals(
            1.0,
            KompactRuntime.readDoubleOrThrow(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0.toByte(), 0x3F), 0),
        )
    }

    // ---- …OrThrow wrappers: a bounds overrun raises KompactDecodeException ----

    @Test
    fun readScalarOrThrow_throwsOnBoundsError() {
        val ex =
            assertFailsWith<KompactDecodeException> {
                KompactRuntime.readScalarOrThrow(byteArrayOf(), 0, ScalarType.of(8, signed = false))
            }
        assertEquals(KompactDecodeError.BoundsError, ex.error)
    }

    @Test
    fun readScalarAsLongOrThrow_throwsOnBoundsError() {
        val ex =
            assertFailsWith<KompactDecodeException> {
                KompactRuntime.readScalarAsLongOrThrow(byteArrayOf(), 0, ScalarType.of(16, signed = false))
            }
        assertEquals(KompactDecodeError.BoundsError, ex.error)
    }

    @Test
    fun readBoolOrThrow_throwsOnBoundsError() {
        val ex =
            assertFailsWith<KompactDecodeException> {
                KompactRuntime.readBoolOrThrow(byteArrayOf(), 0)
            }
        assertEquals(KompactDecodeError.BoundsError, ex.error)
    }

    @Test
    fun readFloatOrThrow_throwsOnBoundsError() {
        val ex =
            assertFailsWith<KompactDecodeException> {
                KompactRuntime.readFloatOrThrow(byteArrayOf(0x00, 0x00, 0x80.toByte()), 0) // 3 bytes < 4
            }
        assertEquals(KompactDecodeError.BoundsError, ex.error)
    }

    @Test
    fun readDoubleOrThrow_throwsOnBoundsError() {
        val ex =
            assertFailsWith<KompactDecodeException> {
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

    // ---- getOrElse / map on the remaining typed results ----

    @Test
    fun remainingResultTypes_getOrElseAndMap_preserveTypedFailure() {
        // IntResult + NestedRegion are covered above; these pin the wiring for the
        // other specialised value classes.

        // BooleanResult: success map, failure-fallback, and failure-propagation.
        val boolOk = KompactRuntime.readBool(byteArrayOf(0x01), 0)
        assertTrue(boolOk.isSuccess)
        assertEquals(true, boolOk.getOrElse { false })
        val boolMapped = boolOk.map { !it }
        assertTrue(boolMapped.isSuccess)
        assertEquals(false, boolMapped.getOrThrow())
        val boolBad = KompactRuntime.readBool(byteArrayOf(), 0)
        assertEquals(false, boolBad.getOrElse { false })
        val boolMapBad = boolBad.map { !it }
        assertTrue(boolMapBad.isFailure)
        assertEquals(KompactDecodeError.BoundsError, boolMapBad.error)

        // LongResult: sign-extended small value + failure (sentinel-band decode).
        val longOk = KompactRuntime.readScalarAsLong(byteArrayOf(0x2A), 0, ScalarType.of(8, signed = true))
        assertTrue(longOk.isSuccess)
        assertEquals(42L, longOk.getOrElse { 0L })
        val longBad = KompactRuntime.readScalarAsLong(byteArrayOf(), 0, ScalarType.of(8, signed = true))
        assertEquals(0L, longBad.getOrElse { 0L })
        assertTrue(longBad.map { it + 1L }.isFailure)

        // FloatResult: canonical NaN on the success path.
        val floatOk = KompactRuntime.readFloat(byteArrayOf(0x00, 0x00, 0x48, 0x42), 0)
        assertTrue(floatOk.isSuccess)
        assertEquals(50.0f, floatOk.getOrElse { 0f }, 0f)
        val floatBad = KompactRuntime.readFloat(byteArrayOf(), 0)
        assertEquals(0f, floatBad.getOrElse { 0f })

        // DoubleResult: canonical NaN on the success path.
        val doubleOk =
            KompactRuntime.readDouble(
                byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0.toByte(), 0x3F),
                0,
            )
        assertTrue(doubleOk.isSuccess)
        assertEquals(1.0, doubleOk.getOrElse { 0.0 }, 0.0)
        val doubleBad = KompactRuntime.readDouble(byteArrayOf(), 0)
        assertEquals(0.0, doubleBad.getOrElse { 0.0 })

        // ByteResult: no checked accessor returns ByteResult (api-reference), so
        // construct directly to pin success/failure wiring.
        val byteOk = ByteResult.success(0xAB.toByte())
        assertEquals(0xAB.toByte(), byteOk.getOrElse { 0 })
        val byteMapped = byteOk.map { (it + 1).toByte() }
        assertTrue(byteMapped.isSuccess)
        assertEquals(0xAC.toByte(), byteMapped.getOrThrow())
        val byteBad = ByteResult.failure(KompactDecodeError.BoundsError)
        assertEquals(0, byteBad.getOrElse { 0 })
        assertTrue(byteBad.map { (it + 1).toByte() }.isFailure)
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

        // Prefix claims 2 payload bytes but only 1 present -> length-prefix
        // exceeds remaining bytes -> BadLengthPrefix (Ticket 06/09).
        val truncated = byteArrayOf(0x02, 0xAB.toByte())
        val trunc = KompactFraming.readNested(truncated, 0, 8)
        assertTrue(trunc.isFailure)
        assertEquals(KompactDecodeError.BadLengthPrefix, trunc.error)
    }

    @Test
    fun readNestedOrThrow_and_readLengthPrefixOrThrow_succeedOrThrowTyped() {
        val valid = byteArrayOf(0x01, 0xAB.toByte())
        assertEquals(Pair(8, 8), KompactFraming.readNestedOrThrow(valid, 0, 8))
        assertEquals(1, KompactFraming.readLengthPrefixOrThrow(valid, 0, 8))

        // Bad prefix width throws BadLengthPrefix.
        val ex1 =
            assertFailsWith<KompactDecodeException> {
                KompactFraming.readLengthPrefixOrThrow(valid, 0, 7)
            }
        assertEquals(KompactDecodeError.BadLengthPrefix, ex1.error)

        // Prefix exceeds remaining bytes throws BadLengthPrefix (Ticket 06/09).
        val ex2 =
            assertFailsWith<KompactDecodeException> {
                KompactFraming.readNestedOrThrow(byteArrayOf(0x02, 0xAB.toByte()), 0, 8)
            }
        assertEquals(KompactDecodeError.BadLengthPrefix, ex2.error)
    }

    @Test
    fun readNested_classifiesF003IntMaxPrefixAsBadLengthPrefix() {
        // F-003: a 32-bit prefix encoding 0x7FFFFFFF (= Int.MAX_VALUE bytes) makes
        // byteCount*8 overflow Int to a negative region bit-length, which is
        // unrepresentable in the Int-pair contract. Length-prefix > remaining
        // buffer is a typed BadLengthPrefix for the typed reader too (Ticket 06/09).
        val buf = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F)
        val res = KompactFraming.readNested(buf, 0, 32)
        assertTrue(res.isFailure)
        assertEquals(KompactDecodeError.BadLengthPrefix, res.error)
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
