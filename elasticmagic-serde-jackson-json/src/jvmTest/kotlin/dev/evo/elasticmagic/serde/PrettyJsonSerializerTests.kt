package dev.evo.elasticmagic.serde

import dev.evo.elasticmagic.serde.jackson.JsonSerializer
import dev.evo.elasticmagic.serde.jackson.PrettyJsonSerializer

import io.kotest.matchers.shouldBe

import kotlin.test.Test


class JsonSerializerTests {
    @Test
    fun objectCtx() {
        val obj = JsonSerializer.obj {
            field("test", 123L)
            field("key", "value")
        }
        obj.serialize() shouldBe """
            {"test":123,"key":"value"}
        """.trimIndent()
    }

    @Test
    fun arrayCtx() {
        val array = JsonSerializer.array {
            value(1L)
            value(2L)
            value(3L)
        }
        array.serialize() shouldBe """
            [1,2,3]
        """.trimIndent()
    }
}

class PrettyJsonSerializerTests {
    @Test
    fun objectCtx() {
        val obj = PrettyJsonSerializer.obj {
            field("test", 123L)
        }
        obj.serialize() shouldBe """
            {
                "test" : 123
            }
        """.trimIndent()
    }

    @Test
    fun arrayCtx() {
        val array = PrettyJsonSerializer.array {
            value(1L)
            value(2L)
            value(3L)
        }
        array.serialize() shouldBe """
            [ 1, 2, 3 ]
        """.trimIndent()
    }
}
