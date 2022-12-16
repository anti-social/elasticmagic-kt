package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.aggs.TermBucket
import dev.evo.elasticmagic.aggs.FilterAggResult
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.Document

import io.kotest.matchers.shouldBe
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull

import kotlin.test.Test

class AttrRangeFacetFiterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    object ProductDoc : Document() {
        val rangeAttrs by long("range_attrs")
    }

    @Test
    fun default() = testWithCompiler {
        val filter = AttrRangeFacetFilter(ProductDoc.rangeAttrs)

        val ctx = filter.prepare("attrs", emptyMap())
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly emptyMap()

    }

    @Test
    fun selected() = testWithCompiler {
        val filter = AttrRangeFacetFilter(ProductDoc.rangeAttrs)

        val ctx = filter.prepare(
            "attrs",
            mapOf(
                listOf("attrs", "1", "gte") to listOf("1")
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "post_filter" to mapOf(
                "range" to mapOf(
                    "range_attrs" to mapOf(
                        "gte" to 0x00000001_3f800000L,
                        "lte" to 0x00000001_7f800000L
                    )
                )
            )
        )

    }
}