package ch.trancee.kompact.runtime

public object KompactRuntime {
    public fun readBits(packet: ByteArray, bitOffset: Int, bitWidth: Int): ULong {
        requireValidRange(packet, bitOffset, bitWidth)

        var value = 0uL
        var valueBit = 0
        while (valueBit < bitWidth) {
            val packetBit = bitOffset + valueBit
            val bitInByte = packetBit and 7
            val chunkWidth = minOf(8 - bitInByte, bitWidth - valueBit)
            val chunkMask = (1 shl chunkWidth) - 1
            val byteValue = packet[packetBit ushr 3].toInt() and 0xFF
            val chunk = (byteValue ushr bitInByte) and chunkMask
            value = value or (chunk.toULong() shl valueBit)
            valueBit += chunkWidth
        }
        return value
    }

    public fun readSignedBits(packet: ByteArray, bitOffset: Int, bitWidth: Int): Long {
        require(bitWidth in 2..Long.SIZE_BITS) { "signed bitWidth must be between 2 and 64" }
        val value = readBits(packet, bitOffset, bitWidth)
        if (bitWidth == Long.SIZE_BITS) return value.toLong()

        val signBit = 1uL shl (bitWidth - 1)
        return if ((value and signBit) == 0uL) {
            value.toLong()
        } else {
            (value or (ULong.MAX_VALUE shl bitWidth)).toLong()
        }
    }

    public fun readBitsBoolean(packet: ByteArray, bitOffset: Int): Boolean =
        readBits(packet, bitOffset, 1) != 0uL

    public fun writeSignedBits(
        packet: ByteArray,
        bitOffset: Int,
        bitWidth: Int,
        value: Long,
    ): KompactWriteError? {
        require(bitWidth in 2..Long.SIZE_BITS) { "signed bitWidth must be between 2 and 64" }
        if (bitWidth < Long.SIZE_BITS) {
            val magnitude = 1L shl (bitWidth - 1)
            if (value < -magnitude || value >= magnitude) {
                return KompactWriteError.ValueOutOfRange(bitWidth)
            }
        }
        val encoded =
            if (bitWidth == Long.SIZE_BITS) value.toULong()
            else value.toULong() and ((1uL shl bitWidth) - 1uL)
        return writeBits(packet, bitOffset, bitWidth, encoded)
    }

    public fun writeBitsBoolean(packet: ByteArray, bitOffset: Int, value: Boolean) {
        writeBits(packet, bitOffset, 1, if (value) 1uL else 0uL)
    }

    public fun writeBits(
        packet: ByteArray,
        bitOffset: Int,
        bitWidth: Int,
        value: ULong,
    ): KompactWriteError? {
        requireValidRange(packet, bitOffset, bitWidth)
        if (bitWidth < ULong.SIZE_BITS && value >= (1uL shl bitWidth)) {
            return KompactWriteError.ValueOutOfRange(bitWidth)
        }

        var valueBit = 0
        while (valueBit < bitWidth) {
            val packetBit = bitOffset + valueBit
            val byteIndex = packetBit ushr 3
            val bitInByte = packetBit and 7
            val chunkWidth = minOf(8 - bitInByte, bitWidth - valueBit)
            val chunkMask = (1 shl chunkWidth) - 1
            val packetMask = chunkMask shl bitInByte
            val chunk = ((value shr valueBit).toInt() and chunkMask) shl bitInByte
            val oldByte = packet[byteIndex].toInt() and 0xFF
            packet[byteIndex] = ((oldByte and packetMask.inv()) or chunk).toByte()
            valueBit += chunkWidth
        }
        return null
    }

    public fun readFloatBits(packet: ByteArray, bitOffset: Int): Float =
        Float.fromBits(readBits(packet, bitOffset, Float.SIZE_BITS).toInt())

    public fun readDoubleBits(packet: ByteArray, bitOffset: Int): Double =
        Double.fromBits(readBits(packet, bitOffset, Double.SIZE_BITS).toLong())

    public fun writeFloatBits(packet: ByteArray, bitOffset: Int, value: Float) {
        val bits = if (value.isNaN()) 0x7FC00000u else value.toRawBits().toUInt()
        writeBits(packet, bitOffset, Float.SIZE_BITS, bits.toULong())
    }

    public fun writeDoubleBits(packet: ByteArray, bitOffset: Int, value: Double) {
        val bits = if (value.isNaN()) 0x7FF8000000000000uL else value.toRawBits().toULong()
        writeBits(packet, bitOffset, Double.SIZE_BITS, bits)
    }

    @PublishedApi
    internal fun requireValidRange(packet: ByteArray, bitOffset: Int, bitWidth: Int) {
        require(bitOffset >= 0) { "bitOffset must be non-negative" }
        require(bitWidth in 1..64) { "bitWidth must be between 1 and 64" }
        require(bitOffset.toLong() + bitWidth <= packet.size.toLong() * Byte.SIZE_BITS) {
            "bit range exceeds packet size"
        }
    }
}
