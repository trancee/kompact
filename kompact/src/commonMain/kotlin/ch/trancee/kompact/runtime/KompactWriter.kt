package ch.trancee.kompact.runtime

/**
 * Forward-only, growable write builder for Kompact wire output (Ticket 07).
 *
 * The writer grows its output buffer and provides lambda-based nested and
 * repeated builders. Its framing uses fixed-width little-endian length
 * prefixes, length-delimited nested sub-regions (child length computed first,
 * then prefix + bytes — no back-patch), and count-prefixed repeats
 * `<count><elem₀><elem₁>…`.
 *
 * `build()` returns an exact-length snapshot without exposing the backing
 * buffer. The writer can continue accumulating data and later builds return
 * updated snapshots.
 */
public class KompactWriter {
    /**
     * Internal write cursor in bits (LSB-first packing, Ticket 01). Advances as
     * values are written; callers observe progress via [build]. Not part of the
     * public API — nested/repeat assembly is write-forward and does not read the
     * cursor (ergonomics-11: bit-cursor visibility).
     */
    internal var bitCursor: Int = 0
        private set

    private var buffer: ByteArray = ByteArray(INITIAL_CAPACITY_BYTES)

    /** Appends [bitWidth] low bits of [value] (two's-complement magnitude). */
    public fun writeBits(
        bitWidth: Int,
        value: Int,
    ) {
        require(bitWidth in 1..31) { "writeBits bitWidth must be 1..31, was $bitWidth" }
        ensureCapacityBits(bitWidth.toLong())
        KompactRuntime.writeBits(buffer, bitCursor, bitWidth, value)
        bitCursor += bitWidth
    }

    /** Appends [bitWidth] low bits of [value] (64-bit, for UInt64/Int64). */
    public fun writeBitsLong(
        bitWidth: Int,
        value: Long,
    ) {
        require(bitWidth in 1..64) { "writeBitsLong bitWidth must be 1..64, was $bitWidth" }
        ensureCapacityBits(bitWidth.toLong())
        KompactRuntime.writeBitsLong(buffer, bitCursor, bitWidth, value)
        bitCursor += bitWidth
    }

    /** Writes a single bit (true = 1, false = 0). */
    public fun writeBool(value: Boolean) {
        ensureCapacityBits(1L)
        KompactRuntime.writeBitsBoolean(buffer, bitCursor, value)
        bitCursor += 1
    }

    /**
     * Writes [value] under [type]: [ScalarType.bitWidth] low bits as a two's-complement
     * magnitude (1..64), dispatched to [writeBits] (<=31) / [writeBitsLong] (32..64).
     * Replaces the writeInt/writeUInt/writeInt64/writeEnum overloads — one accessor
     * per width-band (ergonomics-01: ScalarType consolidation). Pass a [ScalarType] carrying the band.
     */
    public fun writeScalar(
        type: ScalarType,
        value: Long,
    ) {
        val bitWidth = type.bitWidth
        require(bitWidth in 1..64) { "writeScalar bitWidth must be 1..64, was $bitWidth" }
        if (bitWidth <= 31) {
            writeBits(bitWidth, value.toInt())
        } else {
            writeBitsLong(bitWidth, value)
        }
    }

    /**
     * Writes a length-prefixed UTF-8 string: `<prefix><bytes>` (Ticket 05).
     * Throws before changing this writer if the encoded length does not fit
     * [countWidth].
     */
    public fun writeString(
        countWidth: Int,
        value: String,
    ) {
        val bytes = value.encodeToByteArray()
        checkPrefix(countWidth, bytes.size)
        ensureCapacityBits(countWidth.toLong() + bytes.size.toLong() * 8)
        KompactFraming.writeLengthPrefix(buffer, bitCursor, countWidth, bytes.size)
        bitCursor += countWidth
        appendBytes(bytes)
    }

    /**
     * Writes a length-prefixed blob: `<prefix><bytes>` (Ticket 05).
     * Throws before changing this writer if [bytes] does not fit [countWidth].
     */
    public fun writeBlob(
        countWidth: Int,
        bytes: ByteArray,
    ) {
        checkPrefix(countWidth, bytes.size)
        ensureCapacityBits(countWidth.toLong() + bytes.size.toLong() * 8)
        KompactFraming.writeLengthPrefix(buffer, bitCursor, countWidth, bytes.size)
        bitCursor += countWidth
        appendBytes(bytes)
    }

    /**
     * Writes a nested sub-region: a child `KompactWriter` drains [block], then the
     * child's byte length is emitted as a [lengthPrefixWidth]-bit LE prefix
     * immediately followed by the child bytes (forward-only, compute-first —
     * Ticket 07). The child region begins byte-aligned after the prefix. An
     * unrepresentable child length throws before changing this writer.
     */
    public fun writeNested(
        lengthPrefixWidth: Int = 16,
        block: KompactWriter.() -> Unit,
    ) {
        val child = KompactWriter()
        block(child)
        val bytes = child.build()
        checkPrefix(lengthPrefixWidth, bytes.size)
        ensureCapacityBits(lengthPrefixWidth.toLong() + bytes.size.toLong() * 8)
        KompactFraming.writeLengthPrefix(buffer, bitCursor, lengthPrefixWidth, bytes.size)
        bitCursor += lengthPrefixWidth
        appendBytes(bytes)
    }

    /** Append a bounded byte-aligned view without allocating an intermediate payload. */
    public fun writeSlice(slice: KompactByteSlice) {
        appendRegion(slice.raw, slice.start, slice.end)
    }

    /** Write a borrowed nested region with its length prefix. */
    public fun writeNested(
        lengthPrefixWidth: Int,
        slice: KompactByteSlice,
    ) {
        checkPrefix(lengthPrefixWidth, slice.size)
        ensureCapacityBits(lengthPrefixWidth.toLong() + slice.size.toLong() * 8)
        KompactFraming.writeLengthPrefix(buffer, bitCursor, lengthPrefixWidth, slice.size)
        bitCursor += lengthPrefixWidth
        writeSlice(slice)
    }

    /** Append the bounded payload of an existing model with its length prefix. */
    public fun writeNested(
        lengthPrefixWidth: Int,
        raw: ByteArray,
        start: Int,
        end: Int,
    ) {
        require(start >= 0 && end >= start && end <= raw.size) { "Invalid nested view bounds" }
        checkPrefix(lengthPrefixWidth, end - start)
        ensureCapacityBits(lengthPrefixWidth.toLong() + (end - start).toLong() * 8)
        KompactFraming.writeLengthPrefix(buffer, bitCursor, lengthPrefixWidth, end - start)
        bitCursor += lengthPrefixWidth
        appendRegion(raw, start, end)
    }

    /**
     * Writes a count-prefixed repeat: `<count><elem₀>…<elem_{count-1}>` where each
     * element is produced by one invocation of [block] against this writer
     * (Ticket 05). [countWidth] must be one of [KompactFraming.VALID_PREFIX_WIDTHS]
     * and [count] must fit its unsigned range; invalid counts throw before
     * changing this writer or invoking [block].
     */
    public fun writeRepeated(
        count: Int,
        countWidth: Int = 8,
        block: KompactWriter.() -> Unit,
    ) {
        require(countWidth in KompactFraming.VALID_PREFIX_WIDTHS) {
            "countWidth must be 8, 16, or 32 (Ticket 06), was $countWidth"
        }
        require(count >= 0) { "repeat count must be non-negative, was $count" }
        checkPrefix(countWidth, count)
        ensureCapacityBits(countWidth.toLong())
        KompactFraming.writeLengthPrefix(buffer, bitCursor, countWidth, count)
        bitCursor += countWidth
        for (i in 0 until count) {
            block()
        }
    }

    /**
     * Returns an exact-length snapshot of the accumulated bits. Calling this
     * repeatedly is allowed and does not consume the writer's contents.
     */
    public fun build(): ByteArray {
        val byteLen = (bitCursor + 7) / 8
        return if (byteLen == 0) {
            ByteArray(0)
        } else {
            buffer.copyOfRange(0, byteLen)
        }
    }

    // --- internals ---

    private fun ensureCapacityBits(neededBits: Long) {
        require(bitCursor.toLong() + neededBits <= Int.MAX_VALUE - 7L) {
            "Writer length overflow"
        }
        val neededBytes = ((bitCursor.toLong() + neededBits + 7) / 8).toInt()
        if (neededBytes > buffer.size) {
            val newSize = maxOf(neededBytes, buffer.size * 2)
            buffer = buffer.copyOf(newSize)
        }
    }

    private fun appendBytes(bytes: ByteArray) {
        appendRegion(bytes, 0, bytes.size)
    }

    private fun appendRegion(bytes: ByteArray, start: Int, end: Int) {
        require(start >= 0 && end >= start && end <= bytes.size) { "Invalid byte slice" }
        val size = end - start
        ensureCapacityBits(size.toLong() * 8)
        // Byte-aligned append fast path (the prefix left us byte-aligned for nested/blob).
        if (bitCursor % 8 == 0) {
            // copyInto lowers to System.arraycopy on the JVM (memcpy on Native),
            // avoiding a per-byte Kotlin loop on the common aligned string/blob path.
            val dst = bitCursor / 8
            bytes.copyInto(buffer, destinationOffset = dst, startIndex = start, endIndex = end)
            bitCursor += size * 8
        } else {
            var i = start
            while (i < end) {
                KompactRuntime.writeBits(buffer, bitCursor, 8, bytes[i].toInt() and 0xFF)
                bitCursor += 8
                i++
            }
        }
    }

    private fun checkPrefix(width: Int, length: Int) {
        val maximum =
            when (width) {
                8 -> 255
                16 -> 65_535
                32 -> Int.MAX_VALUE
                else -> throw IllegalArgumentException("Invalid prefix width: $width")
            }
        require(length <= maximum) {
            "Length $length does not fit in a $width-bit prefix"
        }
    }

    public companion object {
        private const val INITIAL_CAPACITY_BYTES = 16
    }
}
