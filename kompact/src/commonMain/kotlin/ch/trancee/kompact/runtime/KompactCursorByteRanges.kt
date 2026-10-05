package ch.trancee.kompact.runtime

internal object KompactCursorByteRanges {
    fun preflightRawByteRange(
        cursor: KompactCursor,
        range: KompactByteRange,
    ): Int {
        if (cursor.position % 8 != 0) {
            return cursor.fail(KompactCursor.STATUS_UNALIGNED_REGION, cursor.position, range.size)
        }
        if (range.buffer === cursor.buffer) {
            return cursor.fail(KompactCursor.STATUS_INVALID_ARGUMENT, cursor.position, range.size)
        }
        val end = cursor.position.toLong() + range.size.toLong() * 8L
        if (end > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BOUNDS_ERROR, cursor.position, range.size)
        }
        cursor.position = end.toInt()
        cursor.valueBits = range.size.toLong()
        cursor.clearError()
        return cursor.status
    }

    fun writeRawByteRange(
        cursor: KompactCursor,
        range: KompactByteRange,
    ): Int {
        val writeStartBit = cursor.position
        val checked = preflightRawByteRange(cursor, range)
        if (checked != KompactCursor.STATUS_OK) return checked
        range.buffer.copyInto(cursor.buffer, writeStartBit / 8, range.start, range.end)
        cursor.clearError()
        return cursor.status
    }

    fun captureRegion(
        cursor: KompactCursor,
        range: KompactByteRange,
        validateUtf8: Boolean,
    ): Int {
        if (cursor.startBit % 8 != 0 || cursor.endBit % 8 != 0) {
            return cursor.fail(KompactCursor.STATUS_UNALIGNED_REGION, cursor.position, cursor.endBit - cursor.startBit)
        }
        val first = cursor.startBit / 8
        val last = cursor.endBit / 8
        if (validateUtf8 && !KompactUtf8Validator.isValid(cursor.buffer, first, last)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_UTF8, cursor.startBit, last - first)
        }
        range.reset(cursor.buffer, first, last)
        cursor.clearError()
        return cursor.status
    }

    fun readNested(
        cursor: KompactCursor,
        prefixWidth: Int,
        nestedCursor: KompactCursor,
    ): Int {
        if (nestedCursor === cursor) {
            return cursor.fail(KompactCursor.STATUS_INVALID_ARGUMENT, cursor.position, 0)
        }
        if (!isPrefixWidth(prefixWidth)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_WIDTH, cursor.position, prefixWidth)
        }
        if (cursor.position % 8 != 0) {
            return cursor.fail(KompactCursor.STATUS_UNALIGNED_REGION, cursor.position, prefixWidth)
        }

        val prefixEnd = cursor.position.toLong() + prefixWidth
        if (prefixEnd > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, prefixWidth)
        }
        val length = KompactRuntime.readBitsLong(cursor.buffer, cursor.position, prefixWidth)
        val payloadStart = prefixEnd
        val payloadEnd = payloadStart + length * 8L
        if (length > Int.MAX_VALUE.toLong() || payloadEnd > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, prefixWidth)
        }

        val payloadStartBit = payloadStart.toInt()
        val payloadEndBit = payloadEnd.toInt()
        nestedCursor.bindValidatedByteRange(cursor.buffer, payloadStartBit / 8, payloadEndBit / 8)
        cursor.position = payloadEndBit
        cursor.valueBits = length
        cursor.clearError()
        return cursor.status
    }

    fun writeNested(
        cursor: KompactCursor,
        prefixWidth: Int,
        payloadByteLength: Int,
        nestedCursor: KompactCursor,
    ): Int {
        if (nestedCursor === cursor) {
            return cursor.fail(KompactCursor.STATUS_INVALID_ARGUMENT, cursor.position, 0)
        }
        if (!isPrefixWidth(prefixWidth)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_WIDTH, cursor.position, prefixWidth)
        }
        if (payloadByteLength < 0 || payloadByteLength.toLong() > prefixMaximum(prefixWidth)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_VALUE, cursor.position, payloadByteLength)
        }

        val payloadStart = cursor.position.toLong() + prefixWidth
        val payloadEnd = payloadStart + payloadByteLength.toLong() * 8L
        if (cursor.position % 8 != 0 || payloadStart > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_UNALIGNED_REGION, cursor.position, prefixWidth)
        }
        if (payloadEnd > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BOUNDS_ERROR, cursor.position, payloadByteLength)
        }

        KompactRuntime.writeBitsLong(cursor.buffer, cursor.position, prefixWidth, payloadByteLength.toLong())
        val payloadStartBit = payloadStart.toInt()
        val payloadEndBit = payloadEnd.toInt()
        nestedCursor.reset(cursor.buffer, payloadStartBit, payloadStartBit, payloadEndBit)
        cursor.position = payloadEndBit
        cursor.valueBits = payloadByteLength.toLong()
        cursor.clearError()
        return cursor.status
    }

    fun readByteRange(
        cursor: KompactCursor,
        prefixWidth: Int,
        range: KompactByteRange,
    ): Int = readPrefixedRange(cursor, prefixWidth, range, validateUtf8 = false)

    fun readUtf8Range(
        cursor: KompactCursor,
        prefixWidth: Int,
        range: KompactByteRange,
    ): Int = readPrefixedRange(cursor, prefixWidth, range, validateUtf8 = true)

    fun skipByteRange(
        cursor: KompactCursor,
        prefixWidth: Int,
        validateUtf8: Boolean,
    ): Int {
        if (!isPrefixWidth(prefixWidth)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_WIDTH, cursor.position, prefixWidth)
        }
        if (cursor.position % 8 != 0) {
            return cursor.fail(KompactCursor.STATUS_UNALIGNED_REGION, cursor.position, prefixWidth)
        }
        val payloadStart = cursor.position.toLong() + prefixWidth
        if (payloadStart > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, prefixWidth)
        }
        val length = KompactRuntime.readBitsLong(cursor.buffer, cursor.position, prefixWidth)
        val payloadEnd = payloadStart + length * 8L
        if (length > Int.MAX_VALUE.toLong() || payloadEnd > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, prefixWidth)
        }
        val byteStart = (payloadStart / 8L).toInt()
        val byteEnd = (payloadEnd / 8L).toInt()
        if (validateUtf8 && !KompactUtf8Validator.isValid(cursor.buffer, byteStart, byteEnd)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_UTF8, cursor.position, prefixWidth)
        }

        cursor.position = payloadEnd.toInt()
        cursor.valueBits = length
        cursor.clearError()
        return cursor.status
    }

    fun preflightByteRangeWrite(
        cursor: KompactCursor,
        prefixWidth: Int,
        range: KompactByteRange,
        validateUtf8: Boolean,
    ): Int {
        if (!isPrefixWidth(prefixWidth)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_WIDTH, cursor.position, prefixWidth)
        }
        if (cursor.position % 8 != 0) {
            return cursor.fail(KompactCursor.STATUS_UNALIGNED_REGION, cursor.position, prefixWidth)
        }
        if (range.size.toLong() > prefixMaximum(prefixWidth)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_VALUE, cursor.position, range.size)
        }
        if (validateUtf8 && !KompactUtf8Validator.isValid(range.buffer, range.start, range.end)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_UTF8, cursor.position, range.size)
        }
        if (range.buffer === cursor.buffer) {
            return cursor.fail(KompactCursor.STATUS_INVALID_ARGUMENT, cursor.position, range.size)
        }
        val payloadEnd = cursor.position.toLong() + prefixWidth + range.size.toLong() * 8L
        if (payloadEnd > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BOUNDS_ERROR, cursor.position, range.size)
        }
        cursor.position = payloadEnd.toInt()
        cursor.valueBits = range.size.toLong()
        cursor.clearError()
        return cursor.status
    }

    fun copyErrorFrom(
        cursor: KompactCursor,
        source: KompactCursor,
    ): Int {
        if (source === cursor) return cursor.status
        cursor.status = source.status
        cursor.errorBitOffset = source.errorBitOffset
        cursor.errorDetail = source.errorDetail
        return cursor.status
    }

    fun ensureDistinct(
        cursor: KompactCursor,
        other: KompactCursor,
    ): Int =
        if (other === cursor) {
            cursor.fail(KompactCursor.STATUS_INVALID_ARGUMENT, cursor.position, 0)
        } else {
            cursor.clearError()
            cursor.status
        }

    private fun readPrefixedRange(
        cursor: KompactCursor,
        prefixWidth: Int,
        range: KompactByteRange,
        validateUtf8: Boolean,
    ): Int {
        if (!isPrefixWidth(prefixWidth)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_WIDTH, cursor.position, prefixWidth)
        }
        if (cursor.position % 8 != 0) {
            return cursor.fail(KompactCursor.STATUS_UNALIGNED_REGION, cursor.position, prefixWidth)
        }
        val payloadStart = cursor.position.toLong() + prefixWidth
        if (payloadStart > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, prefixWidth)
        }
        val length = KompactRuntime.readBitsLong(cursor.buffer, cursor.position, prefixWidth)
        val payloadEnd = payloadStart + length * 8L
        if (length > Int.MAX_VALUE.toLong() || payloadEnd > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, prefixWidth)
        }

        val byteStart = (payloadStart / 8L).toInt()
        val byteEnd = (payloadEnd / 8L).toInt()
        if (validateUtf8 && !KompactUtf8Validator.isValid(cursor.buffer, byteStart, byteEnd)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_UTF8, cursor.position, prefixWidth)
        }
        range.reset(cursor.buffer, byteStart, byteEnd)
        cursor.position = payloadEnd.toInt()
        cursor.valueBits = length
        cursor.clearError()
        return cursor.status
    }

    fun writeByteRange(
        cursor: KompactCursor,
        prefixWidth: Int,
        range: KompactByteRange,
    ): Int {
        if (!isPrefixWidth(prefixWidth)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_WIDTH, cursor.position, prefixWidth)
        }
        if (cursor.position % 8 != 0) {
            return cursor.fail(KompactCursor.STATUS_UNALIGNED_REGION, cursor.position, prefixWidth)
        }
        if (range.size.toLong() > prefixMaximum(prefixWidth)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_VALUE, cursor.position, range.size)
        }

        val payloadStart = cursor.position.toLong() + prefixWidth
        val payloadEnd = payloadStart + range.size.toLong() * 8L
        if (payloadEnd > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BOUNDS_ERROR, cursor.position, range.size)
        }
        val writeStartByte = cursor.position / 8
        val writeEndByte = (payloadEnd / 8L).toInt()
        if (range.buffer === cursor.buffer && range.start < writeEndByte && range.end > writeStartByte) {
            return cursor.fail(KompactCursor.STATUS_INVALID_ARGUMENT, cursor.position, range.size)
        }

        KompactRuntime.writeBitsLong(cursor.buffer, cursor.position, prefixWidth, range.size.toLong())
        val payloadStartByte = (payloadStart / 8L).toInt()
        range.buffer.copyInto(cursor.buffer, payloadStartByte, range.start, range.end)
        cursor.position = payloadEnd.toInt()
        cursor.valueBits = range.size.toLong()
        cursor.clearError()
        return cursor.status
    }

    private fun isPrefixWidth(width: Int): Boolean = width == 8 || width == 16 || width == 32

    private fun prefixMaximum(width: Int): Long =
        when (width) {
            8 -> 0xFFL
            16 -> 0xFFFFL
            else -> Int.MAX_VALUE.toLong()
        }
}
