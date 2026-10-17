package ch.trancee.kompact.benchmark

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

@State(Scope.Benchmark)
public open class RepeatIndexBenchmark {
    @Param("65", "4096", "65536")
    public var count: Int = 65

    private lateinit var workload: RepeatWorkload

    @Setup
    public fun setup() {
        workload = RepeatWorkload(count)
    }

    @Benchmark
    public open fun fixedIndex(): Int = workload.fixedIndex()

    @Benchmark
    public open fun variableIndex(): Int = workload.variableIndex()
}
