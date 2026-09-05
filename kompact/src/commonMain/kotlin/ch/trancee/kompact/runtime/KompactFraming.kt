package ch.trancee.kompact.runtime

/**
 * Sequential, length-delimited framing (Ticket 05) and repeat/count handling.
 *
 * Wire shape, read forward (no random access):
 * - **Length prefix** — a fixed-width (8/16/32-bit) little-endian byte count
 *   placed at [bitOffset]; the prefixed payload follows immediately at
 *   `bitOffset + prefixBitWidth`.
 * - **Nested composite** — a length-delimited sub-region: read the prefix to
 *   learn the byte count, then consume `prefixBitWidth + count * 8` bits and
 *   hand the caller the sub-region's `[startBit, bitLength)`.
 * - **Repeated fields** — one fixed-width count prefix, then `count` elements
 *   in sequence (the count width is the field's declared prefix width).
 *
 * Reads never throw on the hot path (Ticket 06): a prefix that overruns the
 * buffer is surfaced via [nestedRegionOrNull]'s nullable return so the caller
 * can map it to a typed `TruncatedNested`/`BadLengthPrefix` result (Ticket 09:
 * skew is fail-fast, never silent).
 */
public object KompactFraming {

    /** Valid length-prefix bit widths (Ticket 06 invariant matrix). */
    public val VALID_PREFIX_WIDTHS: Set<Int> = setOf(8, 16, 32)

    /**
     * Reads a fixed-width (8/16/32-bit) little-endian byte count at [bitOffset].
     * Unsigned magnitude via the raw bit primitives; returns -1 when
     * [bitWidth] is invalid or the region overruns [raw] (caller maps to a
     * typed error — never throws on the read path, Ticket 06).
     */
    public inline fun readLengthPrefix(raw: ByteArray, bitOffset: Int, bitWidth: Int): Int {
        if (bitWidth !in VALID_PREFIX_WIDTHS || !KompactRuntime.fits(raw, bitOffset, bitWidth)) {
            return -1
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
    public inline fun writeLengthPrefix(raw: ByteArray, bitOffset: Int, bitWidth: Int, length: Int) {
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
     * `null` when the prefix overruns the buffer (a typed `TruncatedNested` /
     * `BadLengthPrefix` at the caller, per Ticket 06/09; never a silent misread).
     */
    public inline fun nestedRegionOrNull(
        raw: ByteArray,
        bitOffset: Int,
        prefixBitWidth: Int
    ): Pair<Int, Int>? {
        if (bitOffset < 0 || prefixBitWidth !in VALID_PREFIX_WIDTHS) return null
        if (!KompactRuntime.fits(raw, bitOffset, prefixBitWidth)) return null
        val byteCount = readLengthPrefix(raw, bitOffset, prefixBitWidth)
        if (byteCount < 0) return null
        // (startBit, bitLength) is an Int pair: a payload whose bit-length would
        // overflow signed Int is unrepresentable, so fail fast to null (a typed
        // TruncatedNested at the caller, Ticket 06/09) instead of wrapping to a
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
     * Count-prefixed repeat read (Ticket 05). Returns the element count decoded
     * from the fixed-width LE prefix at [bitOffset], or -1 when invalid/out-of-bounds.
     * The caller then reads [count] sequential elements starting at
     * `bitOffset + prefixBitWidth`.
     */
    public inline fun readCountPrefix(raw: ByteArray, bitOffset: Int, bitWidth: Int): Int =
        readLengthPrefix(raw, bitOffset, bitWidth)
}
