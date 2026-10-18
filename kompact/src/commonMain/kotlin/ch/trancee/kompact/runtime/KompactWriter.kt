package ch.trancee.kompact.runtime

/**
 * Forward-only, growable write builder for Kompact wire output.
 *
 * The writer grows its output buffer and provides lambda-based nested and
 * repeated builders. Its framing uses fixed-width little-endian length
 * prefixes, length-delimited nested sub-regions (written in place, then the
 * prefix is back-patched), and count-prefixed repeats `<count><elem₀><elem₁>…`.
 *
 * `build()` returns an exact-length snapshot without exposing the backing
 * buffer. The writer can continue accumulating data and later builds return
 * updated snapshots; [reset] discards the content but keeps the capacity.
 */
public class KompactWriter {
    /** Internal LSB-first write cursor in bits; callers observe progress via [build]. */
    internal var bitCursor: Int = 0
        private set

    private var buffer: ByteArray = ByteArray(INITIAL_CAPACITY_BYTES)

    /** Current backing-buffer size; exposed to pin the growth policy in tests. */
    internal val capacity: Int get() = buffer.size

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
     * magnitude (1..64).
     */
    public fun writeScalar(
        type: ScalarType,
        value: Long,
    ) {
        writeBitsLong(type.bitWidth, value)
    }

    /**
     * Writes a length-prefixed UTF-8 string: `<prefix><bytes>`.
     * Throws before changing this writer if the encoded length does not fit
     * [countWidth].
     */
    public fun writeString(
        countWidth: Int,
        value: String,
    ) {
        val bytes = value.encodeToByteArray()
        writePrefixedRegion(countWidth, bytes, 0, bytes.size)
    }

    /**
     * Writes a length-prefixed blob: `<prefix><bytes>`.
     * Throws before changing this writer if [bytes] does not fit [countWidth].
     */
    public fun writeBlob(
        countWidth: Int,
        bytes: ByteArray,
    ) {
        writePrefixedRegion(countWidth, bytes, 0, bytes.size)
    }

    /**
     * Writes a nested sub-region: [block] writes the payload directly into this
     * writer after a reserved [lengthPrefixWidth]-bit LE prefix; the payload is
     * zero-padded to whole bytes and the prefix is back-patched with its byte
     * length. No child writer or intermediate copy is allocated. If [block]
     * throws or the length does not fit the prefix, the writer is rolled back to
     * its state before the call.
     */
    public fun writeNested(
        lengthPrefixWidth: Int = 16,
        block: KompactWriter.() -> Unit,
    ) {
        KompactFraming.requirePrefixWidth(lengthPrefixWidth)
        val prefixBit = bitCursor
        ensureCapacityBits(lengthPrefixWidth.toLong())
        bitCursor += lengthPrefixWidth
        val payloadBit = bitCursor
        // Roll back on any failure, then rethrow it unchanged.
        @Suppress("TooGenericExceptionCaught")
        try {
            block()
            val payloadBits = bitCursor - payloadBit
            val length = (payloadBits + 7) / 8
            val padding = length * 8 - payloadBits
            if (padding > 0) writeBits(padding, 0)
            KompactFraming.writeLengthPrefix(buffer, prefixBit, lengthPrefixWidth, length)
        } catch (failure: Throwable) {
            bitCursor = prefixBit
            throw failure
        }
    }

    /** Append a bounded byte-aligned view without allocating an intermediate payload. */
    public fun writeSlice(slice: KompactByteSlice) {
        requireRegion(slice.raw, slice.start, slice.end)
        ensureCapacityBits(slice.size.toLong() * 8)
        appendRegion(slice.raw, slice.start, slice.end)
    }

    /** Write a borrowed nested region with its length prefix. */
    public fun writeNested(
        lengthPrefixWidth: Int,
        slice: KompactByteSlice,
    ) {
        writePrefixedRegion(lengthPrefixWidth, slice.raw, slice.start, slice.end)
    }

    /** Append the bounded payload of an existing model with its length prefix. */
    public fun writeNested(
        lengthPrefixWidth: Int,
        raw: ByteArray,
        start: Int,
        end: Int,
    ) {
        writePrefixedRegion(lengthPrefixWidth, raw, start, end)
    }

    /**
     * Writes a count-prefixed repeat: `<count><elem₀>…<elem_{count-1}>` where each
     * element is produced by one invocation of [block] against this writer
     * [countWidth] must be one of [KompactFraming.VALID_PREFIX_WIDTHS]
     * and [count] must fit its unsigned range; invalid counts throw before
     * changing this writer or invoking [block].
     */
    public fun writeRepeated(
        count: Int,
        countWidth: Int = 8,
        block: KompactWriter.() -> Unit,
    ) {
        ensureCapacityBits(countWidth.toLong())
        writePrefix(countWidth, count)
        repeat(count) { block() }
    }

    /**
     * Returns an exact-length snapshot of the accumulated bits. Calling this
     * repeatedly is allowed and does not consume the writer's contents.
     */
    public fun build(): ByteArray {
        val byteLen = (bitCursor + 7) / 8
        val snapshot = buffer.copyOfRange(0, byteLen)
        val usedBits = bitCursor and 7
        if (usedBits != 0) {
            // Bits past the cursor may be stale after reset or a rolled-back nested write.
            snapshot[byteLen - 1] = (snapshot[byteLen - 1].toInt() and ((1 shl usedBits) - 1)).toByte()
        }
        return snapshot
    }

    /** Discards all written content while keeping the grown buffer for reuse. */
    public fun reset() {
        bitCursor = 0
    }

    // --- internals ---

    /**
     * Validates [width] and [length] (via [KompactFraming.writeLengthPrefix]) before
     * advancing the cursor, so an invalid prefix leaves the content unchanged.
     * The caller has already ensured capacity for the prefix.
     */
    private fun writePrefix(
        width: Int,
        length: Int,
    ) {
        KompactFraming.writeLengthPrefix(buffer, bitCursor, width, length)
        bitCursor += width
    }

    private fun writePrefixedRegion(
        width: Int,
        bytes: ByteArray,
        start: Int,
        end: Int,
    ) {
        requireRegion(bytes, start, end)
        val length = end - start
        // Reserve prefix and payload together so an overflow throws before the prefix is written.
        ensureCapacityBits(width.toLong() + length.toLong() * 8)
        writePrefix(width, length)
        appendRegion(bytes, start, end)
    }

    private fun requireRegion(
        bytes: ByteArray,
        start: Int,
        end: Int,
    ) {
        require(start >= 0 && end >= start && end <= bytes.size) { "Invalid byte region" }
    }

    private fun ensureCapacityBits(neededBits: Long) {
        val neededBytes = (bitCursor.toLong() + neededBits + 7) / 8
        require(KompactRuntime.isBitAddressable(neededBytes)) { "Writer length overflow" }
        if (neededBytes > buffer.size) {
            buffer = buffer.copyOf(maxOf(neededBytes.toInt(), buffer.size * 2))
        }
    }

    /** Appends a region whose bounds and capacity the caller already checked. */
    private fun appendRegion(bytes: ByteArray, start: Int, end: Int) {
        val size = end - start
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

    public companion object {
        private const val INITIAL_CAPACITY_BYTES = 16
    }
}
