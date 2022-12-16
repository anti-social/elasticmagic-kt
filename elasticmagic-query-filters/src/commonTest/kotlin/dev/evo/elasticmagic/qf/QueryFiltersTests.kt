package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchHit
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.AggregationResult
import dev.evo.elasticmagic.aggs.TermBucket
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.aggs.ValueCountAggResult
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.DynDocSource

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

import kotlin.test.Test

fun searchResult(
    totalHits: Long,
    hits: List<SearchHit<DynDocSource>>,
    aggs: Map<String, AggregationResult>
): SearchQueryResult<DynDocSource> {
    return SearchQueryResult(
        rawResult = null,
        took = 10,
        timedOut = false,
        totalHits = totalHits,
        totalHitsRelation = null,
        maxScore = null,
        hits = hits,
        aggs = aggs,
    )
}

class QueryFiltersTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    object BikeQueryFilters : QueryFilters() {
        val price by FacetRangeFilter(BikeDocument.price)
        val manufacturer by FacetFilter(BikeDocument.manufacturer)
        val sort by SortFilter(
            "score" to emptyList(),
            "price" to listOf(BikeDocument.price, BikeDocument.meta.id),
            "-price" to listOf(BikeDocument.price.desc(), BikeDocument.meta.id.desc())
        )
        val page by PageFilter()
    }

    @Test
    fun emptyParams() = testWithCompiler {
        BikeQueryFilters.toList().size shouldBe 4

        val sq = SearchQuery()
        val appliedFilters = BikeQueryFilters.apply(sq, emptyMap())
        val expectedQuery = mutableMapOf(
            "from" to 0,
            "size" to 10,
            "aggs" to mapOf(
                "qf:manufacturer" to mapOf(
                    "terms" to mapOf(
                        "field" to "manufacturer"
                    )
                ),
                "qf:price.count" to mapOf(
                    "value_count" to mapOf(
                        "field" to "price"
                    )
                )
            )
        )
        if (features.supportsTrackingOfTotalHits) {
            expectedQuery["track_total_hits"] = true
        }

        compile(sq).body shouldContainExactly expectedQuery

        val filters = appliedFilters.processResult(
            searchResult(
                453,
                emptyList(),
                mapOf(
                    "qf:price.count" to ValueCountAggResult(439L),
                    "qf:manufacturer" to TermsAggResult(
                        listOf(
                            TermBucket("Giant", 84),
                            TermBucket("Bianchi", 37),
                        ),
                        docCountErrorUpperBound = 0,
                        sumOtherDocCount = 0,
                    )
                )
            )
        )

        val manufacturerFacet = filters[BikeQueryFilters.manufacturer]
        manufacturerFacet.name shouldBe "manufacturer"
        manufacturerFacet.values.size shouldBe 2
        manufacturerFacet.selected.shouldBeFalse()
        val priceFacet = filters[BikeQueryFilters.price]
        priceFacet.name shouldBe "price"
        priceFacet.count shouldBe 439L
        val sort = filters[BikeQueryFilters.sort]
        sort.name shouldBe "sort"
        sort.values.size shouldBe 3
        val page = filters[BikeQueryFilters.page]
        page.name shouldBe "page"
        page.totalPages shouldBe 46

        filters.toList().size shouldBe 4
    }

    @Test
    fun withParams() = testWithCompiler {
        val sq = SearchQuery()
        val appliedFilters = BikeQueryFilters.apply(
            sq,
            mapOf(
                listOf("manufacturer") to listOf("BMC", "Cube"),
                listOf("price", "lte") to listOf("2500"),
                listOf("page") to listOf("3"),
                listOf("page", "size") to listOf("50"),
                listOf("sort") to listOf("price")
            )
        )
        val filters = appliedFilters.processResult(
            searchResult(
                453,
                emptyList(),
                mapOf(
                    "qf:price.count" to ValueCountAggResult(439L),
                    "qf:manufacturer" to TermsAggResult(
                        listOf(
                            TermBucket("Giant", 84),
                            TermBucket("Bianchi", 37),
                        ),
                        docCountErrorUpperBound = 0,
                        sumOtherDocCount = 0,
                    )
                )
            )
        )
        val manufacturerFacet = filters[BikeQueryFilters.manufacturer]
        manufacturerFacet.name shouldBe "manufacturer"
    }
}
