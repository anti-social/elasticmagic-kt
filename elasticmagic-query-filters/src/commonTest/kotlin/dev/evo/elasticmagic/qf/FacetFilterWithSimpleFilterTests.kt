package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.Document

import io.kotest.matchers.maps.shouldContainExactly

import kotlin.test.Test

class FacetFilterWithSimpleFilterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    object ProductDoc : Document() {
        val facet by keyword()
        val attrs by long()
    }

    object AttrsQueryFilters : QueryFilters() {
        val facet by FacetFilter(ProductDoc.facet)
        val attrs by AttrSimpleFilter(ProductDoc.attrs, "a")
        val attrsRange by AttrRangeSimpleFilter(ProductDoc.attrs, "a")
        val attrsBool by AttrBoolSimpleFilter(ProductDoc.attrs, "a")
    }

    @Test
    fun applyAllAttrsFilter() = testWithCompiler {
        val sq = SearchQuery()
        AttrsQueryFilters.apply(
            sq, mapOf(
                listOf("a", "1") to listOf("12", "13"),
                listOf("a", "1") to listOf("true", "false"),
                listOf("a", "1", "gte") to listOf("0"),
                listOf("facet") to listOf("A", "B")

            )
        )

        compile(sq).body shouldContainExactly mapOf(
            "post_filter" to mapOf(
                "terms" to mapOf(
                    "facet" to listOf(
                        "A",
                        "B"
                    )
                )
            ),
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "bool" to mapOf(
                                "should" to listOf(
                                    mapOf(
                                        "term" to mapOf("attrs" to 6442450944L)
                                    ),
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
                        ), mapOf("terms" to mapOf("attrs" to listOf(3L, 2L)))
                    )
                )
            ),
            "aggs" to mapOf("qf:facet" to mapOf("terms" to mapOf("field" to "facet")))
        )

    }
}
