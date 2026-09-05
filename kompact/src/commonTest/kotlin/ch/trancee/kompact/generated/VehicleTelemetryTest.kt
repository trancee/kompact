package ch.trancee.kompact.generated

import ch.trancee.kompact.runtime.KompactRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VehicleTelemetryTest {

    // PROMPT §3 layout (LSB-first, 16 bits):
    // [0..3] batteryStatus (4 bits), [4..13] speed (10 bits),
    // [14] isMalfunctioning (1 bit), [15] reserved.

    @Test
    fun decodesLsbFirstPackedFields() {
        val buf = ByteArray(2)
        KompactRuntime.writeBits(buf, 0, 4, 5)
        KompactRuntime.writeBits(buf, 4, 10, 10)
        KompactRuntime.writeBitsBoolean(buf, 14, true)

        val tel = VehicleTelemetry(buf)
        val batteryStatus = tel.batteryStatus
        val speed = tel.speed
        val isMalfunctioning = tel.isMalfunctioning

        assertEquals(5, batteryStatus)
        assertEquals(10, speed)
        assertEquals(true, isMalfunctioning)
        assertEquals(0xA5, buf[0].toInt() and 0xFF) // wire bytes match LSB-first layout
        assertEquals(0x40, buf[1].toInt() and 0xFF)
    }

    @Test
    fun roundTripsArbitraryValues() {
        val buf = ByteArray(2)
        KompactRuntime.writeBits(buf, 0, 4, 9)
        KompactRuntime.writeBits(buf, 4, 10, 517)
        KompactRuntime.writeBitsBoolean(buf, 14, true)

        val tel = VehicleTelemetry(buf)
        val batteryStatus = tel.batteryStatus
        val speed = tel.speed
        val isMalfunctioning = tel.isMalfunctioning

        assertEquals(9, batteryStatus)
        assertEquals(517, speed)
        assertEquals(true, isMalfunctioning)
    }

    @Test
    fun decodesFromPreencodedWireBytes() {
        val tel = VehicleTelemetry(byteArrayOf(0xA5.toByte(), 0x40.toByte()))
        val batteryStatus = tel.batteryStatus
        val speed = tel.speed
        val isMalfunctioning = tel.isMalfunctioning

        assertEquals(5, batteryStatus)
        assertEquals(10, speed)
        assertEquals(true, isMalfunctioning)
    }

    @Test
    fun isMalfunctioning_falseOnZeroByte() {
        val tel = VehicleTelemetry(byteArrayOf(0x00, 0x00))
        val isMalfunctioning = tel.isMalfunctioning
        assertEquals(false, isMalfunctioning)
    }

    @Test
    fun reservedBit_doesNotLeakIntoReads() {
        val tel = VehicleTelemetry(byteArrayOf(0x00, 0x80.toByte())) // bit 15 (reserved) set
        val batteryStatus = tel.batteryStatus
        val speed = tel.speed
        val isMalfunctioning = tel.isMalfunctioning

        assertEquals(0, batteryStatus)
        assertEquals(0, speed)
        assertEquals(false, isMalfunctioning)
    }

    // === F-001: constructor must validate the in-format buffer precondition ===
    // The 16-bit layout ([0..15]) requires >= 2 bytes. A truncated/untrusted
    // buffer must fail fast at construction with a clear IllegalArgumentException
    // (fail-fast, Ticket 06) rather than a delayed ArrayIndexOutOfBoundsException
    // at field-access time. The raw getters remain the zero-alloc fast path
    // (Ticket 08:39); untrusted input should use the checked accessors instead.

    @Test
    fun constructorRejectsTruncatedBuffer() {
        assertFailsWith<IllegalArgumentException> { VehicleTelemetry(ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { VehicleTelemetry(ByteArray(1)) }
    }

    @Test
    fun constructorAcceptsInFormatBuffer() {
        val tel = VehicleTelemetry(ByteArray(2))
        assertEquals(0, tel.batteryStatus)
        assertEquals(0, tel.speed)
        assertEquals(false, tel.isMalfunctioning)
    }
}
