package ch.trancee.kompact.runtime

/**
 * Spec: .scratch/kompact-spec/issues/01-wire-format-bit-order.md
 *
 * LSB-first bit packing. Multi-bit integers assemble least-significant-bit
 * first: byte 0 holds the field's low bits (bits 0..7), byte 1 holds bits
 * 8..15, and bit 0 is the LSB of the value. Kotlin `Byte` is signed, so
 * every byte is masked with `and 0xFF` before `shl` / `or`; that masking
 * makes the operation identical on JVM and Kotlin/Native.
 *
 * `readBits` / `readBitsLong` / `writeBits` / `readBitsBoolean` are the raw
 * zero-allocation primitives (Ticket 03) used by the generated value-class
 * view getters. They validate only the local argument shape (width,
 * non-negative offset, fits-in-buffer); the compile-time-validated layout
 * (Ticket 06) is responsible for in-range reads.
 */
public object KompactRuntime {

    /**
     * Read an unsigned `bitWidth`-bit value at `bitOffset` in `buf`,
     * LSB-first, as an `Int`. Width must be 1..64; for 33..64, the result
     * is sign-extended by the `Int` cast; use [readBitsLong] to keep the
     * high bits.
     */
    public fun readBits(buf: ByteArray, bitOffset: Int, bitWidth: Int): Int {
        return readBitsLong(buf, bitOffset, bitWidth).toInt()
    }

    /**
     * Read a `bitWidth`-bit value at `bitOffset` in `buf`, LSB-first, as a
     * `Long`. Use for widths 33..64; for widths 1..32 prefer [readBits].
     */
    public fun readBitsLong(buf: ByteArray, bitOffset: Int, bitWidth: Int): Long {
        require(bitWidth in 1..64) { "bitWidth must be in 1..64 (was $bitWidth)" }
        require(bitOffset >= 0) { "bitOffset must be >= 0 (was $bitOffset)" }
        val end = bitOffset.toLong() + bitWidth.toLong()
        require(end <= buf.size.toLong() * 8L) {
            "read at [$bitOffset, $end) exceeds buffer (${buf.size * 8} bits)"
        }
        val startByte = bitOffset ushr 3
        val endByteExclusive = ((bitOffset + bitWidth) + 7) ushr 3
        val startBitInByte = bitOffset and 7
        var value = 0L
        var bitsAccumulated = 0
        for (i in startByte until endByteExclusive) {
            val b = buf[i].toInt() and 0xFF
            val availableInByte = if (i == startByte) 8 - startBitInByte else 8
            val takeFromByte = minOf(availableInByte, bitWidth - bitsAccumulated)
            val shiftInByte = if (i == startByte) startBitInByte else 0
            val piece = (b ushr shiftInByte) and ((1 shl takeFromByte) - 1)
            value = value or (piece.toLong() shl bitsAccumulated)
            bitsAccumulated += takeFromByte
        }
        return value
    }

    /**
     * Write the low `bitWidth` bits of `value` to `buf` at `bitOffset`,
     * LSB-first. Width must be 1..64; the write must fit within
     * `buf.size * 8` bits. Bits outside the [bitOffset, bitOffset+bitWidth)
     * range are preserved.
     */
    public fun writeBits(buf: ByteArray, bitOffset: Int, bitWidth: Int, value: Long) {
        require(bitWidth in 1..64) { "bitWidth must be in 1..64 (was $bitWidth)" }
        require(bitOffset >= 0) { "bitOffset must be >= 0 (was $bitOffset)" }
        val end = bitOffset.toLong() + bitWidth.toLong()
        require(end <= buf.size.toLong() * 8L) {
            "write at [$bitOffset, $end) exceeds buffer (${buf.size * 8} bits)"
        }
        val mask = if (bitWidth == 64) -1L else (1L shl bitWidth) - 1L
        val v = value and mask
        val startByte = bitOffset ushr 3
        val endByteExclusive = ((bitOffset + bitWidth) + 7) ushr 3
        val startBitInByte = bitOffset and 7
        var bitsWritten = 0
        for (i in startByte until endByteExclusive) {
            val b = buf[i].toInt() and 0xFF
            val availableInByte = if (i == startByte) 8 - startBitInByte else 8
            val takeFromByte = minOf(availableInByte, bitWidth - bitsWritten)
            val byteMask = (1 shl takeFromByte) - 1
            val shiftInByte = if (i == startByte) startBitInByte else 0
            val cleared = b and (byteMask shl shiftInByte).inv()
            val piece = ((v ushr bitsWritten) and byteMask.toLong()).toInt() shl shiftInByte
            buf[i] = (cleared or piece).toByte()
            bitsWritten += takeFromByte
        }
    }

    /**
     * Read a single boolean at `bitOffset`. LSB-first; uses [readBits] with
     * width=1 (the [readBits] path is the hot primitive; this method exists
     * for the `readBitsBoolean(raw, 14)` shape in `PROMPT.md` §2).
     */
    public fun readBitsBoolean(buf: ByteArray, bitOffset: Int): Boolean {
        return readBits(buf, bitOffset, 1) != 0
    }
}
