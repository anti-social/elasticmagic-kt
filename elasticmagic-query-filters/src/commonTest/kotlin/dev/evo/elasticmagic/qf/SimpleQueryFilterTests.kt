package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import io.kotest.matchers.maps.shouldContainExactly
import kotlin.test.Test

class SimpleQueryFilterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {

    object BikeQueryFilters : QueryFilters() {
        val price by SimpleExpressionsFilter(
            "price",
            values = listOf(
                ExpressionValue("new", BikeDocument.price.gte(1000F)),
                ExpressionValue("used", BikeDocument.price.lte(2000F))
            ),
            mode = FilterMode.INTERSECT
        )
        val priceUnion by SimpleExpressionsFilter(
            "priceUnion",
            values = listOf(
                ExpressionValue("new", BikeDocument.price.gte(4000F)),
                ExpressionValue("used", BikeDocument.price.lte(5000F))
            ),
            mode = FilterMode.UNION
        )
    }

    @Test
    fun intersectFilter() = testWithCompiler {

        val sq = SearchQuery()
        BikeQueryFilters.apply(sq, mapOf(listOf("price") to listOf("new", "used")))

        compile(sq).body shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "bool" to mapOf(
                                "must" to listOf(
                                    mapOf("range" to mapOf("price" to mapOf("gte" to 1000.0F))),
                                    mapOf("range" to mapOf("price" to mapOf("lte" to 2000.0F)))
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun unionFilter() = testWithCompiler {
        val sq = SearchQuery()

        compile(sq).body shouldContainExactly mapOf()

        BikeQueryFilters.apply(sq, mapOf(listOf("priceUnion") to listOf("new", "used")))

        compile(sq).body shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "bool" to mapOf(
                                "should" to listOf(
                                    mapOf("range" to mapOf("price" to mapOf("gte" to 4000.0F))),
                                    mapOf("range" to mapOf("price" to mapOf("lte" to 5000.0F)))
                                )
                            )
                        )
                    )
                )
            )
        )
    }
}
