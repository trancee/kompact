package ch.trancee.kompact.runtime

/**
 * Sequential, length-delimited framing (Ticket 05) and repeat/count handling.
 *
 * Wire shape, read forward (no random access):
 * - **Length prefix** — a fixed-width (8/16/32-bit) little-endian byte count
 *   placed at `bitOffset`; the prefixed payload follows immediately at
 *   `bitOffset + prefixBitWidth`.
 * - **Nested composite** — a length-delimited sub-region: read the prefix to
 *   learn the byte count, then consume `prefixBitWidth + count * 8` bits and
 *   hand the caller the sub-region's `[startBit, bitLength)`.
 * - **Repeated fields** — one fixed-width count prefix, then `count` elements
 *   in sequence (the count width is the field's declared prefix width).
 *
 * Malformed input never throws on the hot path: a prefix that overruns the
 * buffer is reported as a typed `BadLengthPrefix` result; skew is fail-fast,
 * never silent. An invalid caller-supplied prefix width is a programming error
 * and throws [IllegalArgumentException].
 */
public object KompactFraming {
    /** Valid length-prefix bit widths (Ticket 06 invariant matrix). */
    public val VALID_PREFIX_WIDTHS: Set<Int> = setOf(8, 16, 32)

    /**
     * Sentinel returned by [readLengthPrefix] when the prefix field overruns
     * `raw`. It is never a valid (non-negative) byte count.
     */
    public const val INVALID_LENGTH_PREFIX: Int = -1

    /**
     * Reads a fixed-width (8/16/32-bit) little-endian byte count at [bitOffset].
     * Returns [INVALID_LENGTH_PREFIX] (-1) when the prefix overruns [raw]; the
     * caller maps it to a typed error.
     *
     * @throws IllegalArgumentException if [bitWidth] is not 8, 16, or 32.
     */
    public fun readLengthPrefix(
        raw: ByteArray,
        bitOffset: Int,
        bitWidth: Int,
    ): Int {
        requirePrefixWidth(bitWidth)
        if (!KompactRuntime.fits(raw, bitOffset, bitWidth)) {
            return INVALID_LENGTH_PREFIX
        }
        return when (bitWidth) {
            8 -> KompactRuntime.readBits(raw, bitOffset, 8)
            16 -> KompactRuntime.readBits(raw, bitOffset, 16)
            else -> KompactRuntime.readBitsLong(raw, bitOffset, 32).toInt()
        }
    }

    internal fun requirePrefixWidth(width: Int) {
        require(width == 8 || width == 16 || width == 32) {
            "length-prefix bit width must be 8, 16, or 32, was $width"
        }
    }

    /**
     * Writes [length] as a fixed-width little-endian byte count at [bitOffset].
     * Mirrors [readLengthPrefix] (Ticket 07: the writer selects the per-field
     * prefix width at codegen time; it must be one of [VALID_PREFIX_WIDTHS]).
     * [length] must be non-negative and fit the selected width; invalid values
     * throw before the buffer is modified.
     *
     * @throws IllegalArgumentException for an invalid width or unrepresentable [length].
     */
    public fun writeLengthPrefix(
        raw: ByteArray,
        bitOffset: Int,
        bitWidth: Int,
        length: Int,
    ) {
        requirePrefixWidth(bitWidth)
        require(length >= 0) { "length-prefix value must be non-negative, was $length" }
        val maximum =
            when (bitWidth) {
                8 -> 0xFF
                16 -> 0xFFFF
                else -> Int.MAX_VALUE
            }
        require(length <= maximum) {
            "length $length does not fit in a $bitWidth-bit prefix (maximum $maximum)"
        }
        when (bitWidth) {
            8 -> KompactRuntime.writeBits(raw, bitOffset, 8, length)
            16 -> KompactRuntime.writeBitsLong(raw, bitOffset, 16, length.toLong())
            32 -> KompactRuntime.writeBitsLong(raw, bitOffset, 32, length.toLong())
        }
    }

    /**
     * Parses the [prefixBitWidth]-bit length prefix at [bitOffset] and returns
     * the payload region `[startBit, startBit + bitLength)` that follows it.
     *
     * Returns a [KompactDecodeError.BadLengthPrefix] failure when the prefix is
     * unreadable, the payload's bit length would overflow `Int`, or the payload
     * exceeds the remaining buffer.
     *
     * @throws IllegalArgumentException if [prefixBitWidth] is not 8, 16, or 32.
     */
    public fun readNested(
        raw: ByteArray,
        bitOffset: Int,
        prefixBitWidth: Int,
    ): NestedRegionResult {
        val byteCount = readLengthPrefix(raw, bitOffset, prefixBitWidth)
        // byteCount * 8 must fit in Int: a 32-bit prefix can claim up to
        // Int.MAX_VALUE bytes, which would otherwise wrap to a negative length.
        if (byteCount == INVALID_LENGTH_PREFIX || byteCount > Int.MAX_VALUE / 8) {
            return NestedRegionResult.failure(KompactDecodeError.BadLengthPrefix)
        }
        val regionStart = bitOffset + prefixBitWidth
        val regionBits = byteCount * 8
        if (!KompactRuntime.fits(raw, regionStart, regionBits)) {
            return NestedRegionResult.failure(KompactDecodeError.BadLengthPrefix)
        }
        return NestedRegionResult.success(regionStart, regionBits)
    }

    /**
     * Throwing variant of [readLengthPrefix].
     *
     * @throws IllegalArgumentException if [bitWidth] is not 8, 16, or 32.
     * @throws KompactDecodeException with [KompactDecodeError.BadLengthPrefix] when the prefix overruns [raw].
     */
    public fun readLengthPrefixOrThrow(
        raw: ByteArray,
        bitOffset: Int,
        bitWidth: Int,
    ): Int {
        val count = readLengthPrefix(raw, bitOffset, bitWidth)
        if (count == INVALID_LENGTH_PREFIX) throw KompactDecodeException(KompactDecodeError.BadLengthPrefix)
        return count
    }
}
