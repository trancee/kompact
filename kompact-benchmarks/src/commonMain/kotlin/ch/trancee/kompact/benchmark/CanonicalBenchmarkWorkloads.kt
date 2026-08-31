package ch.trancee.kompact.benchmark

import ch.trancee.kompact.runtime.KompactRuntime

internal object CanonicalBenchmarkWorkloads {
    val small: ByteArray = byteArrayOf(0x2A, 0x00, 0xF2.toByte(), 0x5F)
    val medium: ByteArray = ByteArray(32) { index -> (index * 37 + 11).toByte() }
    val large: ByteArray = ByteArray(244) { index -> (index * 73 + 19).toByte() }

    fun readSmallSpeed(): ULong = KompactRuntime.readBits(small, 20, 10)

    fun readMediumUnaligned(): ULong = KompactRuntime.readBits(medium, 67, 17)

    fun readLargeNestedElement(): ULong = KompactRuntime.readBits(large, 1531, 13)
}

internal object HandwrittenReference {
    fun readUnsigned(packet: ByteArray, bitOffset: Int, bitWidth: Int): ULong {
        var result = 0uL
        var consumed = 0
        while (consumed < bitWidth) {
            val packetBit = bitOffset + consumed
            val available = minOf(8 - (packetBit and 7), bitWidth - consumed)
            val byte = packet[packetBit ushr 3].toInt() and 0xff
            val mask = (1 shl available) - 1
            val part = (byte ushr (packetBit and 7)) and mask
            result = result or (part.toULong() shl consumed)
            consumed += available
        }
        return result
    }
}
