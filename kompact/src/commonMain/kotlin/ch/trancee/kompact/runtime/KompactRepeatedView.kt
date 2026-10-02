package ch.trancee.kompact.runtime

import kotlin.collections.AbstractList

/**
 * Read-only lazy list; fixed elements index directly, variable ones use sparse checkpoints.
 *
 * Keep the backing wire bytes unchanged while using this view; changing a variable element's
 * prefix invalidates the sparse index validated when the view was created.
 */
public class KompactRepeatedView<T> internal constructor(
    private val raw: ByteArray,
    private val firstBit: Int,
    private val repeatEndBit: Int,
    override val size: Int,
    private val elementWidth: Int,
    private val elementPrefixWidth: Int,
    private val checkpoints: IntArray,
    private val decodeElement: (KompactFrame) -> T,
) : AbstractList<T>() {
    /** Decode one element with a typed failure instead of throwing on malformed content. */
    public fun getResult(index: Int): KompactFrameResult<T> =
        try {
            val start = elementStart(index)
            KompactFrame.decodeBits(raw, start, elementEnd(start), decodeElement)
        } catch (failure: KompactDecodeException) {
            KompactFrameResult.Failure(failure.error)
        }

    /** Borrow a length-prefixed element payload without copying; fixed-width elements return a typed failure. */
    public fun getElementSlice(index: Int): KompactFrameResult<KompactByteSlice> =
        try {
            val start = elementStart(index)
            if (elementWidth != 0) {
                throw KompactDecodeException(KompactDecodeError.BoundsError)
            }
            KompactFrame.decodeBits(raw, start, elementEnd(start)) { frame -> frame.readBlob(elementPrefixWidth) }
        } catch (failure: KompactDecodeException) {
            KompactFrameResult.Failure(failure.error)
        }

    override fun get(index: Int): T = getResult(index).getOrThrow()

    private fun elementStart(index: Int): Int {
        if (index !in 0 until size) throw IndexOutOfBoundsException("index $index, size $size")
        if (elementWidth != 0) return firstBit + index * elementWidth
        val checkpoint = index / CHECKPOINT_INTERVAL
        var cursor = checkpoints[checkpoint]
        repeat(index % CHECKPOINT_INTERVAL) {
            cursor = elementEnd(cursor)
        }
        return cursor
    }

    private fun elementEnd(startBit: Int): Int {
        if (elementWidth != 0) return startBit + elementWidth
        val encodedLength = KompactRuntime.readBitsLong(raw, startBit, elementPrefixWidth)
        if (encodedLength > Int.MAX_VALUE) {
            throw KompactDecodeException(KompactDecodeError.BadLengthPrefix)
        }
        val length = encodedLength.toInt()
        val endBit = startBit.toLong() + elementPrefixWidth + length.toLong() * 8
        if (endBit > repeatEndBit.toLong()) {
            throw KompactDecodeException(KompactDecodeError.BadLengthPrefix)
        }
        return endBit.toInt()
    }

    internal companion object {
        const val CHECKPOINT_INTERVAL: Int = 64
    }
}
