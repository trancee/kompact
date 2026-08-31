package ch.trancee.kompact.example

import ch.trancee.kompact.runtime.KompactDecodeError
import ch.trancee.kompact.runtime.KompactDecodeResult
import ch.trancee.kompact.runtime.KompactStatusCode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class VehicleTelemetryValidationTest {
    @Test
    fun rejectsPacketShorterThanEnvelope() {
        val result = VehicleTelemetry.wrap(byteArrayOf(0x2A))

        val failure = assertIs<KompactDecodeResult.Failure>(result)
        assertIs<KompactDecodeError.InvalidPacketLength>(failure.error)
    }

    @Test
    fun rejectsUnknownSchemaBeforeLength() {
        val packet = byteArrayOf(0x2B, 0x00)

        val result = VehicleTelemetry.wrap(packet)

        val failure = assertIs<KompactDecodeResult.Failure>(result)
        assertEquals(KompactStatusCode.UNKNOWN_SCHEMA_ID, failure.error.status)
    }

    @Test
    fun rejectsReservedSchemaIdBeforeUnknownIdentity() {
        val packet = byteArrayOf(0x00, 0x00)

        val result = VehicleTelemetry.wrap(packet)

        val failure = assertIs<KompactDecodeResult.Failure>(result)
        assertEquals(KompactStatusCode.RESERVED_SCHEMA_ID, failure.error.status)
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val packet = byteArrayOf(0x2A, 0x10)

        val result = VehicleTelemetry.wrap(packet)

        val failure = assertIs<KompactDecodeResult.Failure>(result)
        assertEquals(KompactStatusCode.UNSUPPORTED_LAYOUT_VERSION, failure.error.status)
    }

    @Test
    fun rejectsExtraPacketBytesAfterEnvelopeValidation() {
        val packet = byteArrayOf(0x2A, 0x00, 0x00, 0x00, 0x00)

        val result = VehicleTelemetry.wrap(packet)

        val failure = assertIs<KompactDecodeResult.Failure>(result)
        assertEquals(KompactStatusCode.INVALID_PACKET_LENGTH, failure.error.status)
    }

    @Test
    fun rejectsNonzeroReservedBit() {
        val packet = byteArrayOf(0x2A, 0x00, 0x00, 0x80.toByte())

        val result = VehicleTelemetry.wrap(packet)

        val failure = assertIs<KompactDecodeResult.Failure>(result)
        assertEquals(KompactStatusCode.NONZERO_RESERVED_BITS, failure.error.status)
    }

    @Test
    fun rejectsUnknownBatteryStatus() {
        val packet = byteArrayOf(0x2A, 0x00, 0x0F, 0x00)

        val result = VehicleTelemetry.wrap(packet)

        val failure = assertIs<KompactDecodeResult.Failure>(result)
        assertEquals(KompactStatusCode.UNKNOWN_ENUM_CODE, failure.error.status)
    }

    @Test
    fun reportsLowestBodyBitFailureFirst() {
        val packet = byteArrayOf(0x2A, 0x00, 0x0F, 0x80.toByte())

        val result = VehicleTelemetry.wrap(packet)

        val failure = assertIs<KompactDecodeResult.Failure>(result)
        assertEquals(KompactStatusCode.UNKNOWN_ENUM_CODE, failure.error.status)
    }

    @Test
    fun rejectedSpeedWritePreservesPacket() {
        val packet = ByteArray(VehicleTelemetry.PACKET_BYTE_SIZE)
        val writer =
            assertIs<KompactDecodeResult.Success<VehicleTelemetryWriter>>(
                    VehicleTelemetry.initialize(packet)
                )
                .value
        val before = packet.copyOf()

        val error = writer.writeSpeed(1024u)

        assertEquals(KompactStatusCode.VALUE_OUT_OF_RANGE, error?.status)
        assertContentEquals(before, packet)
    }

    @Test
    fun editReusesValidatedPacket() {
        val packet = byteArrayOf(0x2A, 0x00, 0xF2.toByte(), 0x5F)

        val result = VehicleTelemetry.edit(packet)

        assertIs<KompactDecodeResult.Success<VehicleTelemetryWriter>>(result)
    }

    @Test
    fun editPropagatesValidationFailure() {
        val packet = byteArrayOf(0x2B, 0x00, 0x00, 0x00)

        val result = VehicleTelemetry.edit(packet)

        assertIs<KompactDecodeResult.Failure>(result)
    }

    @Test
    fun contentComparisonUsesPacketBytesExplicitly() {
        val first = VehicleTelemetry.wrap(byteArrayOf(0x2A, 0x00, 0xF2.toByte(), 0x5F)).success()
        val second = VehicleTelemetry.wrap(byteArrayOf(0x2A, 0x00, 0xF2.toByte(), 0x5F)).success()

        assertFalse(first == second)
        assertEquals(true, first.contentEquals(second))
        assertEquals(first.contentHashCode(), second.contentHashCode())
    }
}

private fun KompactDecodeResult<VehicleTelemetryView>.success(): VehicleTelemetryView =
    assertIs<KompactDecodeResult.Success<VehicleTelemetryView>>(this).value
