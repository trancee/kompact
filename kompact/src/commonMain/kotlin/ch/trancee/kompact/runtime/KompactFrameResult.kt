package ch.trancee.kompact.runtime

/** Typed result of decoding an untrusted framed region. */
public sealed class KompactFrameResult<out T> {
    public abstract val error: KompactDecodeError?

    public abstract fun getOrThrow(): T

    public class Success<T>(public val value: T) : KompactFrameResult<T>() {
        override val error: KompactDecodeError? = null

        override fun getOrThrow(): T = value
    }

    public class Failure(override val error: KompactDecodeError) : KompactFrameResult<Nothing>() {
        override fun getOrThrow(): Nothing = throw KompactDecodeException(error)
    }
}
