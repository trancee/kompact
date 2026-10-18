package ch.trancee.kompact.benchmark

import ch.trancee.kompact.runtime.KompactWriter
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

/** Nested-region encoding cost of [KompactWriter]; run with the `writer` profile. */
@State(Scope.Benchmark)
public open class WriterBenchmark {
    private val writer: KompactWriter = KompactWriter()

    @Benchmark
    public open fun nestedEncode(): Int {
        writer.reset()
        repeat(NESTED_REGIONS) { region ->
            writer.writeNested(lengthPrefixWidth = 8) {
                writeBits(12, region)
                writeBitsLong(40, region.toLong() * 31)
                writeBool(true)
            }
        }
        return writer.build().size
    }

    private companion object {
        const val NESTED_REGIONS = 16
    }
}
