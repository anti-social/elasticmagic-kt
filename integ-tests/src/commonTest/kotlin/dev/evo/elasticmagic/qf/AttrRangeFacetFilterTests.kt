package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.ElasticsearchTestBase
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.bulk.DocSourceAndMeta
import dev.evo.elasticmagic.bulk.IdActionMeta
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.qf.AttrRangeFacetFilter.SelectedValue

import io.kotest.matchers.shouldBe

import kotlin.test.Test

class AttrRangeFacetFilterTests : ElasticsearchTestBase() {
    override val indexName = "attr-range-facet-filter"

    object ItemDoc : Document() {
        val rangeAttrs by long()
    }

    companion object {
        private const val ATTR_ID = 2
        private val VALUES = listOf(
            Float.NEGATIVE_INFINITY,
            -Float.MAX_VALUE,
            -1.1F,
            -Float.MIN_VALUE,
            -0.0F,
            0.0F,
            Float.MIN_VALUE,
            1.1F,
            Float.MAX_VALUE,
            Float.POSITIVE_INFINITY,
            Float.NaN,
        )
        private val FIXTURES = listOf(1, ATTR_ID, Int.MAX_VALUE)
            .flatMap { attrId ->
                VALUES.map { v ->
                    DynDocSource {
                        it[ItemDoc.rangeAttrs] = encodeRangeAttrWithValue(attrId, v)
                    }
                }
            }
            .mapIndexed { ix, doc ->
                DocSourceAndMeta(IdActionMeta(ix.toString()), doc)
            }
    }

    private suspend fun TestScope.testRangeFiltering(selectedValue: SelectedValue, expectedCount: Long) {
        SearchQuery()
            .filter(selectedValue.filterExpression(ItemDoc.rangeAttrs))
            .count(index).count shouldBe expectedCount
    }

    @Test
    fun rangeQueries() = runTestWithSerdes {
        withFixtures(ItemDoc, FIXTURES, cleanup = false) {
            var searchQuery = SearchQuery()
            searchQuery.execute(index).totalHits shouldBe 33

            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, Float.NEGATIVE_INFINITY),
                10
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, -Float.MAX_VALUE),
                9
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, -3.4028233E38F),
                8
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, -2.0F),
                8
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, -0.0F),
                6
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, 0.0F),
                6
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, Float.MIN_VALUE),
                4
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, 2.8E-45F),
                3
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, Float.MAX_VALUE),
                2
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, Float.POSITIVE_INFINITY),
                1
            )

            testRangeFiltering(
                SelectedValue.Lte(ATTR_ID, Float.POSITIVE_INFINITY),
                10
            )
            testRangeFiltering(
                SelectedValue.Lte(ATTR_ID, Float.MAX_VALUE),
                9
            )
            testRangeFiltering(
                SelectedValue.Lte(ATTR_ID, 1.1F),
                8
            )
            testRangeFiltering(
                SelectedValue.Lte(ATTR_ID, 1.0999999F),
                7
            )
            testRangeFiltering(
                SelectedValue.Lte(ATTR_ID, 0.0F),
                6
            )
            testRangeFiltering(
                SelectedValue.Lte(ATTR_ID, -0.0F),
                6
            )
            testRangeFiltering(
                SelectedValue.Lte(ATTR_ID, -Float.MIN_VALUE),
                4
            )
            testRangeFiltering(
                SelectedValue.Lte(ATTR_ID, -1.1F),
                3
            )
            testRangeFiltering(
                SelectedValue.Lte(ATTR_ID, -Float.MAX_VALUE),
                2
            )
            testRangeFiltering(
                SelectedValue.Lte(ATTR_ID, Float.NEGATIVE_INFINITY),
                1
            )

            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, 0.0F).lte(-1.0F),
                0
            )
            testRangeFiltering(
                SelectedValue.Lte(ATTR_ID, 0.0F).gte(0.0F),
                2
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, 0.0F).lte(1.1F),
                4
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, -1.1F).lte(-0.0F),
                4
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, Float.MIN_VALUE).lte(Float.MAX_VALUE),
                3
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, -Float.MAX_VALUE).lte(-Float.MIN_VALUE),
                3
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, -Float.MAX_VALUE).lte(Float.MAX_VALUE),
                8
            )
            testRangeFiltering(
                SelectedValue.Gte(ATTR_ID, -1.1F).lte(1.1F),
                6
            )
        }
    }
}
