package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.ElasticsearchTestBase
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.bulk.DocSourceAndMeta
import dev.evo.elasticmagic.bulk.IdActionMeta
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.doc.list
import dev.evo.elasticmagic.qf.AttrRangeFacetFilter.SelectedValue

import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull

import kotlin.test.Test

class AttrRangeFacetFilterTests : ElasticsearchTestBase() {
    override val indexName = "attr-range-facet-filter"

    companion object {
        private const val ATTR_ID = 1

        private fun rangeAttrDocSource(value: Float) = DynDocSource {
            it[RangeAttrsDoc.rangeAttrs] = encodeRangeAttrWithValue(ATTR_ID, -3.0F)
        }
    }

    object RangeAttrsDoc : Document() {
        val rangeAttrs by long()
    }

    @Test
    fun filterExpression() = runTestWithTransports {
        withFixtures(
            RangeAttrsDoc,
            listOf(
                rangeAttrDocSource(Float.NEGATIVE_INFINITY),
                rangeAttrDocSource(-3.0F),
                rangeAttrDocSource(-1.0F),
                rangeAttrDocSource(-0.0F),
                rangeAttrDocSource(0.0F),
                rangeAttrDocSource(1.0F),
                rangeAttrDocSource(3.0F),
                rangeAttrDocSource(Float.POSITIVE_INFINITY),
            ).mapIndexed { ix, doc ->
                DocSourceAndMeta(IdActionMeta(ix.toString()), doc)
            },
            cleanup = true
        ) {
            SearchQuery().count(index).count shouldBe 8

            val countResult = SearchQuery()
                .filter(SelectedValue.Gte(ATTR_ID, 2.0F).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
            countResult.count shouldBe 2
        }
    }
}
