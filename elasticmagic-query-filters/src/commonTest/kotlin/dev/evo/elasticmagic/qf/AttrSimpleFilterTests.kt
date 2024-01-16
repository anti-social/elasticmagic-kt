package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.Document

import io.kotest.matchers.maps.shouldContainExactly

import kotlin.test.Test

class AttrSimpleFilterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    private object ProductDoc : Document() {
        val attrs by long()
    }

    private object AttrsQueryFilters : QueryFilters() {
        val attrsBool by AttrSimpleFilter(ProductDoc.attrs, "a")
    }

    @Test
    fun unionTest() = testWithCompiler {
        val sq = SearchQuery()
        AttrsQueryFilters.apply(sq, mapOf(listOf("a", "1") to listOf("12", "13")))

        compile(sq).body shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "terms" to mapOf(
                                "attrs" to listOf(4294967308L, 4294967309L)
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun intersectTest() = testWithCompiler {
        val sq = SearchQuery()
        AttrsQueryFilters.apply(sq, mapOf(listOf("a", "1", "all") to listOf("12", "13")))

        compile(sq).body shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "bool" to mapOf(
                                "filter" to listOf(
                                    mapOf("term" to mapOf("attrs" to 4294967308L)),
                                    mapOf("term" to mapOf("attrs" to 4294967309L))
                                )
                            )
                        )
                    )
                )
            )
        )
    }
}
