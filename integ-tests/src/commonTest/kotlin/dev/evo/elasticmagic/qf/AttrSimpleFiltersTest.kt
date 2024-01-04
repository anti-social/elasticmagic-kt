package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.ElasticsearchTestBase
import dev.evo.elasticmagic.SearchQuery
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AttrSimpleFiltersTest : ElasticsearchTestBase() {
    override val indexName = "attr-simple-filter"

    object ItemQueryFilters : QueryFilters() {
        val selectAttrs by AttrSimpleFilter(ItemDoc.selectAttrs, "attr")
        val rangeAttrs by AttrRangeSimpleFilter(ItemDoc.rangeAttrs, "attr")
        val boolAttrs by AttrBoolSimpleFilter(ItemDoc.boolAttrs, "attr")
    }

    @Test
    fun attrSimpleFilterTest() = runTestWithSerdes {
        withFixtures(ItemDoc, FIXTURES) {
            val searchQuery = SearchQuery()
            val result = searchQuery.search(index)
            result.totalHits shouldBe 8

            ItemQueryFilters.apply(
                searchQuery,
                mapOf(listOf("attr", Manufacturer.ATTR_ID.toString(), "any") to listOf("1", "0"))
            ).let {
                val searchResult = searchQuery.search(index)
                searchResult.totalHits shouldBe 4
            }
        }
    }

    @Test
    fun attrBoolSimpleFilterTest() = runTestWithSerdes {
        withFixtures(ItemDoc, FIXTURES) {
            val searchQuery = SearchQuery()
            searchQuery.search(index).totalHits shouldBe 8

            ItemQueryFilters.apply(
                searchQuery,
                mapOf(listOf("attr", ExtensionSlot.ATTR_ID.toString()) to listOf("true"))
            ).let {
                val searchResult = searchQuery.search(index)
                searchResult.totalHits shouldBe 3
            }
        }
    }

    @Test
    fun attrRangeSimpleFilterTest() = runTestWithSerdes {
        withFixtures(ItemDoc, FIXTURES) {
            val searchQuery = SearchQuery()
            searchQuery.search(index).totalHits shouldBe 8

            ItemQueryFilters.apply(
                searchQuery,
                mapOf(listOf("attr", DisplaySize.ATTR_ID.toString(), "gte") to listOf("6.7"))
            ).let {
                val searchResult = searchQuery.search(index)
                searchResult.totalHits shouldBe 3
            }
        }
    }

    @Test
    fun applyAllSimpleFilters() = runTestWithSerdes {
        withFixtures(ItemDoc, FIXTURES) {
            val searchQuery = SearchQuery()
            searchQuery.search(index).totalHits shouldBe 8

            ItemQueryFilters.apply(
                searchQuery,
                mapOf(
                    listOf("attr", Manufacturer.ATTR_ID.toString(), "any") to listOf("1", "0"),
                    listOf("attr", ExtensionSlot.ATTR_ID.toString()) to listOf("false"),
                    listOf("attr", DisplaySize.ATTR_ID.toString(), "gte") to listOf("6.3"),
                )
            ).let {
                val searchResult = searchQuery.search(index)
                searchResult.totalHits shouldBe 2

                val qfResult = it.processResult(searchResult)
                qfResult[ItemQueryFilters.selectAttrs].let { filter ->
                    filter.name shouldBe "selectAttrs"
                    filter.paramName shouldBe "attr"
                }
                qfResult[ItemQueryFilters.boolAttrs].let { filter ->
                    filter.name shouldBe "boolAttrs"
                    filter.paramName shouldBe "attr"
                }
                qfResult[ItemQueryFilters.rangeAttrs].let { filter ->
                    filter.name shouldBe "rangeAttrs"
                    filter.paramName shouldBe "attr"
                }
            }
        }
    }
}
