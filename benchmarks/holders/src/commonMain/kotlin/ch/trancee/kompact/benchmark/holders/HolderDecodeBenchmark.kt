package ch.trancee.kompact.benchmark.holders

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

@State(Scope.Benchmark)
public open class ScalarHolderBenchmark {
    private lateinit var workload: HolderWorkload

    @Setup
    public fun setup() {
        workload = HolderWorkload(0)
    }

    @Benchmark
    public open fun decode(): Long = workload.decodeScalar()

    @Benchmark
    public open fun allocationControl(): ByteArray = ByteArray(8)
}

@State(Scope.Benchmark)
public open class RepeatedHolderBenchmark {
    @Param("65", "4096", "65536")
    public var count: Int = 65

    private lateinit var workload: HolderWorkload

    @Setup
    public fun setup() {
        workload = HolderWorkload(count)
    }

    @Benchmark
    public open fun fixedDecode(): Long = workload.decodeFixed()

    @Benchmark
    public open fun variableDecode(): Long = workload.decodeVariable()
}
