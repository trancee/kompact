package ch.trancee.kompact.runtime

/** Borrowed, byte-aligned region; mutation of [raw] is visible through this view. */
public class KompactByteSlice internal constructor(
    public val raw: ByteArray,
    public val start: Int,
    public val end: Int,
) {
    public val size: Int get() = end - start

    /** Explicitly copy the bounded region into an independent array. */
    public fun toByteArray(): ByteArray = raw.copyOfRange(start, end)
}
