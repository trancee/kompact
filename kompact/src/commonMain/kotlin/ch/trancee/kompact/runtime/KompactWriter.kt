package ch.trancee.kompact.runtime

/**
 * Forward-only, growable write builder for Kompact wire output (Ticket 07).
 *
 * The write path is **not** bound by the zero-allocation hot-path discipline
 * (Ticket 03) — allocation/lambda overhead is acceptable here. The binary
 * shape is a straight translation of Ticket 05's framing: fixed-width LE
 * length prefixes, length-delimited nested sub-regions (child length computed
 * first, then prefix + bytes — no back-patch), and count-prefixed repeats
 * `<count><elem₀><elem₁>…`.
 *
 * `build()` returns an exact-length snapshot; the backing buffer is not
 * exposed, so the writer remains single-use forward-only (PROMPT §1).
 */
public class KompactWriter {

    /**
     * Current write cursor in bits (LSB-first packing, Ticket 01). Read-only:
     * the cursor only advances as values are written; callers observe progress
     * via [build]. Exposed so nested/repeat assembly can reason about bit
     * alignment without re-deriving it (PROMPT §1: forward-only).
     */
    public var bitCursor: Int = 0
        private set

    private var buffer: ByteArray = ByteArray(INITIAL_CAPACITY_BYTES)

    /** Appends [bitWidth] low bits of [value] (two's-complement magnitude). */
    public fun writeBits(bitWidth: Int, value: Int) {
        require(bitWidth in 1..31) { "writeBits bitWidth must be 1..31, was $bitWidth" }
        ensureCapacityBits(bitWidth)
        KompactRuntime.writeBits(buffer, bitCursor, bitWidth, value)
        bitCursor += bitWidth
    }

    /** Appends [bitWidth] low bits of [value] (64-bit, for UInt64/Int64). */
    public fun writeBitsLong(bitWidth: Int, value: Long) {
        require(bitWidth in 1..64) { "writeBitsLong bitWidth must be 1..64, was $bitWidth" }
        ensureCapacityBits(bitWidth)
        KompactRuntime.writeBitsLong(buffer, bitCursor, bitWidth, value)
        bitCursor += bitWidth
    }

    /** Writes a single bit (true = 1, false = 0). */
    public fun writeBool(value: Boolean) {
        ensureCapacityBits(1)
        KompactRuntime.writeBitsBoolean(buffer, bitCursor, value)
        bitCursor += 1
    }

    /**
     * Writes [bitWidth] low bits of [value] as a two's-complement magnitude (1..64).
     * Replaces the writeInt/writeUInt/writeInt64/writeEnum overloads — one dispatch
     * to [writeBits] (<=31) / [writeBitsLong] (32..64) (Ticket 10 deepen).
     */
    public fun writeScalar(bitWidth: Int, value: Long) {
        require(bitWidth in 1..64) { "writeScalar bitWidth must be 1..64, was $bitWidth" }
        if (bitWidth <= 31) writeBits(bitWidth, value.toInt())
        else writeBitsLong(bitWidth, value)
    }

    /** Writes a length-prefixed UTF-8 string: `<prefix><bytes>` (Ticket 05). */
    public fun writeString(countWidth: Int, value: String) {
        val bytes = value.encodeToByteArray()
        KompactFraming.writeLengthPrefix(buffer, bitCursor, countWidth, bytes.size)
        bitCursor += countWidth
        appendBytes(bytes)
    }

    /** Writes a length-prefixed blob: `<prefix><bytes>` (Ticket 05). */
    public fun writeBlob(countWidth: Int, bytes: ByteArray) {
        KompactFraming.writeLengthPrefix(buffer, bitCursor, countWidth, bytes.size)
        bitCursor += countWidth
        appendBytes(bytes)
    }
    /**
     * Writes a nested sub-region: a child `KompactWriter` drains [block], then the
     * child's byte length is emitted as a [lengthPrefixWidth]-bit LE prefix
     * immediately followed by the child bytes (forward-only, compute-first —
     * Ticket 07). The child region begins byte-aligned after the prefix.
     */
    public fun writeNested(lengthPrefixWidth: Int = 16, block: KompactWriter.() -> Unit) {
        val child = KompactWriter()
        block(child)
        val bytes = child.build()
        KompactFraming.writeLengthPrefix(buffer, bitCursor, lengthPrefixWidth, bytes.size)
        bitCursor += lengthPrefixWidth
        appendBytes(bytes)
    }

    /**
     * Writes a count-prefixed repeat: `<count><elem₀>…<elem_{count-1}>` where each
     * element is produced by one invocation of [block] against this writer
     * (Ticket 05). [countWidth] must be one of [KompactFraming.VALID_PREFIX_WIDTHS].
     */
    public fun writeRepeated(count: Int, countWidth: Int = 8, block: KompactWriter.() -> Unit) {
        require(countWidth in KompactFraming.VALID_PREFIX_WIDTHS) {
            "countWidth must be 8, 16, or 32 (Ticket 06), was $countWidth"
        }
        require(count >= 0) { "repeat count must be non-negative, was $count" }
        KompactFraming.writeLengthPrefix(buffer, bitCursor, countWidth, count)
        bitCursor += countWidth
        for (i in 0 until count) {
            block()
        }
    }

    /**
     * Returns an exact-length snapshot of the accumulated bits. Calling
     * afterwards is allowed but yields an empty buffer (single-shot by design).
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

    private fun ensureCapacityBits(neededBits: Int) {
        val neededBytes = (bitCursor + neededBits + 7) / 8
        if (neededBytes > buffer.size) {
            val newSize = maxOf(neededBytes, buffer.size * 2)
            buffer = buffer.copyOf(newSize)
        }
    }

    private fun appendBytes(bytes: ByteArray) {
        ensureCapacityBits(bytes.size * 8)
        // Byte-aligned append fast path (the prefix left us byte-aligned for nested/blob).
        if (bitCursor % 8 == 0) {
            val dst = bitCursor / 8
            var i = 0
            while (i < bytes.size) {
                buffer[dst + i] = bytes[i]
                i++
            }
            bitCursor += bytes.size * 8
        } else {
            // Fall back to the bit primitive so we handle the rare non-aligned case.
            var i = 0
            while (i < bytes.size) {
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
