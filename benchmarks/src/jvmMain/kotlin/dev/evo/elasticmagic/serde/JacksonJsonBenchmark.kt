package dev.evo.elasticmagic.serde

import dev.evo.elasticmagic.serde.jackson.JsonSerde

import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
open class JacksonJsonBenchmark : BaseJsonBenchmark() {
    override val serde = JsonSerde
}
