package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.ElasticsearchTestBase
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.bulk.DocSourceAndMeta
import dev.evo.elasticmagic.bulk.IdActionMeta
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.doc.list
import dev.evo.elasticmagic.qf.AttrRangeFacetFilter.SelectedValue
import dev.evo.elasticmagic.serde.serialization.JsonSerde

import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull

import kotlin.math.nextDown
import kotlin.math.nextUp

import kotlin.test.Test

class AttrRangeFacetFilterTests : ElasticsearchTestBase() {
    override val indexName = "attr-range-facet-filter"

    companion object {
        private const val ATTR_ID = 1

        private fun rangeAttrDocSource(value: Float) = DynDocSource {
            it[RangeAttrsDoc.rangeAttrs] = encodeRangeAttrWithValue(ATTR_ID, value)
        }
    }

    object RangeAttrsDoc : Document() {
        val rangeAttrs by long()
    }

    @Test
    fun filterExpression() = runTestWithSerdes(listOf(JsonSerde)) {
        withFixtures(
            RangeAttrsDoc,
            listOf(
                rangeAttrDocSource(Float.POSITIVE_INFINITY),
                rangeAttrDocSource(Float.MAX_VALUE),
                rangeAttrDocSource(3.0F),
                rangeAttrDocSource(1.0F),
                rangeAttrDocSource(Float.MIN_VALUE),
                rangeAttrDocSource(0.0F),
                rangeAttrDocSource(-0.0F),
                rangeAttrDocSource(-Float.MIN_VALUE),
                rangeAttrDocSource(-1.0F),
                rangeAttrDocSource(-3.0F),
                rangeAttrDocSource(-Float.MAX_VALUE),
                rangeAttrDocSource(Float.NEGATIVE_INFINITY),
            ).mapIndexed { ix, doc ->
                DocSourceAndMeta(IdActionMeta(ix.toString()), doc)
            },
            cleanup = true
        ) {
            SearchQuery().count(index).count shouldBe 12

            SearchQuery()
                .filter(SelectedValue.Gte(ATTR_ID, 3.0F.nextDown()).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 3
            SearchQuery()
                .filter(SelectedValue.Gte(ATTR_ID, (-3.0F).nextUp()).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 9
            SearchQuery()
                .filter(SelectedValue.Gte(ATTR_ID, 0.0F).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 7
            SearchQuery()
                .filter(SelectedValue.Gte(ATTR_ID, -0.0F).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 7
            SearchQuery()
                .filter(SelectedValue.Gte(ATTR_ID, Float.MAX_VALUE).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 2
            SearchQuery()
                .filter(SelectedValue.Gte(ATTR_ID, Float.NEGATIVE_INFINITY).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 12

            SearchQuery()
                .filter(SelectedValue.Lte(ATTR_ID, 3.0F.nextDown()).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 9
            SearchQuery()
                .filter(SelectedValue.Lte(ATTR_ID, Float.MIN_VALUE.nextUp()).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 8
            SearchQuery()
                .filter(SelectedValue.Lte(ATTR_ID, 0.0F).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 7
            SearchQuery()
                .filter(SelectedValue.Lte(ATTR_ID, -0.0F).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 7
            SearchQuery()
                .filter(SelectedValue.Lte(ATTR_ID, Float.POSITIVE_INFINITY).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 12
            SearchQuery()
                .filter(SelectedValue.Lte(ATTR_ID, -Float.MAX_VALUE).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 2

            SearchQuery()
                .filter(SelectedValue.Between(ATTR_ID, 0.0F, 0.0F).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 2
            SearchQuery()
                .filter(SelectedValue.Between(ATTR_ID, -0.0F, -0.0F).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 2
            SearchQuery()
                .filter(SelectedValue.Between(ATTR_ID, -3.0F, 3.0F).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 8
            SearchQuery()
                .filter(SelectedValue.Between(ATTR_ID, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 12
            SearchQuery()
                .filter(SelectedValue.Between(ATTR_ID, 0.0F, Float.MAX_VALUE).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 6
            SearchQuery()
                .filter(SelectedValue.Between(ATTR_ID, 1.0F, Float.MAX_VALUE).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 3
            SearchQuery()
                .filter(SelectedValue.Between(ATTR_ID, -Float.MAX_VALUE, -1.0F).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 3
            SearchQuery()
                .filter(SelectedValue.Between(ATTR_ID, -Float.MAX_VALUE, 0.0F).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 6
            SearchQuery()
                .filter(SelectedValue.Between(ATTR_ID, 3.0F, 1.0F).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 0
            SearchQuery()
                .filter(SelectedValue.Between(ATTR_ID, 3.0F, -3.0F).filterExpression(RangeAttrsDoc.rangeAttrs))
                .count(index)
                .count shouldBe 0
        }
    }
}
