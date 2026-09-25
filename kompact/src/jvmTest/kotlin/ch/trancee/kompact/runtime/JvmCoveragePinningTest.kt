package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-only test class that delegates to [JvmCoveragePinning] Java helpers.
 *
 * The Java helpers generate genuine {@code INVOKEVIRTUAL} calls (for
 * {@code getPacked} / {@code getRaw}) and {@code INVOKESTATIC} calls (for
 * {@code $default} bridges) that the Kotlin compiler would otherwise optimise
 * away (to {@code GETFIELD} for inline value-class properties, or resolve at
 * compile time for default parameters).
 *
 * The {@code JvmCoveragePinning} helper itself lives in {@code jvmMain/java}
 * so that the KMP plugin compiles it before the test Kotlin sources.
 */
class JvmCoveragePinningTest {
    @Test
    fun intResult_getPacked_covered() {
        val r = JvmCoveragePinning.boxInt(JvmCoveragePinning.smallSuccess(7L))
        assertEquals(JvmCoveragePinning.smallSuccess(7L), JvmCoveragePinning.getIntPacked(r))
    }

    @Test
    fun longResult_getPacked_covered() {
        val r = JvmCoveragePinning.boxLong(99L)
        assertEquals(99L, JvmCoveragePinning.getLongPacked(r))
    }

    @Test
    fun floatResult_getPacked_covered() {
        val bits = java.lang.Float.floatToRawIntBits(1.0f)
        val r = JvmCoveragePinning.boxFloat(JvmCoveragePinning.smallSuccess(bits.toLong()))
        assertEquals(JvmCoveragePinning.smallSuccess(bits.toLong()), JvmCoveragePinning.getFloatPacked(r))
    }

    @Test
    fun doubleResult_getPacked_covered() {
        val bits = java.lang.Double.doubleToRawLongBits(1.5)
        val r = JvmCoveragePinning.boxDouble(bits)
        assertEquals(bits, JvmCoveragePinning.getDoublePacked(r))
    }

    @Test
    fun booleanResult_getPacked_true_covered() {
        val r = JvmCoveragePinning.boxBoolean(JvmCoveragePinning.smallSuccess(1L))
        assertEquals(JvmCoveragePinning.smallSuccess(1L), JvmCoveragePinning.getBooleanPacked(r))
    }

    @Test
    fun booleanResult_getPacked_false_covered() {
        val r = JvmCoveragePinning.boxBoolean(JvmCoveragePinning.smallSuccess(0L))
        assertEquals(JvmCoveragePinning.smallSuccess(0L), JvmCoveragePinning.getBooleanPacked(r))
    }

    @Test
    fun scalarType_getPacked_covered() {
        val r = JvmCoveragePinning.boxScalarType(17)
        assertEquals(17, JvmCoveragePinning.getScalarTypePacked(r))
    }

    @Test
    fun nestedRegionResult_getPacked_covered() {
        val r = JvmCoveragePinning.boxNestedRegion(JvmCoveragePinning.smallSuccess(42L))
        assertEquals(JvmCoveragePinning.smallSuccess(42L), JvmCoveragePinning.getNestedRegionPacked(r))
    }

    @Test
    fun vehicleTelemetry_getRaw_covered() {
        val raw = byteArrayOf(0x01, 0x02, 0x03)
        val vt = JvmCoveragePinning.boxVehicleTelemetry(raw)
        val result = JvmCoveragePinning.getVehicleRaw(vt)
        assertEquals(3, result.size)
        assertEquals(0x01, result[0].toInt())
        assertEquals(0x02, result[1].toInt())
        assertEquals(0x03, result[2].toInt())
    }

    @Test
    fun mutableVehicleTelemetry_getRaw_covered() {
        // The Kotlin compiler lowers `mvt.raw` to a GETFIELD (backing-field)
        // read for @JvmInline value classes, so the synthetic `getRaw()`
        // accessor is never reachable from Kotlin sources alone. The Java
        // helper below emits a genuine INVOKEVIRTUAL that Kover can record.
        val raw = byteArrayOf(0xA5.toByte(), 0x40.toByte())
        val mvt = JvmCoveragePinning.boxMutableVehicleTelemetry(raw)
        val result = JvmCoveragePinning.getMutableVehicleRaw(mvt)
        assertEquals(2, result.size)
        assertContentEquals(raw, result)
    }

    @Test
    fun kompactObjectsInitialize_covered() {
        JvmCoveragePinning.initializeKompactObjects()
    }

    @Test
    fun writeNested_defaultBridgeCalled() {
        val bytes = JvmCoveragePinning.writeNestedDefault(16, 0x1)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun writeRepeated_defaultBridgeCalled() {
        val bytes = JvmCoveragePinning.writeRepeatedDefault(0, 8, 0x2)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun longResult_map_bridgeCovered() {
        val results =
            JvmCoveragePinning.callMapBridge(
                JvmCoveragePinning.longSuccess42(),
                JvmCoveragePinning.longFailure(),
            )
        // Success path: map(42, +1) → 43
        assertEquals(43L, results[0])
        // Failure path: map returns packed unchanged
        assertEquals(JvmCoveragePinning.longFailure(), results[1])
    }

    @Test
    fun longResult_getOrThrow_failurePathFromJava() {
        try {
            JvmCoveragePinning.callLongGetOrThrow(JvmCoveragePinning.longFailure())
            throw AssertionError("Expected KompactDecodeException")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            assertTrue(e.cause is KompactDecodeException)
        }
    }

    @Test
    fun detailedError_unknownEnumCode_propagatesRawCode() {
        // decodeFull* only surface BoundsError, so detailedError's UnknownEnumCode
        // arm is unreachable via the public API; drive it here via reflection
        // (mirrors callLongGetOrThrow) and assert the rawCode + byte offset.
        val err = JvmCoveragePinning.callDetailedErrorWithUnknownEnumCode(0x0BAD, 0x42)
        assertEquals(0x0BAD, err.rawCode)
        assertEquals(0x42 ushr 3, err.offset)
        val kind = err.error
        assertTrue(kind is KompactDecodeError.UnknownEnumCode)
        assertEquals(0x0BAD, kind.rawCode)
    }
}
