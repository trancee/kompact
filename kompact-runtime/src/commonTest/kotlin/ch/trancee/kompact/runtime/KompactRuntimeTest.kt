package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class KompactRuntimeTest {
    @Test
    fun readsUnsignedBitsAcrossByteBoundary() {
        val packet = byteArrayOf(0xB0.toByte(), 0x2A)

        val value = KompactRuntime.readBits(packet, bitOffset = 4, bitWidth = 10)

        assertEquals(0x2ABuL, value)
    }

    @Test
    fun writesUnsignedBitsAcrossByteBoundaryWithoutChangingNeighbors() {
        val packet = byteArrayOf(0x0F, 0xC0.toByte())

        val error = KompactRuntime.writeBits(packet, bitOffset = 4, bitWidth = 10, value = 0x155uL)

        assertEquals(null, error)
        assertEquals(listOf(0x5F.toByte(), 0xD5.toByte()), packet.toList())
    }

    @Test
    fun signExtendsNarrowSignedValues() {
        val packet = byteArrayOf(0xFA.toByte())

        val value = KompactRuntime.readSignedBits(packet, bitOffset = 1, bitWidth = 7)

        assertEquals(-3L, value)
    }

    @Test
    fun rejectsOutOfRangeWriteWithoutMutation() {
        val packet = byteArrayOf(0x55, 0x2A)
        val before = packet.copyOf()

        val error = KompactRuntime.writeBits(packet, bitOffset = 4, bitWidth = 5, value = 32uL)

        assertEquals(KompactStatusCode.VALUE_OUT_OF_RANGE, error?.status)
        assertContentEquals(before, packet)
    }

    @Test
    fun readsBooleanAtUnalignedOffset() {
        val packet = byteArrayOf(0x20)

        val value = KompactRuntime.readBitsBoolean(packet, bitOffset = 5)

        assertEquals(true, value)
    }

    @Test
    fun writesNarrowSignedValueWithoutChangingNeighbors() {
        val packet = byteArrayOf(0x01)

        val error = KompactRuntime.writeSignedBits(packet, bitOffset = 1, bitWidth = 7, value = -3)

        assertEquals(null, error)
        assertEquals(0xFB.toByte(), packet.single())
    }

    @Test
    fun writesBooleanWithoutChangingNeighbors() {
        val packet = byteArrayOf(0xA5.toByte())

        KompactRuntime.writeBitsBoolean(packet, bitOffset = 4, value = true)

        assertEquals(0xB5.toByte(), packet.single())
    }

    @Test
    fun decodeErrorsExposeStableStatusCode() {
        val error: KompactDecodeError =
            KompactDecodeError.InvalidPacketLength(expectedLength = 4, actualLength = 3)

        assertEquals(KompactStatusCode.INVALID_PACKET_LENGTH, error.status)
    }

    @Test
    fun canonicalizesFloatNaNOnWrite() {
        val packet = ByteArray(4)

        KompactRuntime.writeFloatBits(packet, bitOffset = 0, value = Float.fromBits(0x7FA00001))

        assertEquals(0x7FC00000uL, KompactRuntime.readBits(packet, 0, 32))
    }
}
