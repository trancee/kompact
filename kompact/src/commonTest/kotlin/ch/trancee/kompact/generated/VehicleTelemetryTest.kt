package ch.trancee.kompact.generated

import ch.trancee.kompact.runtime.KompactRuntime
import kotlin.test.Test
import kotlin.test.assertContentEquals
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

    // === create() factory ===

    @Test
    fun create_roundTripsDecodedFields() {
        val tel = VehicleTelemetry.create(batteryStatus = 5, speed = 10, isMalfunctioning = true)

        assertEquals(5, tel.batteryStatus)
        assertEquals(10, tel.speed)
        assertEquals(true, tel.isMalfunctioning)
    }

    @Test
    fun create_encodesFieldsToExpectedWireBytes() {
        val tel = VehicleTelemetry.create(batteryStatus = 5, speed = 10, isMalfunctioning = true)

        assertEquals(0xA5, tel.raw[0].toInt() and 0xFF)
        assertEquals(0x40, tel.raw[1].toInt() and 0xFF)
    }

    @Test
    fun create_producesTwoByteBuffer() {
        val tel = VehicleTelemetry.create(batteryStatus = 0, speed = 0, isMalfunctioning = false)
        assertEquals(2, tel.raw.size)
    }

    @Test
    fun create_allZeroProducesZeroBytes() {
        val tel = VehicleTelemetry.create(batteryStatus = 0, speed = 0, isMalfunctioning = false)
        assertContentEquals(byteArrayOf(0x00, 0x00), tel.raw)
    }

    // === copy() builder (ADR-0006 D2) ===

    @Test
    fun copy_overridesOnlySpecifiedFields() {
        val tel = VehicleTelemetry.create(batteryStatus = 5, speed = 10, isMalfunctioning = true)

        val modified = tel.copy(speed = 30) // override one field, preserve the rest

        assertEquals(5, modified.batteryStatus) // preserved
        assertEquals(30, modified.speed) // overridden
        assertEquals(true, modified.isMalfunctioning) // preserved
        // copy allocates a fresh buffer; the original frame is untouched
        assertEquals(0xA5, tel.raw[0].toInt() and 0xFF)
        assertEquals(0x40, tel.raw[1].toInt() and 0xFF)
    }

    // === MutableVehicleTelemetry write-through setters (opt-in, ADR-0006 D3) ===
    // The default view is immutable (val); mutation goes through the
    // MutableVehicleTelemetry sibling, which writes fields in place on `raw`.

    @Test
    fun batteryStatusSetter_writesLowNibbleWithoutAffectingSpeed() {
        val tel = MutableVehicleTelemetry.create(batteryStatus = 5, speed = 10, isMalfunctioning = true)
        tel.batteryStatus = 3

        assertEquals(3, tel.batteryStatus)
        assertEquals(10, tel.speed) // unchanged
        assertEquals(true, tel.isMalfunctioning) // unchanged
        assertEquals(0xA3, tel.raw[0].toInt() and 0xFF) // low nibble changed, high nibble preserved
    }

    @Test
    fun speedSetter_writesTenBitField() {
        val tel = MutableVehicleTelemetry.create(batteryStatus = 5, speed = 10, isMalfunctioning = true)
        tel.speed = 1023 // max 10-bit value

        assertEquals(1023, tel.speed)
        assertEquals(5, tel.batteryStatus) // unchanged
        assertEquals(true, tel.isMalfunctioning) // unchanged
    }

    @Test
    fun isMalfunctioningSetter_clearsAndSetsBit() {
        val tel = MutableVehicleTelemetry.create(batteryStatus = 5, speed = 10, isMalfunctioning = true)
        tel.isMalfunctioning = false

        assertEquals(false, tel.isMalfunctioning)
        assertEquals(0x00, tel.raw[1].toInt() and 0x40) // bit 14 cleared
    }

    @Test
    fun modifyAfterCreate_preservesUnchangedFields() {
        val tel = MutableVehicleTelemetry.create(batteryStatus = 7, speed = 500, isMalfunctioning = false)
        tel.speed = 25

        assertEquals(7, tel.batteryStatus)
        assertEquals(25, tel.speed)
        assertEquals(false, tel.isMalfunctioning)
    }

    // === BLE workflow: receive (immutable decode) → modify (Mutable sibling) → retransmit ===

    @Test
    fun bleWorkflow_modifyOneFieldAndReadRaw() {
        // Simulate receiving a frame from a BLE characteristic (immutable decode)
        val received = VehicleTelemetry(byteArrayOf(0xA5.toByte(), 0x40.toByte()))
        assertEquals(5, received.batteryStatus)
        assertEquals(10, received.speed)
        assertEquals(true, received.isMalfunctioning)

        // Modify a field in place by wrapping the same backing buffer in the
        // opt-in Mutable sibling (no allocation, re-send the same bytes).
        val mutable = MutableVehicleTelemetry(received.raw)
        mutable.speed = 30

        // Read raw bytes for retransmission — already in wire format
        val bytesToSend = mutable.raw
        assertEquals(2, bytesToSend.size)

        // Verify the modified frame decodes correctly
        val reDecoded = VehicleTelemetry(bytesToSend)
        assertEquals(5, reDecoded.batteryStatus)
        assertEquals(30, reDecoded.speed)
        assertEquals(true, reDecoded.isMalfunctioning)
    }

    // === MutableVehicleTelemetry F-001 guard + raw accessor ===
    // The immutable view's require-guard failure branch is covered by
    // constructorRejectsTruncatedBuffer above; the Mutable sibling enforces the
    // same precondition and must fail fast on a truncated buffer too.

    @Test
    fun mutableConstructorRejectsTruncatedBuffer() {
        assertFailsWith<IllegalArgumentException> { MutableVehicleTelemetry(ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { MutableVehicleTelemetry(ByteArray(1)) }
    }

    @Test
    fun mutableRawBufferIsReadable() {
        val mutable = MutableVehicleTelemetry.create(batteryStatus = 5, speed = 10, isMalfunctioning = true)

        assertEquals(2, mutable.raw.size)
        assertContentEquals(byteArrayOf(0xA5.toByte(), 0x40.toByte()), mutable.raw)
    }
}
