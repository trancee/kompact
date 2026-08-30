package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class KompactRuntimeBoundaryTest {
    @Test
    fun readsPositiveSignedValue() {
        assertEquals(3L, KompactRuntime.readSignedBits(byteArrayOf(0x06), 1, 7))
    }

    @Test
    fun readsFullWidthSignedValue() {
        assertEquals(
            Long.MIN_VALUE,
            KompactRuntime.readSignedBits(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0x80.toByte()), 0, 64),
        )
    }

    @Test
    fun writesFullWidthSignedValue() {
        val packet = ByteArray(8)

        val error = KompactRuntime.writeSignedBits(packet, 0, 64, Long.MIN_VALUE)

        assertEquals(null, error)
        assertEquals(0x80.toByte(), packet.last())
    }

    @Test
    fun rejectsSignedValueBelowWidthRange() {
        val packet = byteArrayOf(0x55)
        val before = packet.copyOf()

        val error = KompactRuntime.writeSignedBits(packet, 0, 7, -65)

        assertEquals(KompactStatusCode.VALUE_OUT_OF_RANGE, error?.status)
        assertContentEquals(before, packet)
    }

    @Test
    fun rejectsSignedValueAboveWidthRange() {
        val error = KompactRuntime.writeSignedBits(ByteArray(1), 0, 7, 64)

        assertEquals(KompactStatusCode.VALUE_OUT_OF_RANGE, error?.status)
    }

    @Test
    fun rejectsInvalidSignedWidth() {
        assertFailsWith<IllegalArgumentException> {
            KompactRuntime.readSignedBits(ByteArray(1), 0, 1)
        }
    }

    @Test
    fun rejectsNegativeBitOffset() {
        assertFailsWith<IllegalArgumentException> { KompactRuntime.readBits(ByteArray(1), -1, 1) }
    }

    @Test
    fun rejectsZeroBitWidth() {
        assertFailsWith<IllegalArgumentException> { KompactRuntime.readBits(ByteArray(1), 0, 0) }
    }

    @Test
    fun rejectsBitWidthAboveSixtyFour() {
        assertFailsWith<IllegalArgumentException> { KompactRuntime.readBits(ByteArray(16), 0, 65) }
    }

    @Test
    fun rejectsRangePastPacketEnd() {
        assertFailsWith<IllegalArgumentException> { KompactRuntime.readBits(ByteArray(1), 7, 2) }
    }

    @Test
    fun writesAndReadsFullUnsignedWidth() {
        val packet = ByteArray(8)

        val error = KompactRuntime.writeBits(packet, 0, 64, ULong.MAX_VALUE)

        assertEquals(null, error)
        assertEquals(ULong.MAX_VALUE, KompactRuntime.readBits(packet, 0, 64))
    }

    @Test
    fun writesFalseBoolean() {
        val packet = byteArrayOf(0xFF.toByte())

        KompactRuntime.writeBitsBoolean(packet, 3, false)

        assertEquals(0xF7.toByte(), packet.single())
        assertFalse(KompactRuntime.readBitsBoolean(packet, 3))
    }

    @Test
    fun roundTripsFiniteFloat() {
        val packet = ByteArray(4)

        KompactRuntime.writeFloatBits(packet, 0, -12.5f)

        assertEquals(-12.5f, KompactRuntime.readFloatBits(packet, 0))
    }

    @Test
    fun canonicalizesDoubleNaN() {
        val packet = ByteArray(8)

        KompactRuntime.writeDoubleBits(packet, 0, Double.fromBits(0x7FF0000000000001L))

        assertEquals(0x7FF8000000000000uL, KompactRuntime.readBits(packet, 0, 64))
    }

    @Test
    fun roundTripsFiniteDouble() {
        val packet = ByteArray(8)

        KompactRuntime.writeDoubleBits(packet, 0, -42.25)

        assertEquals(-42.25, KompactRuntime.readDoubleBits(packet, 0))
    }

    @Test
    fun rejectsSignedReadWidthAboveSixtyFour() {
        assertFailsWith<IllegalArgumentException> {
            KompactRuntime.readSignedBits(ByteArray(16), 0, 65)
        }
    }

    @Test
    fun rejectsSignedWriteWidthBelowTwo() {
        assertFailsWith<IllegalArgumentException> {
            KompactRuntime.writeSignedBits(ByteArray(1), 0, 1, 0)
        }
    }

    @Test
    fun rejectsSignedWriteWidthAboveSixtyFour() {
        assertFailsWith<IllegalArgumentException> {
            KompactRuntime.writeSignedBits(ByteArray(16), 0, 65, 0)
        }
    }
}
