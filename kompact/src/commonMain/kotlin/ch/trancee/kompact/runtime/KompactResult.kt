package ch.trancee.kompact.runtime

// ====================================================================
// Ticket 08 — packed-Long encoding constants & shared helpers
//
// ≤32-bit result types (Int/Float/Boolean; Byte/Short widen to Int) use a single
// packed-Long layout:
//   [ ok(bit63) | errorKind(bits 62..60) | rawEnumCode(bits 59..48) | value(bits 47..0) ]
//
// NestedRegionResult uses a packed-Long failure sentinel; successful offsets
// and lengths occupy only non-negative 32-bit halves.
//
// DoubleResult (64-bit) uses reserved quiet-NaN payloads:
//   canonical NaN (payload 0) = success; quiet NaN with non-zero payload
//   in bits 3..0 = failure (error kind encoded as payload 1..4).
//
// These result types use value classes over packed values; this is a type
// representation, not a measured allocation guarantee for every call shape.
// ====================================================================

// --- ≤32-bit result encoding ---

internal val RESULT_OK_FLAG: Long = Long.MIN_VALUE
internal const val RESULT_ERROR_KIND_SHIFT: Int = 60
internal const val RESULT_RAW_ENUM_SHIFT: Int = 48
internal val RESULT_VALUE_MASK: Long = 0x0000_FFFF_FFFF_FFFFL

// --- NestedRegionResult failure sentinel ---

internal val PACKED_FAILURE_MASK: Long = Long.MIN_VALUE or 0x7C00_0000_0000_0000L
internal val PACKED_FAILURE_BASE: Long = Long.MIN_VALUE

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
internal const val ERROR_INVALID_UTF8: Int = 4

// === Shared helpers (commonMain, visible from platform actuals) ===

internal fun encodeErrorKind(error: KompactDecodeError): Int =
    when (error) {
        is KompactDecodeError.BoundsError -> ERROR_BOUNDS
        is KompactDecodeError.BadLengthPrefix -> ERROR_BAD_LENGTH
        is KompactDecodeError.TruncatedNested -> ERROR_TRUNCATED
        is KompactDecodeError.UnknownEnumCode -> ERROR_UNKNOWN_ENUM
        is KompactDecodeError.InvalidUtf8 -> ERROR_INVALID_UTF8
    }

/**
 * Shared kind->case mapping for the failure path (ADR-0005 simplification).
 * Reconstructs the [KompactDecodeError] from a decoded [kind] and [rawCode];
 * an unrecognized kind falls back to [KompactDecodeError.BoundsError]. The
 * per-shape `*Bits` decoders below extract kind/rawCode from their distinct
 * packed-Long layouts and delegate here, so the case mapping cannot drift.
 */
internal fun decodeError(kind: Int, rawCode: Int): KompactDecodeError =
    when (kind) {
        ERROR_BOUNDS -> KompactDecodeError.BoundsError
        ERROR_BAD_LENGTH -> KompactDecodeError.BadLengthPrefix
        ERROR_TRUNCATED -> KompactDecodeError.TruncatedNested
        ERROR_UNKNOWN_ENUM -> KompactDecodeError.UnknownEnumCode(rawCode)
        ERROR_INVALID_UTF8 -> KompactDecodeError.InvalidUtf8
        else -> KompactDecodeError.BoundsError
    }

internal fun decodeErrorFromSmallBits(packed: Long): KompactDecodeError {
    val kind = ((packed ushr RESULT_ERROR_KIND_SHIFT) and 0x7L).toInt()
    val rawCode = ((packed ushr RESULT_RAW_ENUM_SHIFT) and 0xFFFL).toInt()
    return decodeError(kind, rawCode)
}

internal fun encodeSmallSuccess(value: Long): Long = RESULT_OK_FLAG or (value and RESULT_VALUE_MASK)

internal fun encodeSmallFailure(error: KompactDecodeError): Long {
    val kind = encodeErrorKind(error).toLong()
    val rawCode = if (error is KompactDecodeError.UnknownEnumCode) error.rawCode.toLong() else 0L
    return (kind shl RESULT_ERROR_KIND_SHIFT) or (rawCode shl RESULT_RAW_ENUM_SHIFT)
}

internal fun isPackedFailure(packed: Long): Boolean = (packed and PACKED_FAILURE_MASK) == PACKED_FAILURE_BASE

internal fun encodePackedFailure(error: KompactDecodeError): Long {
    val kind = encodeErrorKind(error).toLong()
    val rawCode = if (error is KompactDecodeError.UnknownEnumCode) error.rawCode.toLong() else 0L
    return PACKED_FAILURE_BASE or kind or (rawCode shl 3)
}

internal fun decodePackedError(packed: Long): KompactDecodeError {
    val kind = (packed and 0x7L).toInt()
    val rawCode = ((packed ushr 3) and 0xFFL).toInt()
    return decodeError(kind, rawCode)
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
    return decodeError(kind, rawCode)
}

internal fun encodeDoubleSuccess(value: Double): Long = if (value.isNaN()) DOUBLE_NAN_CANONICAL else value.toBits()

internal fun encodeFloatSuccess(value: Float): Long =
    if (value.isNaN()) FLOAT_NAN_CANONICAL_BITS.toLong() else value.toBits().toLong()

// Centralize the failure-branch throw for the small/long/double result encoders
// so each platform actual's getOrThrow stays one expression (ergonomics-03: throw-helper naming).
internal fun throwDecodeErrorFromSmallBits(packed: Long): Nothing =
    throw KompactDecodeException(decodeErrorFromSmallBits(packed))

internal fun throwDecodeErrorFromPacked(packed: Long): Nothing = throw KompactDecodeException(decodePackedError(packed))

internal fun throwDecodeErrorFromDouble(packed: Long): Nothing = throw KompactDecodeException(decodeDoubleError(packed))

// ====================================================================
// Ticket 08 — result value class declarations (expect)
//
// Specialized result types:
//   IntResult     (≤32-bit integer; Byte/Short widen to Int via ScalarType
//                  width at read time — readScalar already returns IntResult),
//   FloatResult   (32-bit float, NaN-canonical success),
//   DoubleResult  (64-bit float, reserved quiet-NaN payload for errors),
//   BooleanResult (single bit), and LongResult (a full-domain result).
// The four packed result types each wrap a Long; LongResult is a regular class.
// ====================================================================

public expect value class IntResult(
    public val packed: Long,
) {
    public val isSuccess: Boolean
    public val isFailure: Boolean
    public val error: KompactDecodeError?

    public fun getOrThrow(): Int

    public companion object {
        public fun success(value: Int): IntResult

        public fun failure(error: KompactDecodeError): IntResult
    }
}

public expect value class FloatResult(
    public val packed: Long,
) {
    public val isSuccess: Boolean
    public val isFailure: Boolean
    public val error: KompactDecodeError?

    public fun getOrThrow(): Float

    public companion object {
        public fun success(value: Float): FloatResult

        public fun failure(error: KompactDecodeError): FloatResult
    }
}

public expect value class BooleanResult(
    public val packed: Long,
) {
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

    public override fun hashCode(): Int = 31 * successValue.hashCode() + (error?.hashCode() ?: 0)

    public override fun toString(): String =
        "LongResult(${
            when (val decodeError = error) {
                null -> "value=$successValue"
                KompactDecodeError.BoundsError -> "error=BoundsError"
                KompactDecodeError.BadLengthPrefix -> "error=BadLengthPrefix"
                KompactDecodeError.TruncatedNested -> "error=TruncatedNested"
                KompactDecodeError.InvalidUtf8 -> "error=InvalidUtf8"
                is KompactDecodeError.UnknownEnumCode -> "error=UnknownEnumCode(rawCode=${decodeError.rawCode})"
            }
        })"

    public companion object {
        public fun success(value: Long): LongResult = LongResult(value, null)

        public fun failure(error: KompactDecodeError): LongResult = LongResult(0L, error)
    }
}

public expect value class DoubleResult(
    public val packed: Long,
) {
    public val isSuccess: Boolean
    public val isFailure: Boolean
    public val error: KompactDecodeError?

    public fun getOrThrow(): Double

    public companion object {
        public fun success(value: Double): DoubleResult

        public fun failure(error: KompactDecodeError): DoubleResult
    }
}

// === ADR-0005 — opt-in diagnostics tier ===

/**
 * Extra detail returned by the opt-in `decodeFull` path (ADR-0005 §2).
 * Unlike [KompactDecodeError], this includes the byte offset and raw enum code.
 *
 * - [error]: the typed `KompactDecodeError` kind.
 * - [offset]: byte index of the failure (`bitOffset ushr 3`).
 * - [rawCode]: the raw enum ordinal for `UnknownEnumCode`, else 0.
 */
public data class DetailedDecodeError(
    public val error: KompactDecodeError,
    public val offset: Int,
    public val rawCode: Int,
)

/**
 * Opt-in diagnostics result (ADR-0005 §2). Holds either a success [value] or a
 * [DetailedDecodeError]. It is a regular class with explicit value and error
 * state; use it when the additional diagnostic context is useful.
 *
 * Constructed only from `decodeFull` ([KompactRuntime]); the constructor is
 * `internal` so external callers cannot create an inconsistent (value+error)
 * instance.
 */
public class DetailedResult<T> internal constructor(
    public val value: T?,
    public val error: DetailedDecodeError?,
) {
    public val isSuccess: Boolean get() = error == null

    public val isFailure: Boolean get() = error != null
}
