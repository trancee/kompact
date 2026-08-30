package ch.trancee.kompact.benchmark

import ch.trancee.kompact.runtime.KompactRuntime
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

@State(Scope.Benchmark)
open class KompactRuntimeBenchmark {
    private lateinit var packet: ByteArray

    @Setup
    fun setup() {
        packet = byteArrayOf(0x2A, 0x00, 0xF2.toByte(), 0x5F)
    }

    @Benchmark
    fun readCrossByteTenBitInteger(): Int =
        KompactRuntime.readBits(packet, bitOffset = 20, bitWidth = 10).toInt()

    @Benchmark
    fun writeCrossByteTenBitInteger(): Byte {
        KompactRuntime.writeBits(packet, bitOffset = 20, bitWidth = 10, value = 511uL)
        return packet[2]
    }
}
