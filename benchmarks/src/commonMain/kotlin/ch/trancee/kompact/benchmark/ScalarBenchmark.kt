package ch.trancee.kompact.benchmark

import ch.trancee.kompact.runtime.KompactCursor
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
public open class ScalarBenchmark {
    private val bytes: ByteArray = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
    private val cursor: KompactCursor = KompactCursor(bytes)
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

    @Benchmark
    public open fun allocationControl(): ByteArray = bytes.copyOf()
}
