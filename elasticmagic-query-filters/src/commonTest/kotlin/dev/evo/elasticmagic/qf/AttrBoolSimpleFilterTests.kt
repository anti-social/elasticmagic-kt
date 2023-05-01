package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.Document

import io.kotest.matchers.maps.shouldContainExactly

import kotlin.test.Test

class AttrBoolSimpleFilterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    object ProductDoc : Document() {
        val attrs by long()
    }

    object AttrsQueryFilters : QueryFilters() {
        val attrsBool by AttrBoolSimpleFilter(ProductDoc.attrs, "a")
    }


    @Test
    fun empty() = testWithCompiler {
        val sq = SearchQuery()
        AttrsQueryFilters.apply(sq, mapOf(listOf("a", "1") to listOf()))

        compile(sq).body shouldContainExactly mapOf()
    }

    @Test
    fun default() = testWithCompiler {
        val sq = SearchQuery()
        AttrsQueryFilters.apply(sq, mapOf(listOf("a", "1") to listOf("true", "false")))

        compile(sq).body shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "terms" to mapOf(
                                "attrs" to listOf(3L, 2L)
                            )
                        )
                    )
                )
            )
        )
    }
}
