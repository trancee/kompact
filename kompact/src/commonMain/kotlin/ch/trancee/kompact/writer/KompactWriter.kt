package ch.trancee.kompact.writer

import ch.trancee.kompact.runtime.KompactRuntime

/**
 * Spec: .scratch/kompact-spec/issues/07-write-builder-interface.md
 *
 * Hand-written common API for writing Kompact values into a ByteArray.
 * The writer owns a growable buffer; fields are appended sequentially,
 * forward-only — the writer advances a cursor, never backtracks.
 * `build(): ByteArray` snapshots the result; the reader then consumes
 * it via the Ticket 03 caller-owned-`ByteArray` read path, so
 * write → ByteArray → read is symmetric.
 *
 * Nested composites use a sub-writer: the child's fully-computed
 * length is emitted as a fixed-width little-endian length prefix
 * (Ticket 05/06) followed by the bytes (forward-only, no backpatch).
 *
 * The writer is NOT bound by the Ticket 03 zero-alloc read contract —
 * that contract protects the read hot path. The writer allocates
 * during the build (amortized growth).
 */
public class KompactWriter {

    private var buffer: ByteArray = EMPTY
    private var bitLength: Int = 0

    public fun bitLength(): Int = bitLength

    public fun byteLength(): Int = (bitLength + 7) ushr 3

    // --- Fixed-width scalar writes ---

    public fun writeBool(value: Boolean) {
        writeBits(1, if (value) 1L else 0L)
    }

    public fun writeUInt1(value: Int) = writeBits(1, value.toLong())
    public fun writeUInt2(value: Int) = writeBits(2, value.toLong())
    public fun writeUInt3(value: Int) = writeBits(3, value.toLong())
    public fun writeUInt4(value: Int) = writeBits(4, value.toLong())
    public fun writeUInt5(value: Int) = writeBits(5, value.toLong())
    public fun writeUInt6(value: Int) = writeBits(6, value.toLong())
    public fun writeUInt7(value: Int) = writeBits(7, value.toLong())
    public fun writeUInt8(value: Int) = writeBits(8, value.toLong())
    public fun writeUInt10(value: Int) = writeBits(10, value.toLong())
    public fun writeUInt16(value: Int) = writeBits(16, value.toLong())
    public fun writeUInt32(value: Int) = writeBits(32, value.toLong())
    public fun writeUInt64(value: Long) = writeBits(64, value)

    public fun writeInt8(value: Byte) = writeBits(8, value.toLong())
    public fun writeInt16(value: Short) = writeBits(16, value.toLong())
    public fun writeInt32(value: Int) = writeBits(32, value.toLong())
    public fun writeInt64(value: Long) = writeBits(64, value)

    // --- Length-delimited ---

    public fun writeString(value: String, lengthPrefixBits: Int) {
        val bytes = value.encodeToByteArray()
        writeLengthPrefix(lengthPrefixBits, bytes.size)
        writeRawBytes(bytes)
    }

    public fun writeBlob(value: ByteArray, lengthPrefixBits: Int) {
        writeLengthPrefix(lengthPrefixBits, value.size)
        writeRawBytes(value)
    }

    // --- Nested composite ---

    public fun writeNested(lengthPrefixBits: Int, block: (KompactWriter) -> Unit): ByteArray {
        val sub = KompactWriter()
        block(sub)
        val bytes = sub.build()
        writeLengthPrefix(lengthPrefixBits, bytes.size)
        writeRawBytes(bytes)
        return bytes
    }

    // --- Repeated ---

    public fun writeRepeated(
        count: Int,
        countPrefixBits: Int,
        block: (KompactWriter) -> Unit,
    ) {
        val sub = KompactWriter()
        block(sub)
        writeLengthPrefix(countPrefixBits, count)
        writeRawBytes(sub.build())
    }

    // --- Snapshot ---

    public fun build(): ByteArray {
        val byteLen = byteLength()
        if (byteLen == 0) return EMPTY
        return buffer.copyOf(byteLen)
    }

    // --- Internals ---

    private fun writeBits(width: Int, value: Long) {
        ensureBits(bitLength + width)
        KompactRuntime.writeBits(buffer, bitLength, width, value)
        bitLength += width
    }

    private fun writeLengthPrefix(widthBits: Int, length: Int) {
        require(widthBits in setOf(8, 16, 32)) { "length prefix width must be 8, 16, or 32 bits" }
        require(length >= 0) { "length must be non-negative" }
        writeBits(widthBits, length.toLong())
    }

    private fun writeRawBytes(bytes: ByteArray) {
        for (b in bytes) {
            writeBits(8, b.toLong() and 0xFF)
        }
    }

    private fun ensureBits(needed: Int) {
        val neededBytes = ((needed + 7) ushr 3)
        if (buffer.size < neededBytes) {
            val newSize = maxOf(neededBytes, buffer.size * 2 + 1)
            buffer = buffer.copyOf(newSize)
        }
    }

    private companion object {
        private val EMPTY = ByteArray(0)
    }
}
