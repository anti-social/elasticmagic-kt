package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.Document

import io.kotest.matchers.maps.shouldContainExactly

import kotlin.test.Test

class AttrRangeSimpleFilterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    object ProductDoc : Document() {
        val attrs by long()
    }

    object AttrsQueryFilters : QueryFilters() {
        val attrsBool by AttrRangeSimpleFilter(ProductDoc.attrs, "a")
    }

    @Test
    fun default() = testWithCompiler {
        val sq = SearchQuery()
        AttrsQueryFilters.apply(sq, mapOf(listOf("a", "1", "gte") to listOf("0")))

        compile(sq).body shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "bool" to mapOf(
                                "should" to listOf(
                                    mapOf("term" to mapOf("attrs" to 6442450944L)),
                                    mapOf(
                                        "range" to mapOf(
                                            "attrs" to mapOf(
                                                "gte" to 4294967296L,
                                                "lte" to 6434062336L
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun betweenFilter() = testWithCompiler {
        val sq = SearchQuery()
        AttrsQueryFilters.apply(sq, mapOf(listOf("a", "1", "gte") to listOf("1")))
        AttrsQueryFilters.apply(sq, mapOf(listOf("a", "1", "lte") to listOf("100")))

        compile(sq).body shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "range" to mapOf(
                                "attrs" to mapOf(
                                    "gte" to 5360320512L,
                                    "lte" to 6434062336L
                                )
                            )
                        ),
                        mapOf(
                            "bool" to mapOf(
                                "should" to listOf(
                                    mapOf(
                                        "range" to mapOf(
                                            "attrs" to mapOf(
                                                "gte" to 4294967296L,
                                                "lte" to 5415370752L
                                            )
                                        )
                                    ),
                                    mapOf(
                                        "range" to mapOf(
                                            "attrs" to mapOf(
                                                "gte" to 6442450944L,
                                                "lte" to 8581545984L
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    }
}
