package ch.trancee.kompact.result

/**
 * Spec: .scratch/kompact-spec/issues/05-variable-length-framing.md
 *
 * Result value classes for the length-prefixed read accessors. The
 * reference/value payload cannot fit alongside the ok-flag + error
 * code in a single `Long`, so these wrap a `ByteArray` (or list)
 * reference inline; zero-allocation on the success hot path is
 * preserved because the underlying buffer is the caller's
 * (Ticket 03 — caller-owned ByteArray).
 */
public expect value class StringResult(public val packed: Long) {
    public val isOk: Boolean
    public val isError: Boolean
    public val errorCode: Int
    public val value: String
    public fun toLong(): Long

    public companion object {
        public fun success(value: String): StringResult
        public fun failure(error: Int): StringResult
    }
}

public expect value class BlobResult(public val packed: Long) {
    public val isOk: Boolean
    public val isError: Boolean
    public val errorCode: Int
    public val value: ByteArray
    public fun toLong(): Long

    public companion object {
        public fun success(value: ByteArray): BlobResult
        public fun failure(error: Int): BlobResult
    }
}

public expect value class NestedResult(public val packed: Long) {
    public val isOk: Boolean
    public val isError: Boolean
    public val errorCode: Int
    public val value: ByteArray
    public fun toLong(): Long

    public companion object {
        public fun success(value: ByteArray): NestedResult
        public fun failure(error: Int): NestedResult
    }
}

public expect value class RepeatedResult(public val packed: Long) {
    public val isOk: Boolean
    public val isError: Boolean
    public val errorCode: Int
    public val count: Int
    public val elements: List<ByteArray>
    public fun toLong(): Long

    public companion object {
        public fun success(count: Int, elements: List<ByteArray>): RepeatedResult
        public fun failure(error: Int): RepeatedResult
    }
}
