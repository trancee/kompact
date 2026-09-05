package ch.trancee.kompact.runtime

/**
 * Bit-stream primitives for Kompact.
 *
 * Bit order is **LSB-first** (little-endian bit packing): byte 0 holds the
 * field's bits 0-7, byte 1 holds bits 8-15, and bit 0 of each byte is the
 * least-significant bit of the field value (Ticket 01). Every `Byte` is masked
 * with `and 0xFF` before `ushr`/`shl`/`or`, so assembly is identical on the
 * JVM and Kotlin/Native regardless of platform endianness (PROMPT §1).
 *
 * These primitives are small, side-effect-free, and reference-free: an
 * inlinable value-class getter delegates to them with no heap allocation
 * (the backing `ByteArray` is shared, not copied) (Ticket 03). Per
 * PROMPT §3 Phase 1 + Ticket 03, every primitive and checked accessor is
 * `inline` so the zero-allocation call shape holds at the call site.
 */
public object KompactRuntime {

    /** Reads [bitWidth] bits (1..31) from [raw] starting at [bitOffset], LSB-first. */
    public inline fun readBits(raw: ByteArray, bitOffset: Int, bitWidth: Int): Int {
        var result = 0
        var srcBit = bitOffset
        var destBit = 0
        var remaining = bitWidth
        while (remaining > 0) {
            val byteIndex = srcBit ushr 3
            val bitInByte = srcBit and 7
            val bitsAvailable = 8 - bitInByte
            val chunk = minOf(remaining, bitsAvailable)
            val byteVal = raw[byteIndex].toInt() and 0xFF
            val chunkBits = (byteVal ushr bitInByte) and ((1 shl chunk) - 1)
            result = result or (chunkBits shl destBit)
            srcBit += chunk
            destBit += chunk
            remaining -= chunk
        }
        return result
    }

    /** Writes the low [bitWidth] bits (1..31) of [value] into [raw] at [bitOffset], LSB-first. */
    public inline fun writeBits(raw: ByteArray, bitOffset: Int, bitWidth: Int, value: Int) {
        var srcBit = bitOffset
        var srcValBit = 0
        var remaining = bitWidth
        while (remaining > 0) {
            val byteIndex = srcBit ushr 3
            val bitInByte = srcBit and 7
            val bitsAvailable = 8 - bitInByte
            val chunk = minOf(remaining, bitsAvailable)
            val mask = ((1 shl chunk) - 1) shl bitInByte
            val byteVal = raw[byteIndex].toInt() and 0xFF
            val chunkBits = (value ushr srcValBit) and ((1 shl chunk) - 1)
            val cleared = byteVal and mask.inv()
            val set = chunkBits shl bitInByte
            raw[byteIndex] = (cleared or set).toByte()
            srcBit += chunk
            srcValBit += chunk
            remaining -= chunk
        }
    }

    /** Reads a single bit at [bitOffset] as a [Boolean]. */
    public inline fun readBitsBoolean(raw: ByteArray, bitOffset: Int): Boolean {
        val byteIndex = bitOffset ushr 3
        val bitIndex = bitOffset and 7
        return ((raw[byteIndex].toInt() and 0xFF) ushr bitIndex and 1) == 1
    }

    /** Writes [value] as a single bit at [bitOffset]. */
    public inline fun writeBitsBoolean(raw: ByteArray, bitOffset: Int, value: Boolean) {
        val byteIndex = bitOffset ushr 3
        val bitIndex = bitOffset and 7
        if (value) {
            raw[byteIndex] = (raw[byteIndex].toInt() or (1 shl bitIndex)).toByte()
        } else {
            raw[byteIndex] = (raw[byteIndex].toInt() and (1 shl bitIndex).inv()).toByte()
        }
    }

    /** Reads [bitWidth] bits (1..64) from [raw] starting at [bitOffset], LSB-first. */
    public inline fun readBitsLong(raw: ByteArray, bitOffset: Int, bitWidth: Int): Long {
        var result = 0L
        var srcBit = bitOffset
        var destBit = 0
        var remaining = bitWidth
        while (remaining > 0) {
            val byteIndex = srcBit ushr 3
            val bitInByte = srcBit and 7
            val bitsAvailable = 8 - bitInByte
            val chunk = minOf(remaining, bitsAvailable)
            val byteVal = raw[byteIndex].toInt() and 0xFF
            val chunkBits = (byteVal ushr bitInByte) and ((1 shl chunk) - 1)
            result = result or (chunkBits.toLong() shl destBit)
            srcBit += chunk
            destBit += chunk
            remaining -= chunk
        }
        return result
    }

    /** Writes the low [bitWidth] bits (1..64) of [value] into [raw] at [bitOffset], LSB-first. */
    public inline fun writeBitsLong(raw: ByteArray, bitOffset: Int, bitWidth: Int, value: Long) {
        var srcBit = bitOffset
        var srcValBit = 0
        var remaining = bitWidth
        while (remaining > 0) {
            val byteIndex = srcBit ushr 3
            val bitInByte = srcBit and 7
            val bitsAvailable = 8 - bitInByte
            val chunk = minOf(remaining, bitsAvailable)
            val mask = ((1 shl chunk) - 1) shl bitInByte
            val byteVal = raw[byteIndex].toInt() and 0xFF
            val chunkBits = ((value ushr srcValBit) and ((1L shl chunk) - 1L)).toInt()
            val cleared = byteVal and mask.inv()
            val set = chunkBits shl bitInByte
            raw[byteIndex] = (cleared or set).toByte()
            srcBit += chunk
            srcValBit += chunk
            remaining -= chunk
        }
    }

    // --- Ticket 06/07 — bounded (checked) read accessors ---

    /** Bounds-check: true iff [bitOffset]+[bitWidth] fits in [raw]. */
    public inline fun fits(raw: ByteArray, bitOffset: Int, bitWidth: Int): Boolean =
        bitOffset >= 0 && bitWidth >= 1 && bitOffset.toLong() + bitWidth.toLong() <= raw.size.toLong() * 8L

    /** Reads 1 bit at [bitOffset] as a checked [BooleanResult]. */
    public inline fun readBool(raw: ByteArray, bitOffset: Int): BooleanResult {
        if (!fits(raw, bitOffset, 1)) {
            return BooleanResult.failure(KompactDecodeError.BoundsError)
        }
        return BooleanResult.success(readBitsBoolean(raw, bitOffset))
    }

    /** Reads up to [bitWidth] bits (1..8) as a sign-extended [ByteResult]. */
    public inline fun readInt8(raw: ByteArray, bitOffset: Int, bitWidth: Int): ByteResult {
        if (!fits(raw, bitOffset, bitWidth) || bitWidth > 8) {
            return ByteResult.failure(KompactDecodeError.BoundsError)
        }
        val magnitude = readBits(raw, bitOffset, bitWidth)
        val shift = Int.SIZE_BITS - bitWidth
        val signExtended = (magnitude shl shift) shr shift
        return ByteResult.success(signExtended.toByte())
    }

    /** Reads up to [bitWidth] bits (1..8) as an unsigned [ByteResult]. */
    public inline fun readUInt8(raw: ByteArray, bitOffset: Int, bitWidth: Int): ByteResult {
        if (!fits(raw, bitOffset, bitWidth) || bitWidth > 8) {
            return ByteResult.failure(KompactDecodeError.BoundsError)
        }
        val magnitude = readBits(raw, bitOffset, bitWidth).toByte()
        return ByteResult.success(magnitude)
    }

    /** Reads up to [bitWidth] bits (1..16) as a sign-extended [ShortResult]. */
    public inline fun readInt16(raw: ByteArray, bitOffset: Int, bitWidth: Int): ShortResult {
        if (!fits(raw, bitOffset, bitWidth) || bitWidth > 16) {
            return ShortResult.failure(KompactDecodeError.BoundsError)
        }
        val magnitude = readBits(raw, bitOffset, bitWidth)
        val shift = Int.SIZE_BITS - bitWidth
        val signExtended = (magnitude shl shift) shr shift
        return ShortResult.success(signExtended.toShort())
    }

    /** Reads up to [bitWidth] bits (1..16) as an unsigned [ShortResult]. */
    public inline fun readUInt16(raw: ByteArray, bitOffset: Int, bitWidth: Int): ShortResult {
        if (!fits(raw, bitOffset, bitWidth) || bitWidth > 16) {
            return ShortResult.failure(KompactDecodeError.BoundsError)
        }
        val magnitude = readBits(raw, bitOffset, bitWidth).toShort()
        return ShortResult.success(magnitude)
    }

    /** Reads up to [bitWidth] bits (1..32) as a sign-extended [IntResult]. */
    public inline fun readInt32(raw: ByteArray, bitOffset: Int, bitWidth: Int): IntResult {
        if (!fits(raw, bitOffset, bitWidth) || bitWidth > 32) {
            return IntResult.failure(KompactDecodeError.BoundsError)
        }
        val magnitude = if (bitWidth <= 31) {
            readBits(raw, bitOffset, bitWidth).toLong()
        } else {
            readBitsLong(raw, bitOffset, bitWidth)
        }
        val shift = Long.SIZE_BITS - bitWidth
        val signExtended = (magnitude shl shift) shr shift
        return IntResult.success(signExtended.toInt())
    }

    /** Reads up to [bitWidth] bits (1..32) as an unsigned [IntResult]. */
    public inline fun readUInt32(raw: ByteArray, bitOffset: Int, bitWidth: Int): IntResult {
        if (!fits(raw, bitOffset, bitWidth) || bitWidth > 32) {
            return IntResult.failure(KompactDecodeError.BoundsError)
        }
        val magnitude = if (bitWidth <= 31) {
            readBits(raw, bitOffset, bitWidth).toLong()
        } else {
            readBitsLong(raw, bitOffset, bitWidth)
        }
        return IntResult.success(magnitude.toInt())
    }

    /** Reads up to [bitWidth] bits (1..64) as a sign-extended [LongResult]. */
    public inline fun readInt64(raw: ByteArray, bitOffset: Int, bitWidth: Int): LongResult {
        if (!fits(raw, bitOffset, bitWidth) || bitWidth > 64) {
            return LongResult.failure(KompactDecodeError.BoundsError)
        }
        val magnitude = readBitsLong(raw, bitOffset, bitWidth)
        return if (bitWidth < 64) {
            val shift = Long.SIZE_BITS - bitWidth
            LongResult.success((magnitude shl shift) shr shift)
        } else {
            LongResult.success(magnitude)
        }
    }

    /** Reads up to [bitWidth] bits (1..64) as an unsigned [LongResult]. */
    public inline fun readUInt64(raw: ByteArray, bitOffset: Int, bitWidth: Int): LongResult {
        if (!fits(raw, bitOffset, bitWidth) || bitWidth > 64) {
            return LongResult.failure(KompactDecodeError.BoundsError)
        }
        val magnitude = readBitsLong(raw, bitOffset, bitWidth)
        return LongResult.success(magnitude)
    }

    /** Reads 32 bits at [bitOffset] as a checked [FloatResult]. NaN is canonicalized (Ticket 04). */
    public inline fun readFloat(raw: ByteArray, bitOffset: Int): FloatResult {
        if (bitOffset < 0 || bitOffset.toLong() + 32L > raw.size.toLong() * 8L) {
            return FloatResult.failure(KompactDecodeError.BoundsError)
        }
        val bits = readBitsLong(raw, bitOffset, 32).toInt()
        return FloatResult.success(Float.fromBits(bits))
    }

    /** Reads 64 bits at [bitOffset] as a checked [DoubleResult]. NaN is canonicalized (Ticket 04). */
    public inline fun readDouble(raw: ByteArray, bitOffset: Int): DoubleResult {
        if (bitOffset < 0 || bitOffset.toLong() + 64L > raw.size.toLong() * 8L) {
            return DoubleResult.failure(KompactDecodeError.BoundsError)
        }
        val bits = readBitsLong(raw, bitOffset, 64)
        return DoubleResult.success(Double.fromBits(bits))
    }
}
