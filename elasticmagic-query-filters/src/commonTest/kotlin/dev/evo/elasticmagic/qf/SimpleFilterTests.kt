package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import io.kotest.matchers.maps.shouldContainExactly
import kotlin.test.Test

class SimpleFilterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {


    object BikeQueryFilters : QueryFilters() {
        val price by SimpleFilter(BikeDocument.price, mode = FilterMode.INTERSECT)
        val priceUnion by SimpleFilter(BikeDocument.price, mode = FilterMode.UNION)
    }

    @Test
    fun emptyFilter() = testWithCompiler {

        val sq = SearchQuery()
        BikeQueryFilters.apply(sq, mapOf(listOf("price") to listOf()))

        compile(sq).body shouldContainExactly mapOf()
    }

    @Test
    fun intersectFilter() = testWithCompiler {

        val sq = SearchQuery()
        BikeQueryFilters.apply(sq, mapOf(listOf("price") to listOf("1.0", "3.0")))

        compile(sq).body shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "bool" to mapOf(
                                "filter" to listOf(
                                    mapOf(
                                        "term" to mapOf("price" to 1.0F)
                                    ),
                                    mapOf(
                                        "term" to mapOf("price" to 3.0F)
                                    )
                                )
                            ),
                        )
                    )
                )
            )
        )
    }

    @Test
    fun simpleFilterWithFacetFilter() = testWithCompiler {

        val sq = SearchQuery()
        BikeQueryFilters.apply(
            sq,
            mapOf(
                listOf("price") to listOf("1.0", "3.0"),
                listOf("manufacturer") to listOf("BMC", "Cube")
            )
        )

        compile(sq).body shouldContainExactly mapOf(
            "post_filter" to mapOf(
                "terms" to mapOf(
                    "manufacturer" to listOf(
                        "BMC",
                        "Cube"
                    )
                )
            ),
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "bool" to mapOf(
                                "filter" to listOf(
                                    mapOf(
                                        "term" to mapOf("price" to 1.0F)
                                    ), mapOf("term" to mapOf("price" to 3.0F))
                                )
                            )
                        )
                    )
                )
            ),
            "aggs" to mapOf("qf:manufacturer" to mapOf("terms" to mapOf("field" to "manufacturer")))
        )
    }

    @Test
    fun unionFilter() = testWithCompiler {

        val sq = SearchQuery()

        compile(sq).body shouldContainExactly mapOf()

        BikeQueryFilters.apply(sq, mapOf(listOf("priceUnion") to listOf("2500", "2020")))

        compile(sq).body shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "terms" to mapOf("price" to listOf(2500.0F, 2020.0F))
                        )
                    ),
                ),
            )
        )
    }
}
