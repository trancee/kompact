package ch.trancee.kompact.runtime

// Ticket 08 — iOS actuals: plain value class (Kotlin/Native, no @JvmInline).
// Same encoding logic as JVM; value classes over primitive Long are
// zero-alloc on Kotlin/Native (inline value).

public actual value class ByteResult(public actual val packed: Long) {
    public actual val isSuccess: Boolean get() = (packed and RESULT_OK_FLAG) != 0L
    public actual val isFailure: Boolean get() = (packed and RESULT_OK_FLAG) == 0L
    public actual val error: KompactDecodeError? get() =
        if (isSuccess) null else decodeErrorFromSmallBits(packed)
    public actual fun getOrThrow(): Byte =
        if (isSuccess) (packed and RESULT_VALUE_MASK).toByte()
        else throwSmallFailure(packed)
    public actual companion object {
        public actual fun success(value: Byte): ByteResult =
            ByteResult(encodeSmallSuccess(value.toLong()))
        public actual fun failure(error: KompactDecodeError): ByteResult =
            ByteResult(encodeSmallFailure(error))
    }
}

public actual value class ShortResult(public actual val packed: Long) {
    public actual val isSuccess: Boolean get() = (packed and RESULT_OK_FLAG) != 0L
    public actual val isFailure: Boolean get() = (packed and RESULT_OK_FLAG) == 0L
    public actual val error: KompactDecodeError? get() =
        if (isSuccess) null else decodeErrorFromSmallBits(packed)
    public actual fun getOrThrow(): Short =
        if (isSuccess) (packed and RESULT_VALUE_MASK).toShort()
        else throwSmallFailure(packed)
    public actual companion object {
        public actual fun success(value: Short): ShortResult =
            ShortResult(encodeSmallSuccess(value.toLong()))
        public actual fun failure(error: KompactDecodeError): ShortResult =
            ShortResult(encodeSmallFailure(error))
    }
}

public actual value class IntResult(public actual val packed: Long) {
    public actual val isSuccess: Boolean get() = (packed and RESULT_OK_FLAG) != 0L
    public actual val isFailure: Boolean get() = (packed and RESULT_OK_FLAG) == 0L
    public actual val error: KompactDecodeError? get() =
        if (isSuccess) null else decodeErrorFromSmallBits(packed)
    public actual fun getOrThrow(): Int =
        if (isSuccess) (packed and RESULT_VALUE_MASK).toInt()
        else throwSmallFailure(packed)
    public actual companion object {
        public actual fun success(value: Int): IntResult =
            IntResult(encodeSmallSuccess(value.toLong()))
        public actual fun failure(error: KompactDecodeError): IntResult =
            IntResult(encodeSmallFailure(error))
    }
}

public actual value class FloatResult(public actual val packed: Long) {
    public actual val isSuccess: Boolean get() = (packed and RESULT_OK_FLAG) != 0L
    public actual val isFailure: Boolean get() = (packed and RESULT_OK_FLAG) == 0L
    public actual val error: KompactDecodeError? get() =
        if (isSuccess) null else decodeErrorFromSmallBits(packed)
    public actual fun getOrThrow(): Float =
        if (isSuccess) Float.fromBits((packed and RESULT_VALUE_MASK).toInt())
        else throwSmallFailure(packed)
    public actual companion object {
        public actual fun success(value: Float): FloatResult =
            FloatResult(encodeSmallSuccess(encodeFloatSuccess(value)))
        public actual fun failure(error: KompactDecodeError): FloatResult =
            FloatResult(encodeSmallFailure(error))
    }
}

public actual value class BooleanResult(public actual val packed: Long) {
    public actual val isSuccess: Boolean get() = (packed and RESULT_OK_FLAG) != 0L
    public actual val isFailure: Boolean get() = (packed and RESULT_OK_FLAG) == 0L
    public actual val error: KompactDecodeError? get() =
        if (isSuccess) null else decodeErrorFromSmallBits(packed)
    public actual fun getOrThrow(): Boolean =
        if (isSuccess) (packed and RESULT_VALUE_MASK) != 0L
        else throwSmallFailure(packed)
    public actual companion object {
        public actual fun success(value: Boolean): BooleanResult =
            BooleanResult(encodeSmallSuccess(if (value) 1L else 0L))
        public actual fun failure(error: KompactDecodeError): BooleanResult =
            BooleanResult(encodeSmallFailure(error))
    }
}

// LongResult — sentinel-based encoding (bits 63 set + 62..58 clear)

public actual value class LongResult(public actual val packed: Long) {
    public actual val isSuccess: Boolean get() = !isLongFailure(packed)
    public actual val isFailure: Boolean get() = isLongFailure(packed)
    public actual val error: KompactDecodeError? get() =
        if (isSuccess) null else decodeLongError(packed)
    public actual fun getOrThrow(): Long =
        if (isSuccess) packed
        else throwLongFailure(packed)
    public actual companion object {
        public actual fun success(value: Long): LongResult = LongResult(value)
        public actual fun failure(error: KompactDecodeError): LongResult =
            LongResult(encodeLongFailure(error))
    }
}

// DoubleResult — canonical-NaN success, reserved NaN payload for errors

public actual value class DoubleResult(public actual val packed: Long) {
    public actual val isSuccess: Boolean get() = !isDoubleFailure(packed)
    public actual val isFailure: Boolean get() = isDoubleFailure(packed)
    public actual val error: KompactDecodeError? get() =
        if (isSuccess) null else decodeDoubleError(packed)
    public actual fun getOrThrow(): Double =
        if (isSuccess) Double.fromBits(packed)
        else throwDoubleFailure(packed)
    public actual companion object {
        public actual fun success(value: Double): DoubleResult =
            DoubleResult(encodeDoubleSuccess(value))
        public actual fun failure(error: KompactDecodeError): DoubleResult =
            DoubleResult(encodeDoubleFailure(error))
    }
}
