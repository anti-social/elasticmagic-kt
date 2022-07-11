package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.AggregationResult
import dev.evo.elasticmagic.aggs.MinAgg
import dev.evo.elasticmagic.aggs.MinAggResult
import dev.evo.elasticmagic.aggs.SingleBucketAggResult
import dev.evo.elasticmagic.aggs.TermBucket
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.BoundField
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.SubFields

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.maps.shouldContainExactly

import kotlin.test.Test

class IdSubFields(field: BoundField<String, String>) : SubFields<String>(field) {
    val int by int(index = false)
}

object BikeDocument : Document() {
    val manufacturer by keyword()
    val manufacturerId by keyword("manufacturer_id", docValues = false).subFields(::IdSubFields)
    val price by float()
}

fun searchResultWithAggs(aggs: Map<String, AggregationResult>): SearchQueryResult<Nothing> {
    return SearchQueryResult(
        rawResult = null,
        took = 10,
        timedOut = false,
        totalHits = null,
        totalHitsRelation = null,
        maxScore = null,
        hits = emptyList(),
        aggs = aggs,
    )
}

class FacetFilterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    @Test
    fun default() = testWithCompiler {
        val filter = FacetFilter(BikeDocument.manufacturer)

        val ctx = filter.prepare("manufacturer", emptyMap())
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:manufacturer" to mapOf(
                    "terms" to mapOf(
                        "field" to "manufacturer"
                    )
                )
            )
        )

        val manufacturers = ctx.processResult(
            searchResultWithAggs(
                mapOf(
                    "qf:manufacturer" to TermsAggResult(
                        listOf(
                            TermBucket("Giant", 23),
                            TermBucket("Cube", 12)
                        ),
                        docCountErrorUpperBound = 0,
                        sumOtherDocCount = 0,
                    )
                ),
            )
        )
        manufacturers.name shouldBe "manufacturer"
        manufacturers.selected.shouldBeFalse()
        manufacturers.values.size shouldBe 2
        manufacturers.values[0].value shouldBe "Giant"
        manufacturers.values[0].count shouldBe 23
        manufacturers.values[0].selected.shouldBeFalse()
        manufacturers.values[1].value shouldBe "Cube"
        manufacturers.values[1].count shouldBe 12
        manufacturers.values[1].selected.shouldBeFalse()
    }

    @Test
    fun selected() = testWithCompiler {
        val filter = FacetFilter(BikeDocument.manufacturer)

        val ctx = filter.prepare("manufacturer", mapOf(
            ("manufacturer" to "") to listOf("Giant")
        ))
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:manufacturer" to mapOf(
                    "terms" to mapOf(
                        "field" to "manufacturer"
                    )
                )
            ),
            "post_filter" to mapOf(
                "term" to mapOf(
                    "manufacturer" to "Giant"
                )
            )
        )

        val manufacturers = ctx.processResult(
            searchResultWithAggs(
                mapOf(
                    "qf:manufacturer" to TermsAggResult(
                        listOf(
                            TermBucket("Giant", 23),
                            TermBucket("Cube", 12)
                        ),
                        docCountErrorUpperBound = 0,
                        sumOtherDocCount = 0,
                    )
                ),
            ),
        )
        manufacturers.name shouldBe "manufacturer"
        manufacturers.selected.shouldBeTrue()
        manufacturers.values.size shouldBe 2
        manufacturers.values[0].value shouldBe "Giant"
        manufacturers.values[0].count shouldBe 23
        manufacturers.values[0].selected.shouldBeTrue()
        manufacturers.values[1].value shouldBe "Cube"
        manufacturers.values[1].count shouldBe 12
        manufacturers.values[1].selected.shouldBeFalse()
    }

    @Test
    fun withOtherFacetFilters() = testWithCompiler {
        val filter = FacetFilter(BikeDocument.manufacturer)

        val ctx = filter.prepare(
            "manufacturer", mapOf(
                ("manufacturer" to "") to listOf("Giant")
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, listOf(BikeDocument.price.lte(1000F)))

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:manufacturer.filter" to mapOf(
                    "filter" to mapOf(
                        "range" to mapOf(
                            "price" to mapOf(
                                "lte" to 1000F
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:manufacturer" to mapOf(
                            "terms" to mapOf(
                                "field" to "manufacturer"
                            )
                        )
                    )
                )
            ),
            "post_filter" to mapOf(
                "term" to mapOf(
                    "manufacturer" to "Giant"
                )
            )
        )
    }

    @Test
    fun intersectMode() = testWithCompiler {
        val filter = FacetFilter(BikeDocument.manufacturer, mode = FacetFilterMode.INTERSECT)

        val ctx = filter.prepare(
            "manufacturer", mapOf(
                ("manufacturer" to "") to listOf("Giant", "Cube"),
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, listOf(BikeDocument.price.lte(1000F)))

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:manufacturer.filter" to mapOf(
                    "filter" to mapOf(
                        "bool" to mapOf(
                            "filter" to listOf(
                                mapOf(
                                    "range" to mapOf(
                                        "price" to mapOf(
                                            "lte" to 1000F
                                        )
                                    )
                                ),
                                mapOf(
                                    "bool" to mapOf(
                                        "filter" to listOf(
                                            mapOf(
                                                "term" to mapOf(
                                                    "manufacturer" to "Giant"
                                                )
                                            ),
                                            mapOf(
                                                "term" to mapOf(
                                                    "manufacturer" to "Cube"
                                                )
                                            ),
                                        )
                                    )
                                ),
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:manufacturer" to mapOf(
                            "terms" to mapOf(
                                "field" to "manufacturer"
                            )
                        )
                    )
                )
            ),
            "post_filter" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "term" to mapOf(
                                "manufacturer" to "Giant"
                            )
                        ),
                        mapOf(
                            "term" to mapOf(
                                "manufacturer" to "Cube"
                            )
                        ),
                    )
                )
            )
        )
    }

    @Test
    fun customAggParams() = testWithCompiler {
        val filter = FacetFilter(
            BikeDocument.manufacturer,
            termsAggFactory = { field ->
                TermsAgg(
                    field,
                    size = 100,
                    minDocCount = 5,
                    aggs = mapOf("min_price" to MinAgg(BikeDocument.price))
                )
            }
        )

        val ctx = filter.prepare(
            "manufacturer", mapOf(
                ("manufacturer" to "") to listOf("Giant")
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, listOf(BikeDocument.price.lte(1000F)))

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:manufacturer.filter" to mapOf(
                    "filter" to mapOf(
                        "range" to mapOf(
                            "price" to mapOf(
                                "lte" to 1000F
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:manufacturer" to mapOf(
                            "terms" to mapOf(
                                "field" to "manufacturer",
                                "size" to 100,
                                "min_doc_count" to 5,
                            ),
                            "aggs" to mapOf(
                                "min_price" to mapOf(
                                    "min" to mapOf(
                                        "field" to "price"
                                    )
                                )
                            )
                        )
                    )
                )
            ),
            "post_filter" to mapOf(
                "term" to mapOf(
                    "manufacturer" to "Giant"
                )
            )
        )

        val manufacturers = ctx.processResult(searchResultWithAggs(
            mapOf(
                "qf:manufacturer.filter" to SingleBucketAggResult(
                    49,
                    aggs = mapOf(
                        "qf:manufacturer" to TermsAggResult(
                            listOf(
                                TermBucket("Giant", 23, aggs = mapOf(
                                    "min_price" to MinAggResult(999.9)
                                )),
                                TermBucket("Cube", 19, aggs = mapOf(
                                    "min_price" to MinAggResult(1299.0)
                                ))
                            ),
                            docCountErrorUpperBound = 0,
                            sumOtherDocCount = 0
                        )
                    )
                )
            )
        ))

        manufacturers.selected.shouldBeTrue()
        manufacturers.values.size shouldBe 2
        val values = manufacturers.toList()
        values[0].value shouldBe "Giant"
        values[0].count shouldBe 23
        values[0].selected.shouldBeTrue()
        values[0].agg<MinAggResult>("min_price").value shouldBe 999.9
        values[1].value shouldBe "Cube"
        values[1].count shouldBe 19
        values[1].selected.shouldBeFalse()
        values[1].agg<MinAggResult>("min_price").value shouldBe 1299.0
    }

    @Test
    fun customAgg() = testWithCompiler {
        val filter = FacetFilter(
            BikeDocument.manufacturerId,
            termsAgg = TermsAgg(BikeDocument.manufacturerId.int)
        )

        val ctx = filter.prepare(
            "manufacturer", mapOf(
                ("manufacturer" to "") to listOf("1", "3")
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, listOf(BikeDocument.price.lte(1000F)))

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:manufacturer.filter" to mapOf(
                    "filter" to mapOf(
                        "range" to mapOf(
                            "price" to mapOf(
                                "lte" to 1000F
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:manufacturer" to mapOf(
                            "terms" to mapOf(
                                "field" to "manufacturer_id.int"
                            )
                        )
                    )
                )
            ),
            "post_filter" to mapOf(
                "terms" to mapOf(
                    "manufacturer_id" to listOf("1", "3")
                )
            )
        )

        val manufacturers = ctx.processResult(searchResultWithAggs(
            mapOf(
                "qf:manufacturer.filter" to SingleBucketAggResult(
                    49,
                    aggs = mapOf(
                        "qf:manufacturer" to TermsAggResult(
                            listOf(
                                TermBucket(1, 23),
                                TermBucket(2, 19),
                            ),
                            docCountErrorUpperBound = 0,
                            sumOtherDocCount = 0
                        )
                    )
                )
            )
        ))

        manufacturers.selected.shouldBeTrue()
        manufacturers.values.size shouldBe 3
        val values = manufacturers.toList()
        values[0].value shouldBe 1
        values[0].count shouldBe 23
        values[0].selected.shouldBeTrue()
        values[1].value shouldBe 2
        values[1].count shouldBe 19
        values[1].selected.shouldBeFalse()
        values[2].value shouldBe 3
        values[2].count shouldBe 0
        values[2].selected.shouldBeTrue()
    }
}
