package ch.trancee.kompact.runtime

// ====================================================================
// Ticket 08 — packed-Long encoding constants & shared helpers
//
// ≤32-bit result types (Byte/Short/Int/Float/Boolean) use a single
// packed-Long layout:
//   [ ok(bit63) | errorKind(bits 62..60) | rawEnumCode(bits 59..48) | value(bits 47..0) ]
//
// LongResult (64-bit) uses a sentinel range near Long.MIN_VALUE:
//   bits 63 set + bits 62..58 clear → failure; error kind in bits 2..0.
//   Values in that range are not representable as success (documented).
//
// DoubleResult (64-bit) uses reserved quiet-NaN payloads:
//   canonical NaN (payload 0) = success; quiet NaN with non-zero payload
//   in bits 3..0 = failure (error kind encoded as payload 1..4).
//
// On both JVM (@JvmInline) and Kotlin/Native (value class over Long)
// the result instance is zero-alloc on both success and failure —
// the packed Long is stored inline (Ticket 03).
// ====================================================================

// --- ≤32-bit result encoding ---

internal val RESULT_OK_FLAG: Long = Long.MIN_VALUE
internal const val RESULT_ERROR_KIND_SHIFT: Int = 60
internal const val RESULT_RAW_ENUM_SHIFT: Int = 48
internal val RESULT_VALUE_MASK: Long = 0x0000_FFFF_FFFF_FFFFL

// --- LongResult sentinel encoding ---

internal val LONG_FAIL_MASK: Long = Long.MIN_VALUE or 0x7C00_0000_0000_0000L
internal val LONG_FAIL_BASE: Long = Long.MIN_VALUE

// --- FloatResult NaN encoding ---

// Canonical IEEE-754 single-precision quiet NaN (payload 0).
internal const val FLOAT_NAN_CANONICAL_BITS: Int = 0x7FC00000

// --- DoubleResult NaN encoding ---

internal val DOUBLE_NAN_CANONICAL: Long = 0x7FF8_0000_0000_0000L
internal val DOUBLE_ERROR_PAYLOAD_MASK: Long = 0x0000_0000_0000_000FL

// --- Error kind codes (shared across all encodings) ---

internal const val ERROR_BOUNDS: Int = 0
internal const val ERROR_BAD_LENGTH: Int = 1
internal const val ERROR_TRUNCATED: Int = 2
internal const val ERROR_UNKNOWN_ENUM: Int = 3

// === Shared helpers (commonMain, visible from platform actuals) ===

internal fun encodeErrorKind(error: KompactDecodeError): Int = when (error) {
    is KompactDecodeError.BoundsError -> ERROR_BOUNDS
    is KompactDecodeError.BadLengthPrefix -> ERROR_BAD_LENGTH
    is KompactDecodeError.TruncatedNested -> ERROR_TRUNCATED
    is KompactDecodeError.UnknownEnumCode -> ERROR_UNKNOWN_ENUM
}

internal fun decodeErrorFromSmallBits(packed: Long): KompactDecodeError {
    val kind = ((packed ushr RESULT_ERROR_KIND_SHIFT) and 0x7L).toInt()
    val rawCode = ((packed ushr RESULT_RAW_ENUM_SHIFT) and 0xFFFL).toInt()
    return when (kind) {
        ERROR_BOUNDS -> KompactDecodeError.BoundsError
        ERROR_BAD_LENGTH -> KompactDecodeError.BadLengthPrefix
        ERROR_TRUNCATED -> KompactDecodeError.TruncatedNested
        ERROR_UNKNOWN_ENUM -> KompactDecodeError.UnknownEnumCode(rawCode)
        else -> KompactDecodeError.BoundsError
    }
}

internal fun encodeSmallSuccess(value: Long): Long =
    RESULT_OK_FLAG or (value and RESULT_VALUE_MASK)

internal fun encodeSmallFailure(error: KompactDecodeError): Long {
    val kind = encodeErrorKind(error).toLong()
    val rawCode = if (error is KompactDecodeError.UnknownEnumCode) error.rawCode.toLong() else 0L
    return (kind shl RESULT_ERROR_KIND_SHIFT) or (rawCode shl RESULT_RAW_ENUM_SHIFT)
}

internal fun isLongFailure(packed: Long): Boolean =
    (packed and LONG_FAIL_MASK) == LONG_FAIL_BASE

internal fun encodeLongFailure(error: KompactDecodeError): Long {
    val kind = encodeErrorKind(error).toLong()
    val rawCode = if (error is KompactDecodeError.UnknownEnumCode) error.rawCode.toLong() else 0L
    return LONG_FAIL_BASE or kind or (rawCode shl 3)
}

internal fun decodeLongError(packed: Long): KompactDecodeError {
    val kind = (packed and 0x7L).toInt()
    val rawCode = ((packed ushr 3) and 0xFFL).toInt()
    return when (kind) {
        ERROR_BOUNDS -> KompactDecodeError.BoundsError
        ERROR_BAD_LENGTH -> KompactDecodeError.BadLengthPrefix
        ERROR_TRUNCATED -> KompactDecodeError.TruncatedNested
        ERROR_UNKNOWN_ENUM -> KompactDecodeError.UnknownEnumCode(rawCode)
        else -> KompactDecodeError.BoundsError
    }
}

internal fun isDoubleFailure(packed: Long): Boolean {
    val payload = packed and DOUBLE_ERROR_PAYLOAD_MASK
    return payload != 0L && (packed and 0x7FF8_0000_0000_0000L) == DOUBLE_NAN_CANONICAL
}

internal fun encodeDoubleFailure(error: KompactDecodeError): Long {
    val kind = encodeErrorKind(error).toLong()
    val rawCode = if (error is KompactDecodeError.UnknownEnumCode) error.rawCode.toLong() else 0L
    // payload = kind + 1 (1..4); 0 is reserved for canonical success NaN
    return DOUBLE_NAN_CANONICAL or (kind + 1L) or (rawCode shl 4)
}

internal fun decodeDoubleError(packed: Long): KompactDecodeError {
    val payload = (packed and DOUBLE_ERROR_PAYLOAD_MASK).toInt()
    val kind = payload - 1
    val rawCode = ((packed ushr 4) and 0xFFL).toInt()
    return when (kind) {
        ERROR_BOUNDS -> KompactDecodeError.BoundsError
        ERROR_BAD_LENGTH -> KompactDecodeError.BadLengthPrefix
        ERROR_TRUNCATED -> KompactDecodeError.TruncatedNested
        ERROR_UNKNOWN_ENUM -> KompactDecodeError.UnknownEnumCode(rawCode)
        else -> KompactDecodeError.BoundsError
    }
}

internal fun encodeDoubleSuccess(value: Double): Long =
    if (value.isNaN()) DOUBLE_NAN_CANONICAL else value.toBits()

internal fun encodeFloatSuccess(value: Float): Long =
    if (value.isNaN()) FLOAT_NAN_CANONICAL_BITS.toLong() else value.toBits().toLong()

// ====================================================================
// Ticket 08 — result value class declarations (expect)
//
// 7 specialized result types — one per scalar kind. No generic T.
// Each wraps a single Long, zero-alloc on both JVM (@JvmInline) and
// Kotlin/Native (value class).
// ====================================================================

public expect value class ByteResult(public val packed: Long) {
    public val isSuccess: Boolean
    public val isFailure: Boolean
    public val error: KompactDecodeError?
    public fun getOrThrow(): Byte
    public companion object {
        public fun success(value: Byte): ByteResult
        public fun failure(error: KompactDecodeError): ByteResult
    }
}

public expect value class ShortResult(public val packed: Long) {
    public val isSuccess: Boolean
    public val isFailure: Boolean
    public val error: KompactDecodeError?
    public fun getOrThrow(): Short
    public companion object {
        public fun success(value: Short): ShortResult
        public fun failure(error: KompactDecodeError): ShortResult
    }
}

public expect value class IntResult(public val packed: Long) {
    public val isSuccess: Boolean
    public val isFailure: Boolean
    public val error: KompactDecodeError?
    public fun getOrThrow(): Int
    public companion object {
        public fun success(value: Int): IntResult
        public fun failure(error: KompactDecodeError): IntResult
    }
}

public expect value class FloatResult(public val packed: Long) {
    public val isSuccess: Boolean
    public val isFailure: Boolean
    public val error: KompactDecodeError?
    public fun getOrThrow(): Float
    public companion object {
        public fun success(value: Float): FloatResult
        public fun failure(error: KompactDecodeError): FloatResult
    }
}

public expect value class BooleanResult(public val packed: Long) {
    public val isSuccess: Boolean
    public val isFailure: Boolean
    public val error: KompactDecodeError?
    public fun getOrThrow(): Boolean
    public companion object {
        public fun success(value: Boolean): BooleanResult
        public fun failure(error: KompactDecodeError): BooleanResult
    }
}

/**
 * Checked 64-bit integer result (Ticket 08).
 *
 * Because every 64-bit `Long` bit-pattern is a valid signed value, success
 * and failure cannot be distinguished without reserving a sentinel band.
 * [success] therefore treats a compact range near [Long.MIN_VALUE]
 * (bit 63 set with bits 62..58 clear, i.e. `Long.MIN_VALUE` through
 * `Long.MIN_VALUE + (1L shl 58) - 1`) as the failure sentinel — these values
 * are **not representable as success**. The first representable negative
 * success value is `Long.MIN_VALUE + (1L shl 58)` (bit 58 set, outside the
 * sentinel mask). This is the documented tradeoff of packing a typed result
 * into a single `Long` without boxing; see Ticket 08.
 */
public expect value class LongResult(public val packed: Long) {
    public val isSuccess: Boolean
    public val isFailure: Boolean
    public val error: KompactDecodeError?
    public fun getOrThrow(): Long
    public companion object {
        public fun success(value: Long): LongResult
        public fun failure(error: KompactDecodeError): LongResult
    }
}

public expect value class DoubleResult(public val packed: Long) {
    public val isSuccess: Boolean
    public val isFailure: Boolean
    public val error: KompactDecodeError?
    public fun getOrThrow(): Double
    public companion object {
        public fun success(value: Double): DoubleResult
        public fun failure(error: KompactDecodeError): DoubleResult
    }
}
