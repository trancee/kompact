package ch.trancee.kompact.runtime

/**
 * Bit-stream primitives for Kompact.
 *
 * Bit order is **LSB-first** (little-endian bit packing): byte 0 holds the
 * field's bits 0-7, byte 1 holds bits 8-15, and bit 0 of each byte is the
 * least-significant bit of the field value. Every `Byte` is masked
 * with `and 0xFF` before `ushr`/`shl`/`or`, so assembly is identical on the
 * JVM and Kotlin/Native regardless of platform endianness.
 *
 * Each chunk loop runs once per byte the field touches ([byteSpan]), so its
 * trip count never depends on a comparison inside the loop.
 *
 * These primitives read/write the caller-owned buffer without constructing a
 * result wrapper. Generated raw getters delegate to them directly; checked
 * accessors add a typed result. `LongResult` is a regular class so it can
 * represent the full `Long` domain without reserving sentinel values.
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
        repeat(byteSpan(bitOffset, bitWidth)) {
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
        repeat(byteSpan(bitOffset, bitWidth)) {
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
        repeat(byteSpan(bitOffset, bitWidth)) {
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
        repeat(byteSpan(bitOffset, bitWidth)) {
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

    /** Largest byte count whose bit length still fits an `Int` bit offset. */
    internal const val MAX_ADDRESSABLE_BYTES: Int = Int.MAX_VALUE / 8

    /** True iff [byteCount] is non-negative and `byteCount * 8` fits an `Int` bit offset. */
    internal fun isBitAddressable(byteCount: Long): Boolean = byteCount >= 0 && byteCount <= MAX_ADDRESSABLE_BYTES

    // --- Bounded (checked) read accessors ---

    private fun byteSpan(
        bitOffset: Int,
        bitWidth: Int,
    ): Int = ((bitOffset and 7) + bitWidth + 7) shr 3

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
     * Reads up to [ScalarType.bitWidth] bits of [type] as a checked [IntResult]. The width
     * (1..32) and signedness come from [type], so a single accessor replaces the
     * 8 per-width readInt8/16/32 and readUInt8/16/32 overloads.
     * Sign extension uses Long-arithmetic shifts, bit-identical to the legacy
     * accessors. Callers pass a [ScalarType]; see [readScalarOrThrow] for the
     * exceptions variant.
     *
     * @throws IllegalArgumentException if [type] is wider than 32 bits; use
     *   [readScalarAsLong] for wider scalars.
     */
    public fun readScalar(
        raw: ByteArray,
        bitOffset: Int,
        type: ScalarType,
    ): IntResult {
        val bitWidth = type.bitWidth
        val signed = type.signed
        require(bitWidth <= 32) { "readScalar supports widths up to 32 bits, was $bitWidth" }
        if (!fits(raw, bitOffset, bitWidth)) {
            return IntResult.failure(KompactDecodeError.BoundsError)
        }
        val magnitude = readBitsLong(raw, bitOffset, bitWidth)
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
     * Reads up to [ScalarType.bitWidth] bits of [type] as a checked [LongResult] (1..64).
     * Width/signedness derive from [type]; sign extension (two's-complement)
     * uses Long-arithmetic shifts. The regular-class [LongResult] preserves the
     * full signed Long domain without reserving sentinel values. Pass a
     * [ScalarType] carrying the UInt64/Int64 bands; see
     * [readScalarAsLongOrThrow] for the exceptions variant.
     */
    public fun readScalarAsLong(
        raw: ByteArray,
        bitOffset: Int,
        type: ScalarType,
    ): LongResult {
        val bitWidth = type.bitWidth
        val signed = type.signed
        if (!fits(raw, bitOffset, bitWidth)) {
            return LongResult.failure(KompactDecodeError.BoundsError)
        }
        val magnitude = readBitsLong(raw, bitOffset, bitWidth)
        return if (signed) {
            val shift = Long.SIZE_BITS - bitWidth
            LongResult.success((magnitude shl shift) shr shift)
        } else {
            LongResult.success(magnitude)
        }
    }

    /** Reads 32 bits at [bitOffset] as a checked [FloatResult]. NaN is canonicalized. */
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

    /** Reads 64 bits at [bitOffset] as a checked [DoubleResult]. NaN is canonicalized. */
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

    /** Throws [KompactDecodeException] on a bounds error; otherwise reads 1 bit as a [Boolean]. */
    public fun readBoolOrThrow(
        raw: ByteArray,
        bitOffset: Int,
    ): Boolean = readBool(raw, bitOffset).getOrThrow()

    /**
     * Throws [KompactDecodeException] on a bounds error; otherwise decodes [type] bits as an [Int].
     *
     * @throws IllegalArgumentException if [type] is wider than 32 bits.
     */
    public fun readScalarOrThrow(
        raw: ByteArray,
        bitOffset: Int,
        type: ScalarType,
    ): Int = readScalar(raw, bitOffset, type).getOrThrow()

    /** Throws [KompactDecodeException] on a bounds error; otherwise decodes [type] bits as a [Long]. */
    public fun readScalarAsLongOrThrow(
        raw: ByteArray,
        bitOffset: Int,
        type: ScalarType,
    ): Long = readScalarAsLong(raw, bitOffset, type).getOrThrow()

    /** Throws [KompactDecodeException] on a bounds error; otherwise reads 32 bits as a [Float]. */
    public fun readFloatOrThrow(
        raw: ByteArray,
        bitOffset: Int,
    ): Float = readFloat(raw, bitOffset).getOrThrow()

    /** Throws [KompactDecodeException] on a bounds error; otherwise reads 64 bits as a [Double]. */
    public fun readDoubleOrThrow(
        raw: ByteArray,
        bitOffset: Int,
    ): Double = readDouble(raw, bitOffset).getOrThrow()
}
