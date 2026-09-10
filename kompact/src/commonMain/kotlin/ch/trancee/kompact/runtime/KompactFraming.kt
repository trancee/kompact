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
 * Reads never throw on the hot path (Ticket 06): a prefix that overruns the
 * buffer is surfaced via [nestedRegionOrNull]'s nullable return so the caller
 * can map it to a typed `BadLengthPrefix` result (Ticket 06/09: a length-prefix
 * that exceeds remaining bytes is `BadLengthPrefix`; skew is fail-fast, never
 * silent).
 */
public object KompactFraming {
    /** Valid length-prefix bit widths (Ticket 06 invariant matrix). */
    public val VALID_PREFIX_WIDTHS: Set<Int> = setOf(8, 16, 32)

    /**
     * Sentinel returned by [readLengthPrefix] when `bitWidth` is invalid or the
     * prefix field overruns `raw` (Q9: name the length-prefix failure sentinel
     * rather than scattering a bare `-1`). This is the only value [readLengthPrefix]
     * returns on failure; it is never a valid (non-negative) byte count.
     */
    public const val INVALID_LENGTH_PREFIX: Int = -1

    /**
     * Reads a fixed-width (8/16/32-bit) little-endian byte count at [bitOffset].
     * Unsigned magnitude via the raw bit primitives; returns [INVALID_LENGTH_PREFIX]
     * (-1) when [bitWidth] is invalid or the region overruns [raw] (caller maps to a
     * typed error — never throws on the read path, Ticket 06).
     */
    public fun readLengthPrefix(
        raw: ByteArray,
        bitOffset: Int,
        bitWidth: Int,
    ): Int {
        if (bitWidth !in VALID_PREFIX_WIDTHS || !KompactRuntime.fits(raw, bitOffset, bitWidth)) {
            return INVALID_LENGTH_PREFIX
        }
        return when (bitWidth) {
            8 -> KompactRuntime.readBits(raw, bitOffset, 8)
            16 -> KompactRuntime.readBits(raw, bitOffset, 16)
            else -> KompactRuntime.readBitsLong(raw, bitOffset, 32).toInt()
        }
    }

    /**
     * Writes [length] as a fixed-width little-endian byte count at [bitOffset].
     * Mirrors [readLengthPrefix] (Ticket 07: the writer selects the per-field
     * prefix width at codegen time; it must be one of [VALID_PREFIX_WIDTHS]).
     */
    public fun writeLengthPrefix(
        raw: ByteArray,
        bitOffset: Int,
        bitWidth: Int,
        length: Int,
    ) {
        if (bitWidth !in VALID_PREFIX_WIDTHS) {
            throw IllegalArgumentException("length-prefix bit width must be 8, 16, or 32 (Ticket 06)")
        }
        when (bitWidth) {
            8 -> KompactRuntime.writeBits(raw, bitOffset, 8, length)
            16 -> KompactRuntime.writeBitsLong(raw, bitOffset, 16, length.toLong())
            32 -> KompactRuntime.writeBitsLong(raw, bitOffset, 32, length.toLong())
        }
    }

    /**
     * Parse-forward nested region: reads the byte-count length prefix at
     * [bitOffset] ([prefixBitWidth] ∈ 8/16/32 — caller-validated) and returns the
     * sub-region as `(startBit, bitLength)` where the payload lives. Returns
     * `null` when the prefix overruns the buffer (a typed `BadLengthPrefix` at the
     * caller, per Ticket 06/09; never a silent misread).
     */
    internal fun nestedRegionOrNull(
        raw: ByteArray,
        bitOffset: Int,
        prefixBitWidth: Int,
    ): Pair<Int, Int>? {
        val byteCount = readLengthPrefix(raw, bitOffset, prefixBitWidth)
        if (byteCount == INVALID_LENGTH_PREFIX) return null
        // (startBit, bitLength) is an Int pair: a payload whose bit-length would
        // overflow signed Int is unrepresentable, so fail fast to null (a typed
        // BadLengthPrefix at the caller, Ticket 06/09) instead of wrapping to a
        // negative length. A 32-bit prefix can encode up to Int.MAX_VALUE
        // (0x7FFFFFFF) bytes; byteCount*8 overflows Int above Int.MAX_VALUE/8
        // = 268,435,455 bytes. Reject counts beyond that here (F-003).
        if (byteCount > Int.MAX_VALUE / 8) return null
        val regionStart = bitOffset + prefixBitWidth
        val regionBits = byteCount * 8 // safe: byteCount <= Int.MAX_VALUE/8 -> regionBits <= Int.MAX_VALUE - 7
        if (!KompactRuntime.fits(raw, regionStart, regionBits)) return null
        return regionStart to regionBits
    }

    /**
     * Typed [NestedRegionResult] variant of [nestedRegionOrNull] (Ticket 05).
     *
     * Parses the [prefixBitWidth] length-prefix at [bitOffset] and, on success,
     * returns the `(startBit, bitLength)` of the payload region. On a bad prefix
     * width, an unreadable prefix, an overflowing count (F-003), or a length-prefix
     * that exceeds the remaining buffer, returns a typed [KompactDecodeError.BadLengthPrefix]
     * failure — never null — per the Ticket 06/09 invariant (`length-prefix >
     * remaining bytes -> BadLengthPrefix`); skew is fail-fast, never silent.
     *
     * Delegates to [nestedRegionOrNull] (no guard duplication needed since this
     * function is no longer `inline`, so it can call `internal` helpers; see
     * Q9 note on sentinel-named failure paths).
     */
    public fun readNested(
        raw: ByteArray,
        bitOffset: Int,
        prefixBitWidth: Int,
    ): NestedRegionResult =
        nestedRegionOrNull(raw, bitOffset, prefixBitWidth)?.let { (start, length) ->
            NestedRegionResult.success(start, length)
        } ?: NestedRegionResult.failure(KompactDecodeError.BadLengthPrefix)

    /** Throwing variant of [readNested]: throws [KompactDecodeException] on failure (Ticket 05). */
    public fun readNestedOrThrow(
        raw: ByteArray,
        bitOffset: Int,
        prefixBitWidth: Int,
    ): NestedRegion = readNested(raw, bitOffset, prefixBitWidth).getOrThrow()

    /** Throwing variant of [readLengthPrefix]: throws [KompactDecodeException] on a bad prefix (Ticket 05). */
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
