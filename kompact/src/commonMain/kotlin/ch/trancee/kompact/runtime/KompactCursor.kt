package ch.trancee.kompact.runtime

/**
 * Reusable bounded cursor over a caller-owned buffer.
 *
 * The cursor retains the buffer and bit bounds supplied by the caller. Reset
 * validates the complete region before replacing its current binding. Failures
 * are reported through primitive status and offset fields. This mutable cursor
 * is not thread-safe; use separate cursors for simultaneously active regions.
 */
public class KompactCursor(
    buffer: ByteArray,
) {
    /** The currently bound caller-owned buffer. */
    public var buffer: ByteArray = buffer
        private set

    /** Inclusive start of this cursor's bounded bit region. */
    public var startBit: Int = 0
        private set

    /** Current bit position, always between [startBit] and [endBit]. */
    public var position: Int = 0
        internal set

    /** Exclusive end of this cursor's bounded bit region. */
    public var endBit: Int = 0
        private set

    /** Value from the last successful [readBits] or byte/repeat operation. */
    public var valueBits: Long = 0L
        internal set

    /** Status code from the most recent cursor operation. */
    public var status: Int = STATUS_OK
        internal set

    /** Bit position associated with the most recent non-success status. */
    public var errorBitOffset: Int = 0
        internal set

    /** Primitive detail associated with the most recent non-success status. */
    public var errorDetail: Int = 0
        internal set

    init {
        reset(buffer)
    }

    /**
     * Rebind this cursor to a validated bounded region.
     *
     * The default [position] is [startBit]; the default [endBit] is the
     * complete buffer. Invalid bounds leave the previous buffer and region
     * intact and set [status] to [STATUS_INVALID_BOUNDS]. Diagnostics use the
     * first violated bound in this order: negative [startBit], [position]
     * before [startBit], then [endBit] before [position]. If no specific
     * detail is selected, or the selected detail is zero, [errorDetail] is
     * [endBit]. [errorBitOffset] is the non-negative requested [position].
     */
    public fun reset(
        buffer: ByteArray,
        startBit: Int = 0,
        position: Int = startBit,
        endBit: Int = buffer.size * 8,
    ): Int {
        val bufferEnd = buffer.size.toLong() * 8L
        val valid = startBit >= 0 &&
            position >= startBit &&
            endBit >= position &&
            endBit.toLong() <= bufferEnd
        if (!valid) {
            val invalidDetail =
                when {
                    startBit < 0 -> startBit
                    position < startBit -> position
                    endBit < position -> endBit
                    else -> 0
                }
            status = STATUS_INVALID_BOUNDS
            errorBitOffset = position.coerceAtLeast(0)
            errorDetail = if (invalidDetail == 0) endBit else invalidDetail
            return status
        }

        this.buffer = buffer
        this.startBit = startBit
        this.position = position
        this.endBit = endBit
        valueBits = 0L
        status = STATUS_OK
        errorBitOffset = 0
        errorDetail = 0
        return status
    }

    /** Bind this cursor to a byte-aligned region after checking bit-offset overflow. */
    public fun resetByteRange(
        buffer: ByteArray,
        startByte: Int,
        endByte: Int,
    ): Int {
        if (startByte < 0 || endByte < startByte || endByte.toLong() * 8L > Int.MAX_VALUE.toLong()) {
            return fail(STATUS_INVALID_BOUNDS, position, endByte)
        }
        return reset(buffer, startByte * 8, startByte * 8, endByte * 8)
    }

    /**
     * Bind a region whose byte bounds and bit-offset conversion were validated
     * by the caller before any operation state was committed.
     */
    internal fun bindValidatedByteRange(
        buffer: ByteArray,
        startByte: Int,
        endByte: Int,
    ) {
        this.buffer = buffer
        startBit = startByte * 8
        position = startBit
        endBit = endByte * 8
        valueBits = 0L
        clearError()
    }

    /**
     * Read [bitWidth] bits into [valueBits], advancing only when the entire
     * read fits in this cursor's region.
     */
    public fun readBits(bitWidth: Int): Int {
        if (bitWidth !in 1..64) {
            return fail(STATUS_INVALID_WIDTH, position, bitWidth)
        }
        if (bitWidth.toLong() > endBit.toLong() - position.toLong()) {
            return fail(STATUS_BOUNDS_ERROR, position, bitWidth)
        }

        valueBits = KompactRuntime.readBitsLong(buffer, position, bitWidth)
        position += bitWidth
        clearError()
        return status
    }

    /**
     * Validate and skip a variable-width repeat without binding workspace state.
     *
     * [workspaceCapacity] must provide one slot per 64-element block, rounded
     * up. An insufficient capacity returns [STATUS_WORKSPACE_TOO_SMALL].
     */
    public fun skipVariableRepeat(
        countPrefixWidth: Int,
        elementPrefixWidth: Int,
        workspaceCapacity: Int,
    ): Int = KompactCursorRepeats.skipVariableRepeat(this, countPrefixWidth, elementPrefixWidth, workspaceCapacity)

    /**
     * Validate and skip a fixed-width repeat without binding workspace state.
     *
     * [workspaceCapacity] must provide one slot per 64-element block, rounded
     * up. An insufficient capacity returns [STATUS_WORKSPACE_TOO_SMALL].
     */
    public fun skipFixedRepeat(
        countPrefixWidth: Int,
        elementBitWidth: Int,
        workspaceCapacity: Int,
    ): Int = KompactCursorRepeats.skipFixedRepeat(this, countPrefixWidth, elementBitWidth, workspaceCapacity)

    /** Check that [bitCount] bits fit without advancing this cursor. */
    public fun ensureAvailable(bitCount: Int): Int {
        if (bitCount < 0) {
            return fail(STATUS_INVALID_WIDTH, position, bitCount)
        }
        if (bitCount.toLong() > endBit.toLong() - position.toLong()) {
            return fail(STATUS_BOUNDS_ERROR, position, bitCount)
        }
        clearError()
        return status
    }

    /** Preflight a raw byte copy, advancing without mutating the destination. */
    public fun preflightRawByteRange(range: KompactByteRange): Int {
        return KompactCursorByteRanges.preflightRawByteRange(this, range)
    }

    /** Copy an already encoded borrowed byte range without adding a prefix. */
    public fun writeRawByteRange(range: KompactByteRange): Int {
        return KompactCursorByteRanges.writeRawByteRange(this, range)
    }

    /** Bind this range to the complete byte-aligned cursor region. */
    public fun captureRegion(
        range: KompactByteRange,
        validateUtf8: Boolean = false,
    ): Int = KompactCursorByteRanges.captureRegion(this, range, validateUtf8)

    /** Advance over [bitCount] bits after checking the complete skip. */
    public fun skipBits(bitCount: Int): Int {
        val checked = ensureAvailable(bitCount)
        if (checked != STATUS_OK) return checked
        position += bitCount
        return status
    }

    /**
     * Write the low [bitWidth] bits of [value] at the current position.
     *
     * This raw operation deliberately truncates high bits. It leaves the
     * buffer and position unchanged when the width or bounded region is
     * invalid; checked model writes should validate their declared range
     * before calling it.
     */
    public fun writeBitsUnchecked(
        bitWidth: Int,
        value: Long,
    ): Int {
        if (bitWidth !in 1..64) {
            return fail(STATUS_INVALID_WIDTH, position, bitWidth)
        }
        if (bitWidth.toLong() > endBit.toLong() - position.toLong()) {
            return fail(STATUS_BOUNDS_ERROR, position, bitWidth)
        }

        KompactRuntime.writeBitsLong(buffer, position, bitWidth, value)
        position += bitWidth
        valueBits = value
        clearError()
        return status
    }

    /** Write a signed [value] only when it fits the requested two's-complement width. */
    public fun writeSigned(
        bitWidth: Int,
        value: Long,
    ): Int {
        val checked = validateSigned(bitWidth, value)
        if (checked != STATUS_OK) return checked
        return writeBitsUnchecked(bitWidth, value)
    }

    /** Validate a signed value without changing the buffer or cursor position. */
    public fun validateSigned(
        bitWidth: Int,
        value: Long,
    ): Int {
        if (bitWidth !in 1..64) {
            return fail(STATUS_INVALID_WIDTH, position, bitWidth)
        }
        if (bitWidth < 64) {
            val shift = Long.SIZE_BITS - bitWidth
            val minimum = Long.MIN_VALUE shr shift
            val maximum = Long.MAX_VALUE shr shift
            if (value < minimum || value > maximum) {
                return fail(STATUS_INVALID_VALUE, position, bitWidth)
            }
        }
        clearError()
        return status
    }

    /** Write a non-negative [value] only when it fits the requested unsigned width. */
    public fun writeUnsigned(
        bitWidth: Int,
        value: Long,
    ): Int {
        val checked = validateUnsigned(bitWidth, value)
        if (checked != STATUS_OK) return checked
        return writeBitsUnchecked(bitWidth, value)
    }

    /** Validate a non-negative unsigned value without changing cursor data. */
    public fun validateUnsigned(
        bitWidth: Int,
        value: Long,
    ): Int {
        if (bitWidth !in 1..64) {
            return fail(STATUS_INVALID_WIDTH, position, bitWidth)
        }
        if (value < 0L || (bitWidth < 63 && value ushr bitWidth != 0L)) {
            return fail(STATUS_INVALID_VALUE, position, bitWidth)
        }
        clearError()
        return status
    }

    /** Write a full-domain unsigned [value], including all 64 bits when requested. */
    public fun writeUnsigned(
        bitWidth: Int,
        value: ULong,
    ): Int {
        val checked = validateUnsigned(bitWidth, value)
        if (checked != STATUS_OK) return checked
        return writeBitsUnchecked(bitWidth, value.toLong())
    }

    /** Validate a full-domain unsigned value without changing cursor data. */
    public fun validateUnsigned(
        bitWidth: Int,
        value: ULong,
    ): Int {
        if (bitWidth !in 1..64) {
            return fail(STATUS_INVALID_WIDTH, position, bitWidth)
        }
        if (bitWidth < 64 && value shr bitWidth != 0uL) {
            return fail(STATUS_INVALID_VALUE, position, bitWidth)
        }
        clearError()
        return status
    }

    /** Write zero bits across an arbitrary bounded region. */
    public fun writeZeros(bitCount: Int): Int {
        val checked = ensureAvailable(bitCount)
        if (checked != STATUS_OK) return checked
        var remaining = bitCount
        while (remaining > 0) {
            val chunk = minOf(remaining, Long.SIZE_BITS)
            KompactRuntime.writeBitsLong(buffer, position, chunk, 0L)
            position += chunk
            valueBits = 0L
            remaining -= chunk
        }
        clearError()
        return status
    }

    /**
     * Read a length-prefixed byte region into [nestedCursor] without creating a
     * slice or child writer. On success this cursor advances past the complete
     * region and [nestedCursor] is reset to its exact payload bounds.
     */
    public fun readNested(
        prefixWidth: Int,
        nestedCursor: KompactCursor,
    ): Int = KompactCursorByteRanges.readNested(this, prefixWidth, nestedCursor)

    /**
     * Write a known byte length and configure [nestedCursor] over that bounded
     * payload. The parent advances beyond the payload before it is encoded.
     */
    public fun writeNested(
        prefixWidth: Int,
        payloadByteLength: Int,
        nestedCursor: KompactCursor,
    ): Int = KompactCursorByteRanges.writeNested(this, prefixWidth, payloadByteLength, nestedCursor)

    /** Read a length-prefixed byte range into caller-owned [range] state. */
    public fun readByteRange(
        prefixWidth: Int,
        range: KompactByteRange,
    ): Int = KompactCursorByteRanges.readByteRange(this, prefixWidth, range)

    /**
     * Read and validate a length-prefixed UTF-8 byte range without creating a
     * [String]. The range and cursor advance only when the complete payload is
     * valid UTF-8.
     */
    public fun readUtf8Range(
        prefixWidth: Int,
        range: KompactByteRange,
    ): Int = KompactCursorByteRanges.readUtf8Range(this, prefixWidth, range)

    /**
     * Validate and skip a length-prefixed byte region without storing a range.
     * Set [validateUtf8] for string payloads.
     */
    public fun skipByteRange(
        prefixWidth: Int,
        validateUtf8: Boolean = false,
    ): Int = KompactCursorByteRanges.skipByteRange(this, prefixWidth, validateUtf8)

    /**
     * Preflight a borrowed range write and advance this cursor without touching
     * its buffer. Generated encoders use a separate caller-owned cursor for
     * whole-model failure atomicity.
     */
    public fun preflightByteRangeWrite(
        prefixWidth: Int,
        range: KompactByteRange,
        validateUtf8: Boolean = false,
    ): Int = KompactCursorByteRanges.preflightByteRangeWrite(this, prefixWidth, range, validateUtf8)

    /** Copy a preflight error's primitive diagnostics onto this cursor. */
    public fun copyErrorFrom(source: KompactCursor): Int = KompactCursorByteRanges.copyErrorFrom(this, source)

    /** Reject using one mutable cursor for two simultaneously active regions. */
    public fun ensureDistinct(other: KompactCursor): Int = KompactCursorByteRanges.ensureDistinct(this, other)

    /**
     * Write a length-prefixed borrowed range without allocating an intermediate
     * payload array. Overlapping in-place copies are rejected before mutation.
     */
    public fun writeByteRange(
        prefixWidth: Int,
        range: KompactByteRange,
    ): Int = KompactCursorByteRanges.writeByteRange(this, prefixWidth, range)

    /**
     * Validate a count-prefixed variable-width repeat and build its sparse
     * index in caller-owned [workspace] storage.
     *
     * The workspace needs one checkpoint slot per 64-element block, rounded
     * up. Insufficient capacity returns [STATUS_WORKSPACE_TOO_SMALL] without
     * advancing this cursor or changing the workspace binding.
     */
    public fun readVariableRepeat(
        countPrefixWidth: Int,
        elementPrefixWidth: Int,
        workspace: KompactRepeatWorkspace,
    ): Int = KompactCursorRepeats.readVariableRepeat(this, countPrefixWidth, elementPrefixWidth, workspace)

    /**
     * Validate and index a count-prefixed fixed-width repeat in caller-owned
     * [workspace] storage.
     *
     * The workspace needs one checkpoint slot per 64-element block, rounded
     * up. Insufficient capacity returns [STATUS_WORKSPACE_TOO_SMALL] without
     * advancing this cursor or changing the workspace binding.
     */
    public fun readFixedRepeat(
        countPrefixWidth: Int,
        elementBitWidth: Int,
        workspace: KompactRepeatWorkspace,
    ): Int = KompactCursorRepeats.readFixedRepeat(this, countPrefixWidth, elementBitWidth, workspace)

    /**
     * Reset [elementCursor] to one previously indexed repeat element.
     *
     * The parent position is unchanged. [workspace] must still be bound to
     * this cursor's buffer, and that buffer must remain unchanged.
     */
    public fun readRepeatedElement(
        index: Int,
        workspace: KompactRepeatWorkspace,
        elementCursor: KompactCursor,
    ): Int = KompactCursorRepeats.readRepeatedElement(this, index, workspace, elementCursor)

    internal fun fail(
        code: Int,
        bitOffset: Int,
        detail: Int,
    ): Int {
        status = code
        errorBitOffset = bitOffset
        errorDetail = detail
        return status
    }

    internal fun clearError() {
        status = STATUS_OK
        errorBitOffset = 0
        errorDetail = 0
    }

    public companion object {
        /** Operation completed successfully. */
        public const val STATUS_OK: Int = 0
        /** A reset region was outside the representable buffer bit bounds. */
        public const val STATUS_INVALID_BOUNDS: Int = 1
        /** A bit width or prefix width is unsupported. */
        public const val STATUS_INVALID_WIDTH: Int = 2
        /** An operation exceeded the cursor's bounded region. */
        public const val STATUS_BOUNDS_ERROR: Int = 3
        /** A value cannot be represented by the requested wire width. */
        public const val STATUS_INVALID_VALUE: Int = 4
        /** A length prefix is malformed or cannot be represented by cursor offsets. */
        public const val STATUS_BAD_LENGTH_PREFIX: Int = 5
        /** A byte-oriented operation began at a non-byte-aligned position. */
        public const val STATUS_UNALIGNED_REGION: Int = 6
        /** Mutable cursors or source and destination buffers alias incompatibly. */
        public const val STATUS_INVALID_ARGUMENT: Int = 7
        /** A string payload contains malformed UTF-8. */
        public const val STATUS_INVALID_UTF8: Int = 8
        /** The caller-owned repeat workspace has too few checkpoint slots. */
        public const val STATUS_WORKSPACE_TOO_SMALL: Int = 9
        /** A repeated element is truncated or its payload crosses its region. */
        public const val STATUS_TRUNCATED_INPUT: Int = 10
    }
}
