package ch.trancee.kompact.result

import kotlin.jvm.JvmInline

/**
 * JVM `actual` for the length-prefixed result value classes. The
 * underlying storage is a `Long` handle into a thread-local map:
 * the value (String/ByteArray/List) is stored by reference and the
 * packed Long carries an ok-flag + error code + handle id. The
 * handle is interned per-call to avoid leaks; on read paths the
 * value is typically a slice of the caller-owned buffer so no
 * allocation occurs at all.
 */

@JvmInline
public actual value class StringResult actual constructor(public actual val packed: Long) {
    public actual val isOk: Boolean get() = (packed and (1L shl 56)) != 0L
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

@JvmInline
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

@JvmInline
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

@JvmInline
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
