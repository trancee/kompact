package ch.trancee.kompact.runtime

/**
 * Reusable borrowed view of a half-open byte range in a caller-owned buffer.
 *
 * The constructor initially selects the complete buffer. Rebinding and reads
 * do not copy bytes; use [copyTo] when independent storage is required.
 */
public class KompactByteRange(
    buffer: ByteArray,
) {
    /** The borrowed backing buffer. */
    public var buffer: ByteArray = buffer
        private set

    /** Inclusive start offset in [buffer]. */
    public var start: Int = 0
        private set

    /** Exclusive end offset in [buffer]. */
    public var end: Int = buffer.size
        private set

    /** Number of borrowed bytes in this range. */
    public val size: Int
        get() = end - start

    /** Rebind this range; invalid byte bounds leave its previous binding intact. */
    public fun reset(
        buffer: ByteArray,
        start: Int = 0,
        end: Int = buffer.size,
    ): Boolean {
        if (start < 0 || end < start || end > buffer.size) return false
        this.buffer = buffer
        this.start = start
        this.end = end
        return true
    }

    /** Copy the borrowed bytes into the caller's destination. */
    public fun copyTo(
        destination: ByteArray,
        destinationOffset: Int = 0,
    ): Boolean {
        if (destinationOffset < 0 || size > destination.size - destinationOffset) return false
        buffer.copyInto(destination, destinationOffset, start, end)
        return true
    }
}
