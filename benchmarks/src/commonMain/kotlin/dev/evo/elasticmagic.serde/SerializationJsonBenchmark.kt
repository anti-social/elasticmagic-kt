package dev.evo.elasticmagic.serde

import dev.evo.elasticmagic.serde.serialization.JsonSerde
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
open class SerializationJsonBenchmark {
    private val json = """
        {
          "int": 1,
          "float": 1.234,
          "string": "Hello world!",
          "bool": true,
          "array": [1, 2, 3]
        }
    """.trimIndent()

    @Benchmark
    fun objIter(bh: Blackhole) {
        val obj = JsonSerde.deserializer.objFromString(json)
        val iter = obj.iterator()
        while (iter.hasNext()) {
            val (key, value) = iter.anyOrNull()
            bh.consume(key)
            bh.consume(value)
        }
    }

    @Benchmark
    fun objForEach(bh: Blackhole) {
        val obj = JsonSerde.deserializer.objFromString(json)
        obj.forEach { key, value ->
            bh.consume(key)
            bh.consume(value)
        }
    }
}
