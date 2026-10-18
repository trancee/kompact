package ch.trancee.kompact.runtime

/**
 * Runtime decode error taxonomy for checked scalar reads and framed decoding.
 *
 * Checked scalar accessors return a typed `*Result` value class that encodes
 * the error kind. Framed readers expose throwing `read*` methods; the block
 * overload of `KompactFrame.decode` translates [KompactDecodeException] into a
 * `KompactFrameResult`. Every case is a `data object`, so `toString()` prints
 * the case name.
 */
public sealed interface KompactDecodeError {
    /** A read would extend past the end of the input or region. */
    public data object BoundsError : KompactDecodeError

    /** A length or count prefix is unreadable or exceeds the remaining input. */
    public data object BadLengthPrefix : KompactDecodeError

    /** A nested region ends before its declared content. */
    public data object TruncatedNested : KompactDecodeError

    /** A string field contains malformed UTF-8. */
    public data object InvalidUtf8 : KompactDecodeError

    /**
     * A complete framed decode left unread bytes, or non-zero padding bits in
     * the final partial byte, after the declared fields.
     */
    public data object TrailingData : KompactDecodeError
}

/**
 * Thrown by result `getOrThrow()` and by direct framed-reader methods when
 * decoding fails. The block overload of `KompactFrame.decode` converts it to a
 * typed `KompactFrameResult` failure.
 */
public class KompactDecodeException(
    public val error: KompactDecodeError,
) : RuntimeException("Kompact decode failed: $error")
