package ch.trancee.kompact.benchmark

import ch.trancee.kompact.runtime.KompactRuntime
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
open class KompactRuntimeBenchmark {
    @Benchmark
    fun readSmallCrossByteInteger(): Long = CanonicalBenchmarkWorkloads.readSmallSpeed().toLong()

    @Benchmark
    fun readSmallCrossByteIntegerReference(): Long =
        HandwrittenReference.readUnsigned(CanonicalBenchmarkWorkloads.small, 20, 10).toLong()

    @Benchmark
    fun readMediumUnalignedInteger(): Long =
        CanonicalBenchmarkWorkloads.readMediumUnaligned().toLong()

    @Benchmark
    fun readMediumUnalignedIntegerReference(): Long =
        HandwrittenReference.readUnsigned(CanonicalBenchmarkWorkloads.medium, 67, 17).toLong()

    @Benchmark
    fun readLargeNestedElement(): Long =
        CanonicalBenchmarkWorkloads.readLargeNestedElement().toLong()

    @Benchmark
    fun readLargeNestedElementReference(): Long =
        HandwrittenReference.readUnsigned(CanonicalBenchmarkWorkloads.large, 1531, 13).toLong()

    @Benchmark
    fun writeSmallCrossByteInteger(): Byte {
        KompactRuntime.writeBits(CanonicalBenchmarkWorkloads.small, 20, 10, 511uL)
        return CanonicalBenchmarkWorkloads.small[2]
    }

    @Benchmark
    fun readMediumOptionalPresence(): Boolean =
        KompactRuntime.readBitsBoolean(CanonicalBenchmarkWorkloads.medium, 129)

    @Benchmark
    fun writeLargeNestedElement(): Byte {
        KompactRuntime.writeBits(CanonicalBenchmarkWorkloads.large, 1531, 13, 4095uL)
        return CanonicalBenchmarkWorkloads.large[191]
    }
}
