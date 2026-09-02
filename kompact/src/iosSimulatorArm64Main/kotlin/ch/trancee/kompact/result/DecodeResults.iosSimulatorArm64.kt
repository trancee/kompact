package ch.trancee.kompact.result

/**
 * Spec: .scratch/kompact-spec/issues/08-runtime-error-model.md
 *
 * iOS `actual` for the result value classes. Plain `value class` —
 * no `@JvmInline` (JVM-stdlib-only annotation).
 */

public actual value class ByteResult actual constructor(public actual val packed: Long) {
    public actual val isOk: Boolean get() = isOk(packed)
    public actual val isError: Boolean get() = !isOk(packed)
    public actual val errorCode: Int get() = errorOf(packed)
    public actual val value: Byte get() = valueOf(packed).toByte()
    public actual fun toLong(): Long = packed

    public actual companion object {
        public actual fun success(value: Byte): ByteResult = ByteResult(packOk(value.toLong()))
        public actual fun failure(error: Int): ByteResult = ByteResult(packFail(error))
    }
}

public actual value class IntResult actual constructor(public actual val packed: Long) {
    public actual val isOk: Boolean get() = isOk(packed)
    public actual val isError: Boolean get() = !isOk(packed)
    public actual val errorCode: Int get() = errorOf(packed)
    public actual val value: Int get() = valueOf(packed).toInt()
    public actual fun toLong(): Long = packed

    public actual companion object {
        public actual fun success(value: Int): IntResult = IntResult(packOk(value.toLong()))
        public actual fun failure(error: Int): IntResult = IntResult(packFail(error))
    }
}

public actual value class LongResult actual constructor(public actual val packed: Long) {
    public actual val isOk: Boolean get() = isOk(packed)
    public actual val isError: Boolean get() = !isOk(packed)
    public actual val errorCode: Int get() = errorOf(packed)
    public actual val value: Long get() = if (isOk(packed)) valueOf(packed) else 0L
    public actual fun toLong(): Long = packed

    public actual companion object {
        public actual fun success(value: Long): LongResult = LongResult(packOk(value))
        public actual fun failure(error: Int): LongResult = LongResult(packFail(error))
    }
}

public actual value class BooleanResult actual constructor(public actual val packed: Long) {
    public actual val isOk: Boolean get() = isOk(packed)
    public actual val isError: Boolean get() = !isOk(packed)
    public actual val errorCode: Int get() = errorOf(packed)
    public actual val value: Boolean get() = isOk(packed) && valueOf(packed) != 0L
    public actual fun toLong(): Long = packed

    public actual companion object {
        public actual fun success(value: Boolean): BooleanResult =
            BooleanResult(packOk(if (value) 1L else 0L))
        public actual fun failure(error: Int): BooleanResult = BooleanResult(packFail(error))
    }
}
