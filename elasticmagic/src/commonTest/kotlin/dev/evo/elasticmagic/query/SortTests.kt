package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.types.IntType
import io.kotest.matchers.maps.shouldContainExactly

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

import kotlin.test.Test

class SortTests : BaseExpressionTest() {
    @Test
    fun sortByField() {
        Sort(MovieDoc.releaseDate).compile() shouldBe listOf("release_date")

        Sort(
            MovieDoc.stars.rank,
            order = Sort.Order.DESC,
            mode = Sort.Mode.SUM,
            numericType = Sort.NumericType.DOUBLE,
            missing = Sort.Missing.Last,
            unmappedType = IntType,
            nested = Sort.Nested(
                MovieDoc.stars,
                filter = MovieDoc.stars.rank.gte(5.0F)
            )
        ).compile().let { sorts ->
            sorts.size shouldBe 1
            sorts[0].shouldBeInstanceOf<Map<Any, Any?>>() shouldContainExactly mapOf(
                "stars.rank" to mapOf(
                    "order" to "desc",
                    "mode" to "sum",
                    "numeric_type" to "double",
                    "missing" to "_last",
                    "unmapped_type" to "integer",
                    "nested" to mapOf(
                        "path" to "stars",
                        "filter" to mapOf(
                            "range" to mapOf(
                                "stars.rank" to mapOf("gte" to 5.0F)
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun sortByScript() {
        Sort(
            Script.Id("score-by-rank"),
            scriptType = "number",
        ).compile().let { sorts ->
            sorts.size shouldBe 1
            sorts[0].shouldBeInstanceOf<Map<Any, Any?>>() shouldContainExactly mapOf(
                "_script" to mapOf(
                    "type" to "number",
                    "script" to mapOf(
                        "id" to "score-by-rank"
                    )
                )
            )
        }

        Sort(
            Script.Id(
                "score-by-rank",
                lang = "expression",
                params = Params(
                    "field" to "rating"
                )
            ),
            scriptType = "number",
            order = Sort.Order.DESC,
            mode = Sort.Mode.AVG,
        ).compile().let { sorts ->
            sorts.size shouldBe 1
            sorts[0].shouldBeInstanceOf<Map<Any, Any?>>() shouldBe mapOf(
                "_script" to mapOf(
                    "type" to "number",
                    "script" to mapOf(
                        "id" to "score-by-rank",
                        "lang" to "expression",
                        "params" to mapOf(
                            "field" to "rating",
                        )
                    ),
                    "order" to "desc",
                    "mode" to "avg",
                )
            )
        }
    }
}
