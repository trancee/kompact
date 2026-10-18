@file:OptIn(ch.trancee.kompact.annotations.KompactPreview::class)

package ch.trancee.kompact.benchmark.holders

import ch.trancee.kompact.runtime.KompactByteRange
import ch.trancee.kompact.runtime.KompactCursor
import ch.trancee.kompact.runtime.KompactRepeatWorkspace
import ch.trancee.kompact.runtime.KompactRepeatWorkspace.Companion.CHECKPOINT_INTERVAL
import ch.trancee.kompact.runtime.KompactStatus
import ch.trancee.kompact.runtime.KompactWriter

public class HolderWorkload(
    public val count: Int,
) {
    init {
        require(count in 0..65536)
    }

    private val checkpointCount: Int = (count + CHECKPOINT_INTERVAL - 1) / CHECKPOINT_INTERVAL
    public val scalarInputs: Array<ByteArray> = Array(2) { frame(it, FrameShape.SCALAR) }
    public val fixedInputs: Array<ByteArray> = Array(2) { frame(it, FrameShape.FIXED) }
    public val variableInputs: Array<ByteArray> = Array(2) { frame(it, FrameShape.VARIABLE) }
    public val cursor: KompactCursor = KompactCursor(ByteArray(0))
    public val probe: KompactCursor = KompactCursor(ByteArray(0))
    public val scalarHolder: ScalarFrameViewHolder = ScalarFrameViewHolder()
    public val fixedHolder: FixedFrameViewHolder =
        FixedFrameViewHolder(
            payload = KompactByteRange(ByteArray(0)),
            samplesWorkspace = KompactRepeatWorkspace(IntArray(checkpointCount)),
            samplesBytes = KompactByteRange(ByteArray(0)),
        )
    public val variableHolder: VariableFrameViewHolder =
        VariableFrameViewHolder(
            payload = KompactByteRange(ByteArray(0)),
            chunksWorkspace = KompactRepeatWorkspace(IntArray(checkpointCount)),
            chunksBytes = KompactByteRange(ByteArray(0)),
            chunksElement = KompactByteRange(ByteArray(0)),
        )
    public var lastStatus: KompactStatus = KompactStatus.OK
        private set
    private var nextInput: Int = 0

    public fun decodeScalar(): Long {
        reset(scalarInputs)
        lastStatus = scalarHolder.decodeInto(cursor, probe)
        check(lastStatus == KompactStatus.OK) { "Scalar holder decode failed: status=$lastStatus" }
        return scalarHolder.id.toLong() + scalarHolder.sequence + scalarHolder.timestamp + cursor.position
    }

    public fun decodeFixed(): Long {
        reset(fixedInputs)
        lastStatus = fixedHolder.decodeInto(cursor, probe)
        check(lastStatus == KompactStatus.OK) { "Fixed holder decode failed: status=$lastStatus" }
        return fixedHolder.id.toLong() + fixedHolder.sequence + fixedHolder.payload.size +
            fixedHolder.samplesWorkspace.count + cursor.position
    }

    public fun decodeVariable(): Long {
        reset(variableInputs)
        lastStatus = variableHolder.decodeInto(cursor, probe)
        check(lastStatus == KompactStatus.OK) { "Variable holder decode failed: status=$lastStatus" }
        return variableHolder.id.toLong() + variableHolder.sequence + variableHolder.payload.size +
            variableHolder.chunksWorkspace.count + cursor.position
    }

    private fun reset(inputs: Array<ByteArray>) {
        lastStatus = cursor.reset(inputs[nextInput])
        check(lastStatus == KompactStatus.OK) { "Prepared frame reset failed: status=$lastStatus" }
        nextInput = nextInput xor 1
    }

    private fun frame(
        seed: Int,
        shape: FrameShape,
    ): ByteArray {
        val writer = KompactWriter()
        writer.writeBits(16, 7 + seed)
        writer.writeBitsLong(32, 17L + seed)
        when (shape) {
            FrameShape.SCALAR -> {
                writer.writeBitsLong(64, 123456789L + seed)
            }

            FrameShape.FIXED, FrameShape.VARIABLE -> {
                writer.writeBlob(16, ByteArray(128) { (it + seed).toByte() })
                writer.writeBitsLong(32, count.toLong())
                repeat(count) { index ->
                    if (shape == FrameShape.FIXED) {
                        writer.writeBits(16, (index + seed) and 0xffff)
                    } else {
                        writer.writeBlob(8, ByteArray(index % 9) { (index + seed).toByte() })
                    }
                }
            }
        }
        return writer.build()
    }

    private enum class FrameShape {
        SCALAR,
        FIXED,
        VARIABLE,
    }
}
