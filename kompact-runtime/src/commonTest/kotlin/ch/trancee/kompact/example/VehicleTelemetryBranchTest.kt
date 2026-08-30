package ch.trancee.kompact.example

import ch.trancee.kompact.runtime.KompactDecodeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VehicleTelemetryBranchTest {
    @Test
    fun initializeRejectsWrongPacketSizeWithoutMutation() {
        val packet = byteArrayOf(0x55)

        val result = VehicleTelemetry.initialize(packet)

        assertIs<KompactDecodeResult.Failure>(result)
        assertEquals(0x55.toByte(), packet.single())
    }

    @Test
    fun readsNormalBatteryStatus() {
        val view = VehicleTelemetry.wrap(byteArrayOf(0x2A, 0x00, 0x00, 0x00)).success()

        assertEquals(BatteryStatus.NORMAL, view.batteryStatus)
    }

    @Test
    fun readsLowBatteryStatus() {
        val view = VehicleTelemetry.wrap(byteArrayOf(0x2A, 0x00, 0x01, 0x00)).success()

        assertEquals(BatteryStatus.LOW, view.batteryStatus)
    }
}

private fun KompactDecodeResult<VehicleTelemetryView>.success(): VehicleTelemetryView =
    when (this) {
        is KompactDecodeResult.Success -> value
        is KompactDecodeResult.Failure -> error(error.toString())
    }
