package ch.trancee.kompact.runtime

import ch.trancee.kompact.result.BlobResult
import ch.trancee.kompact.result.BooleanResult
import ch.trancee.kompact.result.ByteResult
import ch.trancee.kompact.result.IntResult
import ch.trancee.kompact.result.KompactError
import ch.trancee.kompact.result.LengthReadResult
import ch.trancee.kompact.result.LongResult
import ch.trancee.kompact.result.NestedResult
import ch.trancee.kompact.result.RepeatedResult
import ch.trancee.kompact.result.StringResult

/**
 * Spec: .scratch/kompact-spec/issues/06-validation-model.md
 *        .scratch/kompact-spec/issues/08-runtime-error-model.md
 *        .scratch/kompact-spec/issues/05-variable-length-framing.md
 *        .scratch/kompact-spec/issues/09-versioning-schema-evolution.md
 *
 * Checked read accessors. Each:
 *  1. bounds-checks the read against the buffer;
 *  2. on success, calls the zero-allocation [KompactRuntime.readBits] /
 *     [KompactRuntime.readBitsLong] primitive and returns a typed result;
 *  3. on failure, returns a typed failure result with the matching
 *     [KompactError] code — never throws.
 */
public object KompactRead {

    // --- Boolean ---

    public fun readBool(buf: ByteArray, bitOffset: Int): BooleanResult {
        if (!fits(buf, bitOffset, 1)) return BooleanResult.failure(KompactError.BoundsError)
        val v = KompactRuntime.readBits(buf, bitOffset, 1)
        return BooleanResult.success(v != 0)
    }

    // --- Unsigned integers 1..64 ---

    public fun readUInt1(buf: ByteArray, bitOffset: Int): IntResult =
        readUIntN(buf, bitOffset, 1)

    public fun readUInt2(buf: ByteArray, bitOffset: Int): IntResult =
        readUIntN(buf, bitOffset, 2)

    public fun readUInt3(buf: ByteArray, bitOffset: Int): IntResult =
        readUIntN(buf, bitOffset, 3)

    public fun readUInt4(buf: ByteArray, bitOffset: Int): IntResult =
        readUIntN(buf, bitOffset, 4)

    public fun readUInt5(buf: ByteArray, bitOffset: Int): IntResult =
        readUIntN(buf, bitOffset, 5)

    public fun readUInt6(buf: ByteArray, bitOffset: Int): IntResult =
        readUIntN(buf, bitOffset, 6)

    public fun readUInt7(buf: ByteArray, bitOffset: Int): IntResult =
        readUIntN(buf, bitOffset, 7)

    public fun readUInt8(buf: ByteArray, bitOffset: Int): IntResult =
        readUIntN(buf, bitOffset, 8)

    public fun readUInt16(buf: ByteArray, bitOffset: Int): IntResult =
        readUIntN(buf, bitOffset, 16)

    public fun readUInt32(buf: ByteArray, bitOffset: Int): IntResult =
        readUIntN(buf, bitOffset, 32)

    public fun readUInt64(buf: ByteArray, bitOffset: Int): LongResult =
        readULongN(buf, bitOffset, 64)

    // --- Signed integers 1..64 (two's complement) ---

    public fun readInt4(buf: ByteArray, bitOffset: Int): IntResult =
        readIntN(buf, bitOffset, 4)

    public fun readInt7(buf: ByteArray, bitOffset: Int): IntResult =
        readIntN(buf, bitOffset, 7)

    public fun readInt8(buf: ByteArray, bitOffset: Int): ByteResult {
        if (!fits(buf, bitOffset, 8)) return ByteResult.failure(KompactError.BoundsError)
        val v = KompactRuntime.readBits(buf, bitOffset, 8)
        return ByteResult.success(signExtend8(v))
    }

    public fun readInt10(buf: ByteArray, bitOffset: Int): IntResult =
        readIntN(buf, bitOffset, 10)

    public fun readInt32(buf: ByteArray, bitOffset: Int): IntResult =
        readIntN(buf, bitOffset, 32)

    public fun readInt64(buf: ByteArray, bitOffset: Int): LongResult =
        readLongN(buf, bitOffset, 64)

    // --- Read with default (Ticket 09) — for older-writer / newer-reader compat. ---

    public fun readUInt8WithDefault(buf: ByteArray, bitOffset: Int, default: Int): Int {
        if (!fits(buf, bitOffset, 8)) return default
        return KompactRuntime.readBits(buf, bitOffset, 8)
    }

    public fun readUInt16WithDefault(buf: ByteArray, bitOffset: Int, default: Int): Int {
        if (!fits(buf, bitOffset, 16)) return default
        return KompactRuntime.readBits(buf, bitOffset, 16)
    }

    public fun readBoolWithDefault(buf: ByteArray, bitOffset: Int, default: Boolean): Boolean {
        if (!fits(buf, bitOffset, 1)) return default
        return KompactRuntime.readBitsBoolean(buf, bitOffset)
    }

    // --- Length-prefixed (Ticket 05) ---

    public fun readString(
        buf: ByteArray,
        bitOffset: Int,
        lengthPrefixBits: Int,
    ): StringResult {
        val len = readLengthPrefix(buf, bitOffset, lengthPrefixBits)
        if (len.isError) return StringResult.failure(len.errorCode)
        val (length, afterPrefix) = len.value
        val byteStart = (afterPrefix + 7) ushr 3
        if (byteStart + length > buf.size) {
            return StringResult.failure(KompactError.BadLengthPrefix)
        }
        val bytes = buf.copyOfRange(byteStart, byteStart + length)
        return StringResult.success(bytes.decodeToString())
    }

    public fun readBlob(
        buf: ByteArray,
        bitOffset: Int,
        lengthPrefixBits: Int,
    ): BlobResult {
        val len = readLengthPrefix(buf, bitOffset, lengthPrefixBits)
        if (len.isError) return BlobResult.failure(len.errorCode)
        val (length, afterPrefix) = len.value
        val byteStart = (afterPrefix + 7) ushr 3
        if (byteStart + length > buf.size) {
            return BlobResult.failure(KompactError.BadLengthPrefix)
        }
        return BlobResult.success(buf.copyOfRange(byteStart, byteStart + length))
    }

    public fun readNested(
        buf: ByteArray,
        bitOffset: Int,
        lengthPrefixBits: Int,
    ): NestedResult {
        val len = readLengthPrefix(buf, bitOffset, lengthPrefixBits)
        if (len.isError) return NestedResult.failure(len.errorCode)
        val (length, afterPrefix) = len.value
        val byteStart = (afterPrefix + 7) ushr 3
        if (byteStart + length > buf.size) {
            return NestedResult.failure(KompactError.TruncatedNested)
        }
        return NestedResult.success(buf.copyOfRange(byteStart, byteStart + length))
    }

    public fun readRepeated(
        buf: ByteArray,
        bitOffset: Int,
        countPrefixBits: Int,
        elementBitWidth: Int,
    ): RepeatedResult {
        val countResult = readLengthPrefix(buf, bitOffset, countPrefixBits)
        if (countResult.isError) return RepeatedResult.failure(countResult.errorCode)
        val (count, afterPrefix) = countResult.value
        val elementBytes = (elementBitWidth + 7) ushr 3
        var cursor = afterPrefix
        val elements = ArrayList<ByteArray>(count)
        for (i in 0 until count) {
            val byteStart = (cursor + 7) ushr 3
            if (byteStart + elementBytes > buf.size) {
                return RepeatedResult.failure(KompactError.TruncatedNested)
            }
            elements.add(buf.copyOfRange(byteStart, byteStart + elementBytes))
            cursor += elementBitWidth
        }
        return RepeatedResult.success(count, elements)
    }

    // --- Skip (Ticket 09) — older reader advances past an unknown
    // trailing length-delimited field by reading the uniform-width
    // length-prefix + payload bytes and returning the new bit cursor.
    public fun readSkipLengthPrefixed(
        buf: ByteArray,
        bitOffset: Int,
        lengthPrefixBits: Int,
    ): IntResult {
        val len = readLengthPrefix(buf, bitOffset, lengthPrefixBits)
        if (len.isError) return IntResult.failure(len.errorCode)
        val (length, afterPrefix) = len.value
        val newBitOffset = afterPrefix + length * 8
        if (newBitOffset > buf.size * 8) {
            return IntResult.failure(KompactError.BadLengthPrefix)
        }
        return IntResult.success(newBitOffset)
    }

    /**
     * Write a fixed-width little-endian length prefix at `bitOffset`.
     * Public primitive: callers building custom streams (or
     * KSP-generated views) write their own length-prefixed fields
     * directly without going through the writer.
     */
    public fun writeLengthPrefix(
        buf: ByteArray,
        bitOffset: Int,
        widthBits: Int,
        length: Int,
    ): IntResult {
        if (widthBits !in setOf(8, 16, 32)) {
            return IntResult.failure(KompactError.BoundsError)
        }
        KompactRuntime.writeBits(buf, bitOffset, widthBits, length.toLong())
        return IntResult.success(bitOffset + widthBits)
    }

     // --- Internals ---

    private fun fits(buf: ByteArray, bitOffset: Int, bitWidth: Int): Boolean {
        if (bitOffset < 0 || bitWidth < 1) return false
        val end = bitOffset.toLong() + bitWidth.toLong()
        return end <= buf.size.toLong() * 8L
    }

    private fun readUIntN(buf: ByteArray, bitOffset: Int, bitWidth: Int): IntResult {
        if (!fits(buf, bitOffset, bitWidth)) return IntResult.failure(KompactError.BoundsError)
        val v = KompactRuntime.readBits(buf, bitOffset, bitWidth)
        return IntResult.success(v)
    }

    private fun readIntN(buf: ByteArray, bitOffset: Int, bitWidth: Int): IntResult {
        if (!fits(buf, bitOffset, bitWidth)) return IntResult.failure(KompactError.BoundsError)
        val v = KompactRuntime.readBits(buf, bitOffset, bitWidth)
        val signBit = 1 shl (bitWidth - 1)
        val signed = if ((v and signBit) != 0) v - (1 shl bitWidth) else v
        return IntResult.success(signed)
    }

    private fun readULongN(buf: ByteArray, bitOffset: Int, bitWidth: Int): LongResult {
        if (!fits(buf, bitOffset, bitWidth)) return LongResult.failure(KompactError.BoundsError)
        val v = KompactRuntime.readBitsLong(buf, bitOffset, bitWidth)
        return LongResult.success(v)
    }

    private fun readLongN(buf: ByteArray, bitOffset: Int, bitWidth: Int): LongResult {
        if (!fits(buf, bitOffset, bitWidth)) return LongResult.failure(KompactError.BoundsError)
        val v = KompactRuntime.readBitsLong(buf, bitOffset, bitWidth)
        val signBit = 1L shl (bitWidth - 1)
        val signed = if ((v and signBit) != 0L) v - (1L shl bitWidth) else v
        return LongResult.success(signed)
    }

    private fun signExtend8(v: Int): Byte {
        val signBit = 0x80
        val signed = if ((v and signBit) != 0) v - 0x100 else v
        return signed.toByte()
    }

    private fun readLengthPrefix(
        buf: ByteArray,
        bitOffset: Int,
        widthBits: Int,
    ): LengthReadResult {
        if (widthBits !in setOf(8, 16, 32)) {
            return LengthReadResult.failure(KompactError.BoundsError)
        }
        if (!fits(buf, bitOffset, widthBits)) {
            return LengthReadResult.failure(KompactError.BoundsError)
        }
        val length = KompactRuntime.readBits(buf, bitOffset, widthBits)
        return LengthReadResult.success(length, bitOffset + widthBits)
    }
}
