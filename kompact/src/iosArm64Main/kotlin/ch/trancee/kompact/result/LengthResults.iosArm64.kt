package ch.trancee.kompact.result

/**
 * iOS `actual` for the length-prefixed result value classes. On
 * Kotlin/Native, value classes over a single `Long` are zero-cost
 * inline. The String/ByteArray/List payload is held by the caller;
 * the packed Long stores the ok-flag + error code only. The
 * caller passes the value through a side channel (the inline
 * store pattern) — simplified here: the result only carries
 * ok/error + a raw id; the reader that produces the result also
 * has the value in scope, so the public API returns the value
 * via direct property access on a holder object.
 *
 * For v1, the iOS `actual` is identical to the JVM one in shape.
 * The reference is stored in a thread-local registry for the
 * same reason as the JVM path.
 */

public actual value class StringResult actual constructor(public actual val packed: Long) {
    public actual val isOk: Boolean get() = (packed and OK_FLAG) != 0L
    public actual val isError: Boolean get() = !isOk
    public actual val errorCode: Int get() = errorOf(packed)
    public actual val value: String get() = LengthStore.stringHandle(packed)
    public actual fun toLong(): Long = packed

    public actual companion object {
        public actual fun success(value: String): StringResult =
            StringResult(LengthStore.internString(value))
        public actual fun failure(error: Int): StringResult =
            StringResult(packFail(error))
    }
}

public actual value class BlobResult actual constructor(public actual val packed: Long) {
    public actual val isOk: Boolean get() = (packed and OK_FLAG) != 0L
    public actual val isError: Boolean get() = !isOk
    public actual val errorCode: Int get() = errorOf(packed)
    public actual val value: ByteArray get() = LengthStore.byteArrayHandle(packed)
    public actual fun toLong(): Long = packed

    public actual companion object {
        public actual fun success(value: ByteArray): BlobResult =
            BlobResult(LengthStore.internByteArray(value))
        public actual fun failure(error: Int): BlobResult =
            BlobResult(packFail(error))
    }
}

public actual value class NestedResult actual constructor(public actual val packed: Long) {
    public actual val isOk: Boolean get() = (packed and OK_FLAG) != 0L
    public actual val isError: Boolean get() = !isOk
    public actual val errorCode: Int get() = errorOf(packed)
    public actual val value: ByteArray get() = LengthStore.byteArrayHandle(packed)
    public actual fun toLong(): Long = packed

    public actual companion object {
        public actual fun success(value: ByteArray): NestedResult =
            NestedResult(LengthStore.internByteArray(value))
        public actual fun failure(error: Int): NestedResult =
            NestedResult(packFail(error))
    }
}

public actual value class RepeatedResult actual constructor(public actual val packed: Long) {
    public actual val isOk: Boolean get() = (packed and OK_FLAG) != 0L
    public actual val isError: Boolean get() = !isOk
    public actual val errorCode: Int get() = errorOf(packed)
    public actual val count: Int get() = LengthStore.repeatedCount(packed)
    public actual val elements: List<ByteArray> get() = LengthStore.repeatedElements(packed)
    public actual fun toLong(): Long = packed

    public actual companion object {
        public actual fun success(count: Int, elements: List<ByteArray>): RepeatedResult =
            RepeatedResult(LengthStore.internRepeated(count, elements))
        public actual fun failure(error: Int): RepeatedResult =
            RepeatedResult(packFail(error))
    }
}
