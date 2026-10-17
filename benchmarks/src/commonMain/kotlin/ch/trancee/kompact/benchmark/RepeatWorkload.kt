package ch.trancee.kompact.benchmark

import ch.trancee.kompact.runtime.KompactCursor
import ch.trancee.kompact.runtime.KompactRepeatWorkspace
import ch.trancee.kompact.runtime.KompactWriter

public class RepeatWorkload(
    public val count: Int,
) {
    public val fixedBytes: ByteArray
    public val variableBytes: ByteArray
    public val fixedCheckpoints: IntArray = IntArray(checkpointCount(count))
    public val variableCheckpoints: IntArray = IntArray(checkpointCount(count))
    public val fixedWorkspace: KompactRepeatWorkspace = KompactRepeatWorkspace(fixedCheckpoints)
    public val variableWorkspace: KompactRepeatWorkspace = KompactRepeatWorkspace(variableCheckpoints)
    public val fixedCursor: KompactCursor
    public val variableCursor: KompactCursor

    init {
        require(count >= 0)
        val fixed = KompactWriter()
        fixed.writeBitsLong(32, count.toLong())
        val variable = KompactWriter()
        variable.writeBitsLong(32, count.toLong())
        repeat(count) { index ->
            fixed.writeBits(13, index and 0x1fff)
            variable.writeBlob(8, ByteArray(index % 9) { index.toByte() })
        }
        fixedBytes = fixed.build()
        variableBytes = variable.build()
        fixedCursor = KompactCursor(fixedBytes)
        variableCursor = KompactCursor(variableBytes)
    }

    public fun fixedIndex(): Int {
        fixedCursor.reset(fixedBytes)
        check(fixedCursor.readFixedRepeat(32, 13, fixedWorkspace) == KompactCursor.STATUS_OK)
        return fixedCursor.position xor fixedWorkspace.count
    }

    public fun variableIndex(): Int {
        variableCursor.reset(variableBytes)
        check(variableCursor.readVariableRepeat(32, 8, variableWorkspace) == KompactCursor.STATUS_OK)
        return variableCursor.position xor variableWorkspace.count
    }

    private companion object {
        fun checkpointCount(count: Int): Int =
            count / KompactRepeatWorkspace.CHECKPOINT_INTERVAL +
                if (count % KompactRepeatWorkspace.CHECKPOINT_INTERVAL == 0) 0 else 1
    }
}
