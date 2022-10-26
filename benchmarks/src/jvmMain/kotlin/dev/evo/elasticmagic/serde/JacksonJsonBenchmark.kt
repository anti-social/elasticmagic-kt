package dev.evo.elasticmagic.serde

import dev.evo.elasticmagic.serde.jackson.JsonSerde

import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Blackhole

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Benchmark)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
open class JacksonJsonBenchmark {
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