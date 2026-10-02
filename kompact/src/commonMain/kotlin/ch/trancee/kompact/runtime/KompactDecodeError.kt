package ch.trancee.kompact.runtime

/**
 * Runtime decode error taxonomy for checked scalar reads and framed decoding.
 *
 * Checked scalar accessors return a typed `Kompact*Result` value class whose
 * [packed][Long] encodes the error kind. Framed readers expose throwing
 * `read*` methods; the block overload of `KompactFrame.decode` translates
 * [KompactDecodeException] into a `KompactFrameResult`.
 *
 * Accessing `.error` on a packed scalar result reconstructs the concrete case
 * lazily — singletons on the common path, `UnknownEnumCode` allocates only the
 * data-class payload.
 */
public sealed class KompactDecodeError {
    public object BoundsError : KompactDecodeError()

    public object BadLengthPrefix : KompactDecodeError()

    public object TruncatedNested : KompactDecodeError()

    public object InvalidUtf8 : KompactDecodeError()

    public data class UnknownEnumCode(
        public val rawCode: Int,
    ) : KompactDecodeError()
}

/**
 * Thrown by result `getOrThrow()` / `readOrThrow()` and by direct framed-reader
 * methods when decoding fails. The block overload of `KompactFrame.decode`
 * converts it to a typed `KompactFrameResult` failure. Allocation is limited
 * to failure paths.
 */
public class KompactDecodeException(
    public val error: KompactDecodeError,
) : RuntimeException("Kompact decode failed: $error")
