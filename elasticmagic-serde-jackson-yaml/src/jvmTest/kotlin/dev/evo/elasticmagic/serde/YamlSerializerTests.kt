package dev.evo.elasticmagic.serde

import dev.evo.elasticmagic.serde.jackson.YamlSerializer

import io.kotest.matchers.shouldBe

import kotlin.test.Test

class YamlSerializerTests {
    @Test
    fun preserveFieldsOrder() {
        val obj = YamlSerializer.obj {
            field("size", 10)
            field("from", 20)
            obj("query") {
                field("zeta", 1)
                field("alpha", 2)
            }
            array("sort") {
                obj {
                    field("rank", "desc")
                    field("id", "asc")
                }
            }
            field("_source", false)
        }
        obj.serialize() shouldBe """
            |---
            |size: 10
            |from: 20
            |query:
            |  zeta: 1
            |  alpha: 2
            |sort:
            |- rank: "desc"
            |  id: "asc"
            |_source: false
            |
        """.trimMargin()
    }
}
