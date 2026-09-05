package ch.trancee.kompact.runtime

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class KompactRuntimePropertyTest {

    // Ticket 10: round-trip property — for arbitrary widths/offsets over a random
    // background, encode(value) then decode(value) is identity.

    @Test
    fun writeBits_thenReadBits_roundTrip_random() {
        val rng = Random(0x5EED)
        repeat(1000) {
            val buf = ByteArray(8) { (rng.nextInt() and 0xFF).toByte() }
            val bitOffset = rng.nextInt(0, 64)
            val bitWidth = rng.nextInt(1, 32) // 1..31 (Int-backed cap; overflow would occur at 32+)
            if (bitOffset + bitWidth > 64) return@repeat
            val value = rng.nextLong(0, 1L shl bitWidth).toInt()

            KompactRuntime.writeBits(buf, bitOffset, bitWidth, value)
            val result = KompactRuntime.readBits(buf, bitOffset, bitWidth)

            assertEquals(value, result, "offset=$bitOffset width=$bitWidth value=$value")
        }
    }

    // Ticket 07/08: writeBits must touch ONLY the target bit range.

    @Test
    fun writeBits_onlyTouchesTargetRange_random() {
        val rng = Random(0xBEEF)
        repeat(1000) {
            val original = ByteArray(8) { (rng.nextInt() and 0xFF).toByte() }
            val buf = original.copyOf()
            val bitOffset = rng.nextInt(0, 64)
            val bitWidth = rng.nextInt(1, 9)
            if (bitOffset + bitWidth > 64) return@repeat
            val value = rng.nextLong(0, 1L shl bitWidth).toInt()

            KompactRuntime.writeBits(buf, bitOffset, bitWidth, value)

            for (b in 0 until 64) {
                if (b < bitOffset || b >= bitOffset + bitWidth) {
                    assertEquals(
                        KompactRuntime.readBitsBoolean(original, b),
                        KompactRuntime.readBitsBoolean(buf, b),
                        "bit $b changed outside [$bitOffset, ${bitOffset + bitWidth})"
                    )
                }
            }
        }
    }

    // Ticket 04: readBits returns the unsigned magnitude; a signed 4-bit read is
    // the magnitude sign-extended (two's complement on the assembled value).

    @Test
    fun readBits_returnsUnsignedMagnitude() {
        val buf = byteArrayOf(0xFF.toByte())
        val magnitude = KompactRuntime.readBits(buf, 0, 4)
        assertEquals(15, magnitude)
        val signed = if (magnitude >= (1 shl 3)) magnitude - (1 shl 4) else magnitude
        assertEquals(-1, signed)
    }

    // --- Ticket 04: readBitsLong/writeBitsLong round-trip & byte-identity ---

    private fun maskForWidth(bitWidth: Int): Long =
        if (bitWidth >= 64) -1L else (1L shl bitWidth) - 1L

    @Test
    fun writeBitsLong_thenReadLong_roundTrip_random() {
        val rng = Random(0x5EEDA)
        repeat(1000) {
            val buf = ByteArray(8) { (rng.nextInt() and 0xFF).toByte() }
            val bitOffset = rng.nextInt(0, 64)
            val bitWidth = rng.nextInt(1, 65) // 1..64
            if (bitOffset + bitWidth > 64) return@repeat
            val value = rng.nextLong() and maskForWidth(bitWidth)

            KompactRuntime.writeBitsLong(buf, bitOffset, bitWidth, value)
            val result = KompactRuntime.readBitsLong(buf, bitOffset, bitWidth)

            assertEquals(value, result, "offset=$bitOffset width=$bitWidth value=$value")
        }
    }

    @Test
    fun writeBitsLong_onlyTouchesTargetRange_random() {
        val rng = Random(0xBEBA)
        repeat(1000) {
            val original = ByteArray(8) { (rng.nextInt() and 0xFF).toByte() }
            val buf = original.copyOf()
            val bitOffset = rng.nextInt(0, 64)
            val bitWidth = rng.nextInt(1, 65)
            if (bitOffset + bitWidth > 64) return@repeat
            val value = rng.nextLong() and maskForWidth(bitWidth)

            KompactRuntime.writeBitsLong(buf, bitOffset, bitWidth, value)

            for (b in 0 until 64) {
                if (b < bitOffset || b >= bitOffset + bitWidth) {
                    assertEquals(
                        KompactRuntime.readBitsBoolean(original, b),
                        KompactRuntime.readBitsBoolean(buf, b),
                        "bit $b changed outside [$bitOffset, ${bitOffset + bitWidth})"
                    )
                }
            }
        }
    }

    @Test
    fun readBitsLong_matchesReadBits_forSmallWidths() {
        // Byte-identity: readBitsLong and readBits must agree for widths 1..31.
        val rng = Random(0xCAFE)
        repeat(1000) {
            val buf = ByteArray(8) { (rng.nextInt() and 0xFF).toByte() }
            val bitOffset = rng.nextInt(0, 32)
            val bitWidth = rng.nextInt(1, 32) // 1..31
            if (bitOffset + bitWidth > 64) return@repeat

            val intResult = KompactRuntime.readBits(buf, bitOffset, bitWidth)
            val longResult = KompactRuntime.readBitsLong(buf, bitOffset, bitWidth)

            assertEquals(intResult.toLong(), longResult, "offset=$bitOffset width=$bitWidth")
        }
    }
}
