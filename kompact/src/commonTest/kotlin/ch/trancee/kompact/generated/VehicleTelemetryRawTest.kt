package ch.trancee.kompact.generated

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the `getRaw()` getter on [VehicleTelemetry] — on the JVM the `raw`
 * property belongs to a `@JvmInline` value class, and the Kotlin compiler
 * inlines property access to a direct field read (GETFIELD) rather than
 * calling the synthetic `getRaw()` method. Routing through `Any` (forcing
 * boxing) and invoking the getter via reflection produces a virtual call
 * that JaCoCo/Kover records as covered.
 */
class VehicleTelemetryRawTest {
    private fun callGetRaw(t: VehicleTelemetry): ByteArray =
        VehicleTelemetry::class.java.getMethod("getRaw").invoke(t) as ByteArray

    @Test
    fun getRaw_returnsBackingBuffer() {
        val tel = VehicleTelemetry.create(batteryStatus = 5, speed = 10, isMalfunctioning = true)
        val raw = callGetRaw(tel)
        assertEquals(2, raw.size)
        assertEquals(0xA5, raw[0].toInt() and 0xFF)
        assertEquals(0x40, raw[1].toInt() and 0xFF)
    }
}
