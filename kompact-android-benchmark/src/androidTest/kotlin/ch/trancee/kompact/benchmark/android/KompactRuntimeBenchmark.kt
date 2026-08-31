package ch.trancee.kompact.benchmark.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import ch.trancee.kompact.runtime.KompactRuntime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KompactRuntimeBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    private val packet = ByteArray(244) { index -> (index * 73 + 19).toByte() }
    private var checksum = 0L

    @Test
    fun unalignedNestedRead() {
        benchmarkRule.measureRepeated {
            checksum = checksum xor KompactRuntime.readBits(packet, 1531, 13).toLong()
        }
        check(checksum != Long.MIN_VALUE)
    }

    @Test
    fun unalignedNestedReadReference() {
        benchmarkRule.measureRepeated {
            checksum = checksum xor readReference(packet, 1531, 13).toLong()
        }
        check(checksum != Long.MIN_VALUE)
    }

    @Test
    fun unalignedNestedWrite() {
        benchmarkRule.measureRepeated {
            KompactRuntime.writeBits(packet, 1531, 13, 4095uL)
            checksum = checksum xor packet[191].toLong()
        }
        check(checksum != Long.MIN_VALUE)
    }

    private fun readReference(packet: ByteArray, bitOffset: Int, bitWidth: Int): ULong {
        var result = 0uL
        var consumed = 0
        while (consumed < bitWidth) {
            val packetBit = bitOffset + consumed
            val chunkWidth = minOf(8 - (packetBit and 7), bitWidth - consumed)
            val mask = (1 shl chunkWidth) - 1
            val chunk =
                ((packet[packetBit ushr 3].toInt() and 0xff) ushr (packetBit and 7)) and mask
            result = result or (chunk.toULong() shl consumed)
            consumed += chunkWidth
        }
        return result
    }
}
