package ch.trancee.kompact.result

/**
 * Spec: .scratch/kompact-spec/issues/08-runtime-error-model.md
 *
 * Each scalar kind has its own result value class. The `Long` payload
 * packs: low 56 bits = value (or 0 on failure), bit 56 = ok-flag, bits
 * 57..60 = compact error code, bits 61..63 = reserved.
 *
 * Common declaration has NO `@JvmInline` (PROMPT.md §1 + Ticket 03);
 * JVM `actual` adds `@JvmInline`; iOS actuals are plain value classes.
 * `expect value class` over a primitive `Long` is zero-alloc on both
 * JVM (inline) and Kotlin/Native (value type).
 */

internal const val OK_FLAG: Long = 1L shl 56
internal const val ERROR_SHIFT: Int = 57
internal const val ERROR_MASK: Long = 0xFL shl ERROR_SHIFT

internal fun packOk(value: Long): Long = OK_FLAG or (value and 0x00FFFFFFFFFFFFFFL)
internal fun packFail(error: Int): Long = (error.toLong() and 0xFL) shl ERROR_SHIFT

/** Decode a result's ok-flag and error code. */
internal fun isOk(packed: Long): Boolean = (packed and OK_FLAG) != 0L
internal fun errorOf(packed: Long): Int = ((packed and ERROR_MASK) ushr ERROR_SHIFT).toInt()
internal fun valueOf(packed: Long): Long = packed and 0x00FFFFFFFFFFFFFFL

// --- ByteResult (signed 8-bit) ---

public expect value class ByteResult(public val packed: Long) {
    public val isOk: Boolean
    public val isError: Boolean
    public val errorCode: Int
    public val value: Byte
    public fun toLong(): Long

    public companion object {
        public fun success(value: Byte): ByteResult
        public fun failure(error: Int): ByteResult
    }
}

// --- IntResult (signed 32-bit) ---

public expect value class IntResult(public val packed: Long) {
    public val isOk: Boolean
    public val isError: Boolean
    public val errorCode: Int
    public val value: Int
    public fun toLong(): Long

    public companion object {
        public fun success(value: Int): IntResult
        public fun failure(error: Int): IntResult
    }
}

// --- LongResult (signed 64-bit) ---

public expect value class LongResult(public val packed: Long) {
    public val isOk: Boolean
    public val isError: Boolean
    public val errorCode: Int
    public val value: Long
    public fun toLong(): Long

    public companion object {
        public fun success(value: Long): LongResult
        public fun failure(error: Int): LongResult
    }
}

// --- BooleanResult (1 bit) ---

public expect value class BooleanResult(public val packed: Long) {
    public val isOk: Boolean
    public val isError: Boolean
    public val errorCode: Int
    public val value: Boolean
    public fun toLong(): Long

    public companion object {
        public fun success(value: Boolean): BooleanResult
        public fun failure(error: Int): BooleanResult
    }
}
