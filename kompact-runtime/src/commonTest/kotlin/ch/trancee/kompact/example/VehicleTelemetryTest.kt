package ch.trancee.kompact.example

import ch.trancee.kompact.runtime.KompactDecodeResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VehicleTelemetryTest {
    @Test
    fun writesAndReadsCanonicalCrossPlatformPacket() {
        val packet = ByteArray(VehicleTelemetry.PACKET_BYTE_SIZE)
        val writer = VehicleTelemetry.initialize(packet).successValue()

        assertEquals(null, writer.writeBatteryStatus(BatteryStatus.CRITICAL))
        assertEquals(null, writer.writeSpeed(511u))
        assertEquals(null, writer.writeIsMalfunctioning(true))

        assertContentEquals(byteArrayOf(0x2A, 0x00, 0xF2.toByte(), 0x5F), packet)
        val view = writer.view()
        assertEquals(BatteryStatus.CRITICAL, view.batteryStatus)
        assertEquals(511u, view.speed)
        assertTrue(view.isMalfunctioning)
    }
}

private fun <T> KompactDecodeResult<T>.successValue(): T =
    when (this) {
        is KompactDecodeResult.Success -> value
        is KompactDecodeResult.Failure -> error(error.toString())
    }
