package ch.trancee.kompact.benchmark

import ch.trancee.kompact.runtime.KompactCursor
import ch.trancee.kompact.runtime.KompactRuntime
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
public open class ScalarBenchmark {
    private val bytes: ByteArray = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
    private val cursor: KompactCursor = KompactCursor(bytes)
    private val frame: ByteArray = ByteArray(FRAME_BYTES) { (it * 37 + 11).toByte() }
    private val unaligned: ByteArray = ByteArray(UNALIGNED_BYTES) { (it * 53 + 7).toByte() }
    private var sequence: Long = 0L

    @Benchmark
    public open fun read(): Long {
        cursor.reset(bytes)
        cursor.readBits(64)
        return cursor.valueBits
    }

    @Benchmark
    public open fun write(): Long {
        cursor.reset(bytes)
        sequence++
        cursor.writeBitsUnchecked(64, sequence)
        return cursor.valueBits
    }

    /**
     * Reads seven mixed-width fields that pack one 8-byte frame. Constant
     * offsets and widths match the calls in generated views.
     */
    @Benchmark
    public open fun readFrame(): Long =
            KompactRuntime.readBitsLong(frame, 0, 1) +
            KompactRuntime.readBitsLong(frame, 1, 3) +
            KompactRuntime.readBitsLong(frame, 4, 4) +
            KompactRuntime.readBitsLong(frame, 8, 8) +
            KompactRuntime.readBitsLong(frame, 16, 12) +
            KompactRuntime.readBitsLong(frame, 28, 16) +
            KompactRuntime.readBitsLong(frame, 44, 20)

    /** Writes the same seven fields as [readFrame]. */
    @Benchmark
    public open fun writeFrame(): ByteArray {
        sequence++
        KompactRuntime.writeBitsLong(frame, 0, 1, sequence)
        KompactRuntime.writeBitsLong(frame, 1, 3, sequence)
        KompactRuntime.writeBitsLong(frame, 4, 4, sequence)
        KompactRuntime.writeBitsLong(frame, 8, 8, sequence)
        KompactRuntime.writeBitsLong(frame, 16, 12, sequence)
        KompactRuntime.writeBitsLong(frame, 28, 16, sequence)
        KompactRuntime.writeBitsLong(frame, 44, 20, sequence)
        return frame
    }

    /** Reads a 64-bit field at bit offset 3, so it spans nine bytes. */
    @Benchmark
    public open fun readUnaligned(): Long = KompactRuntime.readBitsLong(unaligned, UNALIGNED_OFFSET, Long.SIZE_BITS)

    /** Writes a 64-bit field at bit offset 3, so it spans nine bytes. */
    @Benchmark
    public open fun writeUnaligned(): ByteArray {
        sequence++
        KompactRuntime.writeBitsLong(unaligned, UNALIGNED_OFFSET, Long.SIZE_BITS, sequence)
        return unaligned
    }

    @Benchmark
    public open fun allocationControl(): ByteArray = bytes.copyOf()

    private companion object {
        const val FRAME_BYTES = 8
        const val UNALIGNED_BYTES = 9
        const val UNALIGNED_OFFSET = 3
    }
}
