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

    @Test
    fun preserveFieldsOrder() {
        val obj = JsonSerializer.obj {
            obj("query") {
                obj("bool") {
                    array("must") {
                        obj {
                            obj("term") {
                                field("status", "active")
                            }
                        }
                    }
                    array("filter") {}
                }
            }
            field("size", 10)
            field("from", 20)
            array("sort") {
                obj {
                    field("rank", "desc")
                    field("id", "asc")
                }
            }
            obj("aggs") {}
            field("_source", false)
        }
        obj.serialize() shouldBe """
            {"query":{"bool":{"must":[{"term":{"status":"active"}}],"filter":[]}},"size":10,"from":20,"sort":[{"rank":"desc","id":"asc"}],"aggs":{},"_source":false}
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

    @Test
    fun preserveFieldsOrder() {
        val obj = PrettyJsonSerializer.obj {
            field("size", 10)
            field("from", 20)
            obj("query") {
                field("zeta", 1)
                field("alpha", 2)
            }
            field("_source", false)
        }
        obj.serialize() shouldBe """
            {
                "size" : 10,
                "from" : 20,
                "query" : {
                    "zeta" : 1,
                    "alpha" : 2
                },
                "_source" : false
            }
        """.trimIndent()
    }
}
