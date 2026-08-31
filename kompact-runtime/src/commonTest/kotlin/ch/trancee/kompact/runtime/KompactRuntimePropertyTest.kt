package ch.trancee.kompact.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class KompactRuntimePropertyTest {
    @Test
    fun fixedSeedRoundTripsEveryWidthAcrossByteOffsets() {
        var state = 0x9E3779B97F4A7C15uL
        for (bitOffset in 0..15) {
            for (bitWidth in 1..64) {
                state = state * 6364136223846793005uL + 1442695040888963407uL
                val mask = if (bitWidth == 64) ULong.MAX_VALUE else (1uL shl bitWidth) - 1uL
                val value = state and mask
                val packet = ByteArray(12) { 0x5A }
                val before = packet.copyOf()

                assertEquals(null, KompactRuntime.writeBits(packet, bitOffset, bitWidth, value))
                assertEquals(value, KompactRuntime.readBits(packet, bitOffset, bitWidth))
                assertOutsideRangeUnchanged(before, packet, bitOffset, bitWidth)
            }
        }
    }

    private fun assertOutsideRangeUnchanged(
        before: ByteArray,
        after: ByteArray,
        bitOffset: Int,
        bitWidth: Int,
    ) {
        for (bit in before.indices.flatMap { byte -> (0..7).map { byte * 8 + it } }) {
            if (bit in bitOffset until bitOffset + bitWidth) continue
            val beforeBit = (before[bit / 8].toInt() ushr (bit % 8)) and 1
            val afterBit = (after[bit / 8].toInt() ushr (bit % 8)) and 1
            assertEquals(beforeBit, afterBit, "bit $bit changed outside the target range")
        }
    }
}
