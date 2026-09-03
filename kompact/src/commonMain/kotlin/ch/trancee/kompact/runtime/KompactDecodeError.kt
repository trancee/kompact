package ch.trancee.kompact.runtime

/**
 * Runtime decode error taxonomy (Ticket 06).
 *
 * Returned (never thrown) on the read path: a checked accessor yields a typed
 * `Kompact*Result` value class whose [packed][Long] encodes the error kind.
 * Accessing `.error` reconstructs the concrete case lazily — singletons on
 * the common path, `UnknownEnumCode` allocates only the data-class payload.
 */
public sealed class KompactDecodeError {

    public object BoundsError : KompactDecodeError()

    public object BadLengthPrefix : KompactDecodeError()

    public object TruncatedNested : KompactDecodeError()

    public data class UnknownEnumCode(public val rawCode: Int) : KompactDecodeError()
}

/**
 * Thrown by `getOrThrow()` / `readOrThrow()` on the failure path. The success
 * hot-path never throws (Ticket 03 zero-alloc). Allocation of this exception
 * is acceptable because it only occurs on an explicit recovery call.
 */
public class KompactDecodeException(public val error: KompactDecodeError) :
    RuntimeException("Kompact decode failed: $error")
