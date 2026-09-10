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
 * These primitives are small, side-effect-free, and reference-free: a
 * value-class getter delegates to them with no heap allocation on Kotlin/Native
 * (value classes over primitive `Long` are unboxed) and no heap allocation
 * on the JVM (the JIT scalar-replaces the `@JvmInline` wrapper) (Ticket 03).
 */
public object KompactRuntime {
    /** Reads [bitWidth] bits (1..31) from [raw] starting at [bitOffset], LSB-first. */
    public fun readBits(
        raw: ByteArray,
        bitOffset: Int,
        bitWidth: Int,
    ): Int {
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
    public fun writeBits(
        raw: ByteArray,
        bitOffset: Int,
        bitWidth: Int,
        value: Int,
    ) {
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
    public fun readBitsBoolean(
        raw: ByteArray,
        bitOffset: Int,
    ): Boolean {
        val byteIndex = bitOffset ushr 3
        val bitIndex = bitOffset and 7
        return ((raw[byteIndex].toInt() and 0xFF) ushr bitIndex and 1) == 1
    }

    /** Writes [value] as a single bit at [bitOffset]. */
    public fun writeBitsBoolean(
        raw: ByteArray,
        bitOffset: Int,
        value: Boolean,
    ) {
        val byteIndex = bitOffset ushr 3
        val bitIndex = bitOffset and 7
        if (value) {
            raw[byteIndex] = (raw[byteIndex].toInt() or (1 shl bitIndex)).toByte()
        } else {
            raw[byteIndex] = (raw[byteIndex].toInt() and (1 shl bitIndex).inv()).toByte()
        }
    }

    /** Reads [bitWidth] bits (1..64) from [raw] starting at [bitOffset], LSB-first. */
    public fun readBitsLong(
        raw: ByteArray,
        bitOffset: Int,
        bitWidth: Int,
    ): Long {
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
    public fun writeBitsLong(
        raw: ByteArray,
        bitOffset: Int,
        bitWidth: Int,
        value: Long,
    ) {
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
    public fun fits(
        raw: ByteArray,
        bitOffset: Int,
        bitWidth: Int,
    ): Boolean = bitOffset >= 0 && bitWidth >= 1 && bitOffset.toLong() + bitWidth.toLong() <= raw.size.toLong() * 8L

    /** Reads 1 bit at [bitOffset] as a checked [BooleanResult]. */
    public fun readBool(
        raw: ByteArray,
        bitOffset: Int,
    ): BooleanResult {
        if (!fits(raw, bitOffset, 1)) {
            return BooleanResult.failure(KompactDecodeError.BoundsError)
        }
        return BooleanResult.success(readBitsBoolean(raw, bitOffset))
    }

    /**
     * Reads up to [bitWidth] bits of [type] as a checked [IntResult]. The width
     * (1..32) and signedness come from [type], so a single accessor replaces the
     * 8 per-width readInt8/16/32 and readUInt8/16/32 overloads (ergonomics-01: ScalarType consolidation).
     * Sign extension uses Long-arithmetic shifts, bit-identical to the legacy
     * accessors. Callers pass a [ScalarType]; see [readScalarOrThrow] for the
     * exceptions variant.
     */
    public fun readScalar(
        raw: ByteArray,
        bitOffset: Int,
        type: ScalarType,
    ): IntResult {
        val bitWidth = type.bitWidth
        val signed = type.signed
        if (!fits(raw, bitOffset, bitWidth) || bitWidth > 32) {
            return IntResult.failure(KompactDecodeError.BoundsError)
        }
        val magnitude =
            if (bitWidth <= 31) {
                readBits(raw, bitOffset, bitWidth).toLong()
            } else {
                readBitsLong(raw, bitOffset, bitWidth)
            }
        val value =
            if (signed) {
                val shift = Long.SIZE_BITS - bitWidth
                (magnitude shl shift) shr shift
            } else {
                magnitude
            }
        return IntResult.success(value.toInt())
    }

    /**
     * Reads up to [bitWidth] bits of [type] as a checked [LongResult] (1..64).
     * Width/signedness derive from [type]; sign extension (two's-complement)
     * uses Long-arithmetic shifts. Replaces readScalarLong(w, b, signed);
     * callers pass a [ScalarType] carrying the UInt64/Int64 bands
     * (ergonomics-01: ScalarType consolidation). See [readScalarAsLongOrThrow]
     * for the exceptions variant.
     */
    public fun readScalarAsLong(
        raw: ByteArray,
        bitOffset: Int,
        type: ScalarType,
    ): LongResult {
        val bitWidth = type.bitWidth
        val signed = type.signed
        if (!fits(raw, bitOffset, bitWidth) || bitWidth > 64) {
            return LongResult.failure(KompactDecodeError.BoundsError)
        }
        val magnitude = readBitsLong(raw, bitOffset, bitWidth)
        return if (signed && bitWidth < 64) {
            val shift = Long.SIZE_BITS - bitWidth
            LongResult.success((magnitude shl shift) shr shift)
        } else {
            LongResult.success(magnitude)
        }
    }

    /** Reads 32 bits at [bitOffset] as a checked [FloatResult]. NaN is canonicalized (Ticket 04). */
    public fun readFloat(
        raw: ByteArray,
        bitOffset: Int,
    ): FloatResult {
        if (bitOffset < 0 || bitOffset.toLong() + 32L > raw.size.toLong() * 8L) {
            return FloatResult.failure(KompactDecodeError.BoundsError)
        }
        val bits = readBitsLong(raw, bitOffset, 32).toInt()
        return FloatResult.success(Float.fromBits(bits))
    }

    /** Reads 64 bits at [bitOffset] as a checked [DoubleResult]. NaN is canonicalized (Ticket 04). */
    public fun readDouble(
        raw: ByteArray,
        bitOffset: Int,
    ): DoubleResult {
        if (bitOffset < 0 || bitOffset.toLong() + 64L > raw.size.toLong() * 8L) {
            return DoubleResult.failure(KompactDecodeError.BoundsError)
        }
        val bits = readBitsLong(raw, bitOffset, 64)
        return DoubleResult.success(Double.fromBits(bits))
    }

    /** Throws [KompactDecodeException] on a bounds error; otherwise reads 1 bit as a [Boolean] (Ticket 04). */
    public fun readBoolOrThrow(
        raw: ByteArray,
        bitOffset: Int,
    ): Boolean = readBool(raw, bitOffset).getOrThrow()

    /** Throws [KompactDecodeException] on a bounds error; otherwise decodes [type] bits as an [Int] (Ticket 04). */
    public fun readScalarOrThrow(
        raw: ByteArray,
        bitOffset: Int,
        type: ScalarType,
    ): Int = readScalar(raw, bitOffset, type).getOrThrow()

    /** Throws [KompactDecodeException] on a bounds error; otherwise decodes [type] bits as a [Long] (Ticket 04). */
    public fun readScalarAsLongOrThrow(
        raw: ByteArray,
        bitOffset: Int,
        type: ScalarType,
    ): Long = readScalarAsLong(raw, bitOffset, type).getOrThrow()

    /** Throws [KompactDecodeException] on a bounds error; otherwise reads 32 bits as a [Float] (Ticket 04). */
    public fun readFloatOrThrow(
        raw: ByteArray,
        bitOffset: Int,
    ): Float = readFloat(raw, bitOffset).getOrThrow()

    /** Throws [KompactDecodeException] on a bounds error; otherwise reads 64 bits as a [Double] (Ticket 04). */
    public fun readDoubleOrThrow(
        raw: ByteArray,
        bitOffset: Int,
    ): Double = readDouble(raw, bitOffset).getOrThrow()
}
