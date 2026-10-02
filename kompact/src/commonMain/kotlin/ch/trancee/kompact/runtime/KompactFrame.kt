package ch.trancee.kompact.runtime

/**
 * Bounded forward-only reader over a borrowed array. Reads advance the cursor; direct
 * read methods throw [KompactDecodeException] on malformed input. The block overload
 * of [decode] translates those failures into [KompactFrameResult].
 */
public class KompactFrame private constructor(
    public val raw: ByteArray,
    public val start: Int,
    public val end: Int,
    private val initialBit: Int = start * 8,
    private val finalBit: Int = end * 8,
) {
    private var cursor: Int = initialBit
    private val endBit: Int = finalBit

    /** Read 1..64 low bits in wire order, without crossing this frame's boundary. */
    public fun readBits(width: Int): Long {
        if (width !in 1..64 || width > endBit - cursor) {
            throw KompactDecodeException(KompactDecodeError.BoundsError)
        }
        val value = KompactRuntime.readBitsLong(raw, cursor, width)
        cursor += width
        return value
    }

    private fun prefix(width: Int): Int {
        if (width !in KompactFraming.VALID_PREFIX_WIDTHS || width > endBit - cursor) {
            throw KompactDecodeException(KompactDecodeError.BadLengthPrefix)
        }
        val count = readBits(width)
        if (count > Int.MAX_VALUE) {
            throw KompactDecodeException(KompactDecodeError.BadLengthPrefix)
        }
        return count.toInt()
    }

    private fun region(width: Int): KompactByteSlice {
        val length = prefix(width)
        if (cursor % 8 != 0 || length > (endBit - cursor) / 8) {
            throw KompactDecodeException(KompactDecodeError.BadLengthPrefix)
        }
        val first = cursor / 8
        cursor += length * 8
        return KompactByteSlice(raw, first, first + length)
    }

    /** Read a length-prefixed UTF-8 string; throws [KompactDecodeException] for malformed input. */
    public fun readString(prefixWidth: Int): String {
        val bytes = region(prefixWidth)
        return try {
            raw.decodeToString(bytes.start, bytes.end, throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            throw KompactDecodeException(KompactDecodeError.InvalidUtf8)
        }
    }

    /** Read a borrowed length-prefixed byte region without copying. */
    public fun readBlob(prefixWidth: Int): KompactByteSlice = region(prefixWidth)

    /** Read a bounded nested payload sharing the original backing array. */
    public fun readNested(prefixWidth: Int): KompactByteSlice = region(prefixWidth)

    /**
     * Validate a count-prefixed sequence; retain sparse boundaries for variable elements
     * and defer each element's value decoding until it is accessed.
     */
    public fun <T> readRepeated(
        countWidth: Int,
        elementWidth: Int,
        elementPrefixWidth: Int,
        decodeElement: (KompactFrame) -> T,
    ): KompactRepeatedView<T> {
        if ((elementWidth == 0) == (elementPrefixWidth == 0) ||
            (elementWidth != 0 && elementWidth !in 1..64) ||
            (elementPrefixWidth != 0 && elementPrefixWidth !in KompactFraming.VALID_PREFIX_WIDTHS)
        ) {
            throw KompactDecodeException(KompactDecodeError.BoundsError)
        }
        val count = prefix(countWidth)
        val minimum = if (elementWidth != 0) elementWidth else elementPrefixWidth
        if (count > (endBit - cursor) / minimum) {
            throw KompactDecodeException(KompactDecodeError.TruncatedNested)
        }
        val firstBit = cursor
        val checkpointCount =
            if (elementWidth == 0) {
                count / KompactRepeatedView.CHECKPOINT_INTERVAL +
                    if (count % KompactRepeatedView.CHECKPOINT_INTERVAL == 0) 0 else 1
            } else {
                0
            }
        val checkpoints = IntArray(checkpointCount)
        if (elementWidth != 0) {
            cursor += count * elementWidth
        } else {
            for (index in 0 until count) {
                if (index % KompactRepeatedView.CHECKPOINT_INTERVAL == 0) {
                    checkpoints[index / KompactRepeatedView.CHECKPOINT_INTERVAL] = cursor
                }
                val length = prefix(elementPrefixWidth)
                if (cursor % 8 != 0 || length > (endBit - cursor) / 8) {
                    throw KompactDecodeException(KompactDecodeError.TruncatedNested)
                }
                cursor += length * 8
            }
        }
        return KompactRepeatedView(
            raw,
            firstBit,
            cursor,
            count,
            elementWidth,
            elementPrefixWidth,
            checkpoints,
            decodeElement,
        )
    }

    /** Reject extra bytes/bits after the declared fields. */
    public fun requireComplete() {
        val remaining = endBit - cursor
        if (remaining > 7 || (remaining > 0 && KompactRuntime.readBitsLong(raw, cursor, remaining) != 0L)) {
            throw KompactDecodeException(KompactDecodeError.BoundsError)
        }
    }

    public companion object {
        internal fun <T> decodeBits(
            raw: ByteArray,
            startBit: Int,
            endBit: Int,
            decodeElement: (KompactFrame) -> T,
        ): KompactFrameResult<T> =
            try {
                val frame = KompactFrame(raw, startBit / 8, (endBit + 7) / 8, startBit, endBit)
                val result = decodeElement(frame)
                frame.requireComplete()
                KompactFrameResult.Success(result)
            } catch (failure: KompactDecodeException) {
                KompactFrameResult.Failure(failure.error)
            }

        /**
         * Validate a bounded region and return a reader without copying. Direct reads may
         * throw; use the block overload when malformed input should be returned as a result.
         */
        public fun decode(
            raw: ByteArray,
            start: Int = 0,
            end: Int = raw.size,
        ): KompactFrameResult<KompactFrame> =
            if (start < 0 || end < start || end > raw.size || end > Int.MAX_VALUE / 8) {
                KompactFrameResult.Failure(KompactDecodeError.BoundsError)
            } else {
                KompactFrameResult.Success(KompactFrame(raw, start, end))
            }

        /** Parse an untrusted region and translate only decoder failures into typed results. */
        public fun <T> decode(
            raw: ByteArray,
            start: Int = 0,
            end: Int = raw.size,
            block: (KompactFrame) -> T,
        ): KompactFrameResult<T> =
            try {
                val frame = decode(raw, start, end).getOrThrow()
                val value = block(frame)
                frame.requireComplete()
                KompactFrameResult.Success(value)
            } catch (failure: KompactDecodeException) {
                KompactFrameResult.Failure(failure.error)
            }
    }
}
