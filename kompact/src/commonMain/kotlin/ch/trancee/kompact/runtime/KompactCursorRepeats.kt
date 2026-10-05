package ch.trancee.kompact.runtime

internal object KompactCursorRepeats {
    fun skipVariableRepeat(
        cursor: KompactCursor,
        countPrefixWidth: Int,
        elementPrefixWidth: Int,
        workspaceCapacity: Int,
    ): Int {
        if (!isPrefixWidth(countPrefixWidth) || !isPrefixWidth(elementPrefixWidth) || workspaceCapacity < 0) {
            return cursor.fail(
                KompactCursor.STATUS_INVALID_WIDTH,
                cursor.position,
                when {
                    !isPrefixWidth(countPrefixWidth) -> countPrefixWidth
                    !isPrefixWidth(elementPrefixWidth) -> elementPrefixWidth
                    else -> workspaceCapacity
                },
            )
        }
        if (cursor.position % 8 != 0) {
            return cursor.fail(KompactCursor.STATUS_UNALIGNED_REGION, cursor.position, countPrefixWidth)
        }
        val countEnd = cursor.position.toLong() + countPrefixWidth
        if (countEnd > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, countPrefixWidth)
        }
        val encodedCount = KompactRuntime.readBitsLong(cursor.buffer, cursor.position, countPrefixWidth)
        if (encodedCount > Int.MAX_VALUE.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, countPrefixWidth)
        }
        val count = encodedCount.toInt()
        val checkpointCount = checkpointCount(count)
        if (checkpointCount > workspaceCapacity) {
            return cursor.fail(KompactCursor.STATUS_WORKSPACE_TOO_SMALL, cursor.position, checkpointCount)
        }

        var scan = countEnd
        var index = 0
        while (index < count) {
            val prefixEnd = scan + elementPrefixWidth
            if (prefixEnd > cursor.endBit.toLong()) {
                return cursor.fail(KompactCursor.STATUS_TRUNCATED_INPUT, scan.toInt(), elementPrefixWidth)
            }
            val length = KompactRuntime.readBitsLong(cursor.buffer, scan.toInt(), elementPrefixWidth)
            if (length > Int.MAX_VALUE.toLong()) {
                return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, scan.toInt(), elementPrefixWidth)
            }
            val elementEnd = prefixEnd + length * 8L
            if (elementEnd > cursor.endBit.toLong()) {
                return cursor.fail(KompactCursor.STATUS_TRUNCATED_INPUT, prefixEnd.toInt(), length.toInt())
            }
            scan = elementEnd
            index++
        }
        cursor.position = scan.toInt()
        cursor.valueBits = count.toLong()
        cursor.clearError()
        return cursor.status
    }

    fun skipFixedRepeat(
        cursor: KompactCursor,
        countPrefixWidth: Int,
        elementBitWidth: Int,
        workspaceCapacity: Int,
    ): Int {
        if (!isPrefixWidth(countPrefixWidth) || elementBitWidth !in 1..64 || workspaceCapacity < 0) {
            return cursor.fail(
                KompactCursor.STATUS_INVALID_WIDTH,
                cursor.position,
                when {
                    !isPrefixWidth(countPrefixWidth) -> countPrefixWidth
                    elementBitWidth !in 1..64 -> elementBitWidth
                    else -> workspaceCapacity
                },
            )
        }
        if (cursor.position % 8 != 0) {
            return cursor.fail(KompactCursor.STATUS_UNALIGNED_REGION, cursor.position, countPrefixWidth)
        }
        val countEnd = cursor.position.toLong() + countPrefixWidth
        if (countEnd > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, countPrefixWidth)
        }
        val encodedCount = KompactRuntime.readBitsLong(cursor.buffer, cursor.position, countPrefixWidth)
        if (encodedCount > Int.MAX_VALUE.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, countPrefixWidth)
        }
        val count = encodedCount.toInt()
        val checkpointCount = checkpointCount(count)
        if (checkpointCount > workspaceCapacity) {
            return cursor.fail(KompactCursor.STATUS_WORKSPACE_TOO_SMALL, cursor.position, checkpointCount)
        }
        val repeatEnd = countEnd + count.toLong() * elementBitWidth
        if (repeatEnd > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_TRUNCATED_INPUT, countEnd.toInt(), count)
        }
        cursor.position = repeatEnd.toInt()
        cursor.valueBits = count.toLong()
        cursor.clearError()
        return cursor.status
    }

    fun readVariableRepeat(
        cursor: KompactCursor,
        countPrefixWidth: Int,
        elementPrefixWidth: Int,
        workspace: KompactRepeatWorkspace,
    ): Int {
        if (!isPrefixWidth(countPrefixWidth) || !isPrefixWidth(elementPrefixWidth)) {
            return cursor.fail(
                KompactCursor.STATUS_INVALID_WIDTH,
                cursor.position,
                if (!isPrefixWidth(countPrefixWidth)) countPrefixWidth else elementPrefixWidth,
            )
        }
        if (cursor.position % 8 != 0) {
            return cursor.fail(KompactCursor.STATUS_UNALIGNED_REGION, cursor.position, countPrefixWidth)
        }

        val countEnd = cursor.position.toLong() + countPrefixWidth
        if (countEnd > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, countPrefixWidth)
        }
        val encodedCount = KompactRuntime.readBitsLong(cursor.buffer, cursor.position, countPrefixWidth)
        if (encodedCount > Int.MAX_VALUE.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, countPrefixWidth)
        }
        val count = encodedCount.toInt()
        val neededCheckpoints = checkpointCount(count)
        if (neededCheckpoints > workspace.capacity) {
            return cursor.fail(KompactCursor.STATUS_WORKSPACE_TOO_SMALL, cursor.position, neededCheckpoints)
        }

        var scan = countEnd
        var index = 0
        while (index < count) {
            val prefixEnd = scan + elementPrefixWidth
            if (prefixEnd > cursor.endBit.toLong()) {
                return cursor.fail(KompactCursor.STATUS_TRUNCATED_INPUT, scan.toInt(), elementPrefixWidth)
            }
            val length = KompactRuntime.readBitsLong(cursor.buffer, scan.toInt(), elementPrefixWidth)
            if (length > Int.MAX_VALUE.toLong()) {
                return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, scan.toInt(), elementPrefixWidth)
            }
            val elementEnd = prefixEnd + length * 8L
            if (elementEnd > cursor.endBit.toLong()) {
                return cursor.fail(KompactCursor.STATUS_TRUNCATED_INPUT, prefixEnd.toInt(), length.toInt())
            }
            scan = elementEnd
            index++
        }

        val repeatStart = countEnd.toInt()
        scan = countEnd
        index = 0
        while (index < count) {
            if (index % KompactRepeatWorkspace.CHECKPOINT_INTERVAL == 0) {
                workspace.storeCheckpoint(index / KompactRepeatWorkspace.CHECKPOINT_INTERVAL, scan.toInt())
            }
            val length = KompactRuntime.readBitsLong(cursor.buffer, scan.toInt(), elementPrefixWidth)
            scan += elementPrefixWidth + length * 8L
            index++
        }

        workspace.bind(cursor.buffer, count, repeatStart, scan.toInt(), elementPrefixWidth, elementBitWidth = 0)
        cursor.position = scan.toInt()
        cursor.valueBits = count.toLong()
        cursor.clearError()
        return cursor.status
    }

    fun readFixedRepeat(
        cursor: KompactCursor,
        countPrefixWidth: Int,
        elementBitWidth: Int,
        workspace: KompactRepeatWorkspace,
    ): Int {
        if (!isPrefixWidth(countPrefixWidth) || elementBitWidth !in 1..64) {
            return cursor.fail(
                KompactCursor.STATUS_INVALID_WIDTH,
                cursor.position,
                if (!isPrefixWidth(countPrefixWidth)) countPrefixWidth else elementBitWidth,
            )
        }
        if (cursor.position % 8 != 0) {
            return cursor.fail(KompactCursor.STATUS_UNALIGNED_REGION, cursor.position, countPrefixWidth)
        }

        val countEnd = cursor.position.toLong() + countPrefixWidth
        if (countEnd > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, countPrefixWidth)
        }
        val encodedCount = KompactRuntime.readBitsLong(cursor.buffer, cursor.position, countPrefixWidth)
        if (encodedCount > Int.MAX_VALUE.toLong()) {
            return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, cursor.position, countPrefixWidth)
        }
        val count = encodedCount.toInt()
        val neededCheckpoints = checkpointCount(count)
        if (neededCheckpoints > workspace.capacity) {
            return cursor.fail(KompactCursor.STATUS_WORKSPACE_TOO_SMALL, cursor.position, neededCheckpoints)
        }

        val repeatEnd = countEnd + count.toLong() * elementBitWidth
        if (repeatEnd > cursor.endBit.toLong()) {
            return cursor.fail(KompactCursor.STATUS_TRUNCATED_INPUT, countEnd.toInt(), count)
        }

        val repeatStart = countEnd.toInt()
        var checkpoint = 0
        var elementIndex = 0
        while (elementIndex < count) {
            if (elementIndex % KompactRepeatWorkspace.CHECKPOINT_INTERVAL == 0) {
                val checkpointOffset = repeatStart.toLong() + elementIndex.toLong() * elementBitWidth
                workspace.storeCheckpoint(checkpoint, checkpointOffset.toInt())
                checkpoint++
            }
            elementIndex++
        }

        workspace.bind(
            buffer = cursor.buffer,
            count = count,
            startBit = repeatStart,
            endBit = repeatEnd.toInt(),
            elementPrefixWidth = 0,
            elementBitWidth = elementBitWidth,
        )
        cursor.position = repeatEnd.toInt()
        cursor.valueBits = count.toLong()
        cursor.clearError()
        return cursor.status
    }

    fun readRepeatedElement(
        cursor: KompactCursor,
        index: Int,
        workspace: KompactRepeatWorkspace,
        elementCursor: KompactCursor,
    ): Int {
        if (elementCursor === cursor || !workspace.isBoundTo(cursor.buffer)) {
            return cursor.fail(KompactCursor.STATUS_INVALID_ARGUMENT, cursor.position, index)
        }
        if (index < 0 || index >= workspace.count) {
            return cursor.fail(KompactCursor.STATUS_BOUNDS_ERROR, workspace.startBit, index)
        }

        if (workspace.elementBitWidth != 0) {
            val elementStart = workspace.startBit.toLong() + index.toLong() * workspace.elementBitWidth
            val elementEnd = elementStart + workspace.elementBitWidth
            if (elementEnd > workspace.endBit.toLong()) {
                return cursor.fail(KompactCursor.STATUS_BAD_LENGTH_PREFIX, elementStart.toInt(), workspace.elementBitWidth)
            }
            val startBit = elementStart.toInt()
            elementCursor.reset(cursor.buffer, startBit, startBit, elementEnd.toInt())
            cursor.valueBits = index.toLong()
            cursor.clearError()
            return cursor.status
        }

        val interval = KompactRepeatWorkspace.CHECKPOINT_INTERVAL
        val checkpointElement = index / interval * interval
        var elementStart = workspace.checkpointAt(index / interval).toLong()
        var skipped = checkpointElement
        while (skipped < index) {
            val prefixEnd = elementStart + workspace.elementPrefixWidth
            if (prefixEnd > workspace.endBit.toLong()) {
                return cursor.fail(
                    KompactCursor.STATUS_BAD_LENGTH_PREFIX,
                    elementStart.toInt(),
                    workspace.elementPrefixWidth,
                )
            }
            val length =
                KompactRuntime.readBitsLong(cursor.buffer, elementStart.toInt(), workspace.elementPrefixWidth)
            if (length > Int.MAX_VALUE.toLong()) {
                return cursor.fail(
                    KompactCursor.STATUS_BAD_LENGTH_PREFIX,
                    elementStart.toInt(),
                    workspace.elementPrefixWidth,
                )
            }
            val elementEnd = prefixEnd + length * 8L
            if (elementEnd > workspace.endBit.toLong()) {
                return cursor.fail(
                    KompactCursor.STATUS_BAD_LENGTH_PREFIX,
                    elementStart.toInt(),
                    workspace.elementPrefixWidth,
                )
            }
            elementStart = elementEnd
            skipped++
        }

        val payloadStart = elementStart + workspace.elementPrefixWidth
        if (payloadStart > workspace.endBit.toLong()) {
            return cursor.fail(
                KompactCursor.STATUS_BAD_LENGTH_PREFIX,
                elementStart.toInt(),
                workspace.elementPrefixWidth,
            )
        }
        val length = KompactRuntime.readBitsLong(cursor.buffer, elementStart.toInt(), workspace.elementPrefixWidth)
        if (length > Int.MAX_VALUE.toLong()) {
            return cursor.fail(
                KompactCursor.STATUS_BAD_LENGTH_PREFIX,
                elementStart.toInt(),
                workspace.elementPrefixWidth,
            )
        }
        val payloadEnd = payloadStart + length * 8L
        if (payloadEnd > workspace.endBit.toLong()) {
            return cursor.fail(
                KompactCursor.STATUS_BAD_LENGTH_PREFIX,
                elementStart.toInt(),
                workspace.elementPrefixWidth,
            )
        }

        val startBit = payloadStart.toInt()
        elementCursor.reset(cursor.buffer, startBit, startBit, payloadEnd.toInt())
        cursor.valueBits = index.toLong()
        cursor.clearError()
        return cursor.status
    }

    private fun checkpointCount(count: Int): Int =
        count / KompactRepeatWorkspace.CHECKPOINT_INTERVAL +
            if (count % KompactRepeatWorkspace.CHECKPOINT_INTERVAL == 0) 0 else 1

    private fun isPrefixWidth(width: Int): Boolean = width == 8 || width == 16 || width == 32
}
