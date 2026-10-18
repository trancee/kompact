package ch.trancee.kompact.runtime

import kotlin.jvm.JvmInline

// ====================================================================
// Packed result encodings
//
// ≤32-bit result types (Int/Float/Boolean; Byte/Short widen to Int) use a single
// packed-Long layout:
//   [ ok(bit63) | errorKind(bits 62..60) | unused(bits 59..48) | value(bits 47..0) ]
// FloatResult stores the raw IEEE-754 bits, so NaN payloads round-trip exactly.
//
// NestedRegionResult uses a packed-Long failure sentinel; successful offsets
// and lengths occupy only non-negative 32-bit halves.
//
// DoubleResult (64-bit) has no spare bits, so it reserves quiet-NaN payloads:
//   a quiet NaN whose low 4 payload bits are 1..5 is a failure (error kind + 1);
//   every successful NaN is canonicalized to payload 0. Double NaN payloads are
//   therefore not preserved — read raw bits with `readBitsLong` if they matter.
//
// These result types use value classes over packed values; this is a type
// representation, not a measured allocation guarantee for every call shape.
// ====================================================================

// --- ≤32-bit result encoding ---

internal const val RESULT_OK_FLAG: Long = Long.MIN_VALUE
internal const val RESULT_ERROR_KIND_SHIFT: Int = 60
internal const val RESULT_VALUE_MASK: Long = 0x0000_FFFF_FFFF_FFFFL

// --- NestedRegionResult failure sentinel ---

internal const val PACKED_FAILURE_MASK: Long = Long.MIN_VALUE or 0x7C00_0000_0000_0000L
internal const val PACKED_FAILURE_BASE: Long = Long.MIN_VALUE

// --- DoubleResult NaN encoding ---

internal const val DOUBLE_NAN_CANONICAL: Long = 0x7FF8_0000_0000_0000L
internal const val DOUBLE_ERROR_PAYLOAD_MASK: Long = 0x0000_0000_0000_000FL

// --- Error kind codes (shared across all encodings) ---

internal const val ERROR_BOUNDS: Int = 0
internal const val ERROR_BAD_LENGTH: Int = 1
internal const val ERROR_TRUNCATED: Int = 2
internal const val ERROR_INVALID_UTF8: Int = 3
internal const val ERROR_TRAILING_DATA: Int = 4

// === Shared helpers ===

internal fun encodeErrorKind(error: KompactDecodeError): Int =
    when (error) {
        KompactDecodeError.BoundsError -> ERROR_BOUNDS
        KompactDecodeError.BadLengthPrefix -> ERROR_BAD_LENGTH
        KompactDecodeError.TruncatedNested -> ERROR_TRUNCATED
        KompactDecodeError.InvalidUtf8 -> ERROR_INVALID_UTF8
        KompactDecodeError.TrailingData -> ERROR_TRAILING_DATA
    }

/**
 * Shared kind->case mapping for the failure path. Every packed layout
 * extracts its kind and delegates here, so the case mapping cannot drift. An
 * unrecognized kind falls back to [KompactDecodeError.BoundsError].
 */
internal fun decodeError(kind: Int): KompactDecodeError =
    when (kind) {
        ERROR_BAD_LENGTH -> KompactDecodeError.BadLengthPrefix
        ERROR_TRUNCATED -> KompactDecodeError.TruncatedNested
        ERROR_INVALID_UTF8 -> KompactDecodeError.InvalidUtf8
        ERROR_TRAILING_DATA -> KompactDecodeError.TrailingData
        else -> KompactDecodeError.BoundsError
    }

internal fun decodeErrorFromSmallBits(packed: Long): KompactDecodeError =
    decodeError(((packed ushr RESULT_ERROR_KIND_SHIFT) and 0x7L).toInt())

internal fun encodeSmallSuccess(value: Long): Long = RESULT_OK_FLAG or (value and RESULT_VALUE_MASK)

internal fun encodeSmallFailure(error: KompactDecodeError): Long =
    encodeErrorKind(error).toLong() shl RESULT_ERROR_KIND_SHIFT

internal fun isPackedFailure(packed: Long): Boolean = (packed and PACKED_FAILURE_MASK) == PACKED_FAILURE_BASE

internal fun encodePackedFailure(error: KompactDecodeError): Long = PACKED_FAILURE_BASE or encodeErrorKind(error).toLong()

internal fun decodePackedError(packed: Long): KompactDecodeError = decodeError((packed and 0x7L).toInt())

internal fun isDoubleFailure(packed: Long): Boolean {
    val payload = packed and DOUBLE_ERROR_PAYLOAD_MASK
    return payload != 0L && (packed and 0x7FF8_0000_0000_0000L) == DOUBLE_NAN_CANONICAL
}

// payload = kind + 1 (1..5); 0 is reserved for the canonical success NaN.
internal fun encodeDoubleFailure(error: KompactDecodeError): Long =
    DOUBLE_NAN_CANONICAL or (encodeErrorKind(error) + 1L)

internal fun decodeDoubleError(packed: Long): KompactDecodeError =
    decodeError((packed and DOUBLE_ERROR_PAYLOAD_MASK).toInt() - 1)

internal fun encodeDoubleSuccess(value: Double): Long = if (value.isNaN()) DOUBLE_NAN_CANONICAL else value.toBits()

internal fun throwDecodeErrorFromSmallBits(packed: Long): Nothing =
    throw KompactDecodeException(decodeErrorFromSmallBits(packed))

internal fun throwDecodeErrorFromPacked(packed: Long): Nothing = throw KompactDecodeException(decodePackedError(packed))

internal fun throwDecodeErrorFromDouble(packed: Long): Nothing = throw KompactDecodeException(decodeDoubleError(packed))

// ====================================================================
// Result value class declarations
//
// Specialized result types:
//   IntResult     (≤32-bit integer; Byte/Short widen to Int via ScalarType
//                  width at read time — readScalar already returns IntResult),
//   FloatResult   (32-bit float, raw bits preserved),
//   DoubleResult  (64-bit float, reserved quiet-NaN payload for errors),
//   BooleanResult (single bit), and LongResult (a full-domain result).
// The four packed result types each wrap a Long; LongResult is a regular class.
// ====================================================================

@JvmInline
public value class IntResult private constructor(
    private val packed: Long,
) {
    public val isSuccess: Boolean get() = (packed and RESULT_OK_FLAG) != 0L
    public val isFailure: Boolean get() = (packed and RESULT_OK_FLAG) == 0L
    public val error: KompactDecodeError? get() =
        if (isSuccess) null else decodeErrorFromSmallBits(packed)

    public fun getOrThrow(): Int =
        if (isSuccess) {
            (packed and RESULT_VALUE_MASK).toInt()
        } else {
            throwDecodeErrorFromSmallBits(packed)
        }

    public companion object {
        public fun success(value: Int): IntResult = IntResult(encodeSmallSuccess(value.toLong()))

        public fun failure(error: KompactDecodeError): IntResult = IntResult(encodeSmallFailure(error))
    }
}

@JvmInline
public value class FloatResult private constructor(
    private val packed: Long,
) {
    public val isSuccess: Boolean get() = (packed and RESULT_OK_FLAG) != 0L
    public val isFailure: Boolean get() = (packed and RESULT_OK_FLAG) == 0L
    public val error: KompactDecodeError? get() =
        if (isSuccess) null else decodeErrorFromSmallBits(packed)

    public fun getOrThrow(): Float =
        if (isSuccess) {
            Float.fromBits((packed and RESULT_VALUE_MASK).toInt())
        } else {
            throwDecodeErrorFromSmallBits(packed)
        }

    public companion object {
        public fun success(value: Float): FloatResult =
            FloatResult(encodeSmallSuccess(value.toRawBits().toLong() and 0xFFFF_FFFFL))

        public fun failure(error: KompactDecodeError): FloatResult = FloatResult(encodeSmallFailure(error))
    }
}

@JvmInline
public value class BooleanResult private constructor(
    private val packed: Long,
) {
    public val isSuccess: Boolean get() = (packed and RESULT_OK_FLAG) != 0L
    public val isFailure: Boolean get() = (packed and RESULT_OK_FLAG) == 0L
    public val error: KompactDecodeError? get() =
        if (isSuccess) null else decodeErrorFromSmallBits(packed)

    public fun getOrThrow(): Boolean =
        if (isSuccess) {
            (packed and RESULT_VALUE_MASK) != 0L
        } else {
            throwDecodeErrorFromSmallBits(packed)
        }

    public companion object {
        public fun success(value: Boolean): BooleanResult =
            BooleanResult(encodeSmallSuccess(if (value) 1L else 0L))

        public fun failure(error: KompactDecodeError): BooleanResult = BooleanResult(encodeSmallFailure(error))
    }
}

/**
 * Checked 64-bit integer result.
 *
 * Holds either any [Long] value or a [KompactDecodeError]. This regular class
 * stores success and failure separately without reserving valid values as
 * sentinels. Unlike the other scalar result types, it is not a value class.
 * Equality and hashing use the held value and error, not object identity.
 */
public class LongResult private constructor(
    private val successValue: Long,
    /** Decode error on failure; `null` on success. */
    public val error: KompactDecodeError?,
) {
    /** Decoded value on success; `null` on failure. */
    public val value: Long? get() = if (error == null) successValue else null

    public val isSuccess: Boolean get() = error == null
    public val isFailure: Boolean get() = error != null

    public fun getOrThrow(): Long {
        val decodeError = error
        if (decodeError != null) throw KompactDecodeException(decodeError)
        return successValue
    }

    public override fun equals(other: Any?): Boolean =
        other is LongResult && successValue == other.successValue && error == other.error

    public override fun hashCode(): Int = error?.hashCode() ?: successValue.hashCode()

    public override fun toString(): String =
        if (error == null) "LongResult(value=$successValue)" else "LongResult(error=$error)"

    public companion object {
        public fun success(value: Long): LongResult = LongResult(value, null)

        public fun failure(error: KompactDecodeError): LongResult = LongResult(0L, error)
    }
}

@JvmInline
public value class DoubleResult private constructor(
    private val packed: Long,
) {
    public val isSuccess: Boolean get() = !isDoubleFailure(packed)
    public val isFailure: Boolean get() = isDoubleFailure(packed)
    public val error: KompactDecodeError? get() =
        if (isSuccess) null else decodeDoubleError(packed)

    public fun getOrThrow(): Double =
        if (isSuccess) {
            Double.fromBits(packed)
        } else {
            throwDecodeErrorFromDouble(packed)
        }

    public companion object {
        public fun success(value: Double): DoubleResult = DoubleResult(encodeDoubleSuccess(value))

        public fun failure(error: KompactDecodeError): DoubleResult = DoubleResult(encodeDoubleFailure(error))
    }
}
