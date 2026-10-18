package ch.trancee.kompact.runtime

/**
 * Outcome of a [KompactCursor] operation.
 *
 * Entries are singletons, so returning or storing a status never allocates.
 * Malformed-input statuses expose the matching shared [KompactDecodeError]
 * through [decodeError]; success and caller-contract statuses (invalid widths,
 * bounds, values, alignment, aliasing, or workspace capacity) expose `null`.
 */
public enum class KompactStatus(
    /** Shared decode error for malformed input, or `null` for success and caller-contract statuses. */
    public val decodeError: KompactDecodeError?,
) {
    /** Operation completed successfully. */
    OK(null),

    /** A reset region was outside the representable buffer bit bounds. */
    INVALID_BOUNDS(null),

    /** A bit width or prefix width is unsupported. */
    INVALID_WIDTH(null),

    /** An operation exceeded the cursor's bounded region. */
    BOUNDS_ERROR(KompactDecodeError.BoundsError),

    /** A value cannot be represented by the requested wire width. */
    INVALID_VALUE(null),

    /** A length prefix is malformed or cannot be represented by cursor offsets. */
    BAD_LENGTH_PREFIX(KompactDecodeError.BadLengthPrefix),

    /** A byte-oriented operation began at a non-byte-aligned position. */
    UNALIGNED_REGION(null),

    /** Mutable cursors or source and destination buffers alias incompatibly. */
    INVALID_ARGUMENT(null),

    /** A string payload contains malformed UTF-8. */
    INVALID_UTF8(KompactDecodeError.InvalidUtf8),

    /** The caller-owned repeat workspace has too few checkpoint slots. */
    WORKSPACE_TOO_SMALL(null),

    /** A repeated element is truncated or its payload crosses its region. */
    TRUNCATED_INPUT(KompactDecodeError.TruncatedNested),
}
