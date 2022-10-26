package dev.evo.elasticmagic.serde

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole

abstract class BaseJsonBenchmark {
    private val json = """
        {
          "int": 1,
          "float": 1.234,
          "string": "Hello world!",
          "bool": true,
          "array": [1, 2, 3]
        }
    """.trimIndent()

    protected abstract val serde: Serde

    @Benchmark
    fun objIter(bh: Blackhole) {
        val obj = serde.deserializer.objFromString(json)
        val iter = obj.iterator()
        while (iter.hasNext()) {
            bh.consume(iter.key())
            bh.consume(iter.anyOrNull())
        }
    }

    @Benchmark
    fun objForEach(bh: Blackhole) {
        val obj = serde.deserializer.objFromString(json)
        obj.forEach { key, value ->
            bh.consume(key)
            bh.consume(value)
        }
    }
}
