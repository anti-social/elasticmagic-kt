package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.aggs.HistogramAgg
import dev.evo.elasticmagic.aggs.HistogramAggResult
import dev.evo.elasticmagic.aggs.HistogramBucket
import dev.evo.elasticmagic.aggs.MaxAgg
import dev.evo.elasticmagic.aggs.MaxAggResult
import dev.evo.elasticmagic.aggs.MinAgg
import dev.evo.elasticmagic.aggs.MinAggResult
import dev.evo.elasticmagic.aggs.ValueCountAggResult
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

import kotlin.test.Test

class FacetRangeFilterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    @Test
    fun default() = testWithCompiler {
        val filter = FacetRangeFilter(BikeDocument.price)
        val ctx = filter.prepare("price", emptyMap())
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:price.count" to mapOf(
                    "value_count" to mapOf(
                        "field" to "price"
                    )
                )
            )
        )

        val price = ctx.processResult(
            searchResultWithAggs(
                "qf:price.count" to ValueCountAggResult(97L)
            )
        )
        price.name shouldBe "price"
        price.count shouldBe 97L
        price.from.shouldBeNull()
        price.to.shouldBeNull()
        price.aggs.shouldBeEmpty()
    }

    @Test
    fun filtered() = testWithCompiler {
        val filter = FacetRangeFilter(BikeDocument.price)
        val ctx = filter.prepare(
            "price",
            mapOf(
                listOf("price", "gte") to listOf("9.99"),
                listOf("price", "lte") to listOf("20"),
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:price.count" to mapOf(
                    "value_count" to mapOf(
                        "field" to "price"
                    )
                )
            ),
            "post_filter" to mapOf(
                "range" to mapOf(
                    "price" to mapOf("gte" to 9.99F, "lte" to 20.0F)
                )
            )
        )

        val price = ctx.processResult(
            searchResultWithAggs(
                "qf:price.count" to ValueCountAggResult(97L)
            )
        )
        price.name shouldBe "price"
        price.count shouldBe 97L
        price.from shouldBe 9.99F
        price.to shouldBe 20F
        price.aggs.shouldBeEmpty()
    }

    @Test
    fun ignoreInvalidFilterValue() = testWithCompiler {
        val filter = FacetRangeFilter(BikeDocument.price)
        val ctx = filter.prepare(
            "price",
            mapOf(
                listOf("price", "gte") to listOf("not-a-number"),
                listOf("price", "lte") to listOf("20"),
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:price.count" to mapOf(
                    "value_count" to mapOf(
                        "field" to "price"
                    )
                )
            ),
            "post_filter" to mapOf(
                "range" to mapOf(
                    "price" to mapOf("lte" to 20.0F)
                )
            )
        )
    }

    @Test
    fun otherFacetFilters() = testWithCompiler {
        val filter = FacetRangeFilter(BikeDocument.price)
        val ctx = filter.prepare(
            "price",
            mapOf(
                listOf("price", "gte") to listOf("9.99"),
                listOf("price", "lte") to listOf("20"),
            )
        )

        SearchQuery().also { sq ->
            ctx.apply(sq, listOf(BikeDocument.manufacturer eq "Cannondale"))

            compile(sq).body shouldContainExactly mapOf(
                "aggs" to mapOf(
                    "qf:price.filter" to mapOf(
                        "filter" to mapOf(
                            "term" to mapOf(
                                "manufacturer" to "Cannondale"
                            )
                        ),
                        "aggs" to mapOf(
                            "qf:price.count" to mapOf(
                                "value_count" to mapOf(
                                    "field" to "price"
                                )
                            )
                        )
                    )
                ),
                "post_filter" to mapOf(
                    "range" to mapOf(
                        "price" to mapOf("gte" to 9.99F, "lte" to 20.0F)
                    )
                )
            )
        }

        SearchQuery().also { sq ->
            ctx.apply(
                sq,
                listOf(
                    BikeDocument.manufacturer eq "Cannondale",
                    BikeDocument.manufacturer eq "Focus"
                )
            )

            compile(sq).body shouldContainExactly mapOf(
                "aggs" to mapOf(
                    "qf:price.filter" to mapOf(
                        "filter" to mapOf(
                            "bool" to mapOf(
                                "filter" to listOf(
                                    mapOf(
                                        "term" to mapOf(
                                            "manufacturer" to "Cannondale"
                                        )
                                    ),
                                    mapOf(
                                        "term" to mapOf(
                                            "manufacturer" to "Focus"
                                        )
                                    ),
                                )
                            )
                        ),
                        "aggs" to mapOf(
                            "qf:price.count" to mapOf(
                                "value_count" to mapOf(
                                    "field" to "price"
                                )
                            )
                        )
                    )
                ),
                "post_filter" to mapOf(
                    "range" to mapOf(
                        "price" to mapOf("gte" to 9.99F, "lte" to 20.0F)
                    )
                )
            )
        }
    }

    @Test
    fun moreAggs() = testWithCompiler {
        val filter = FacetRangeFilter(
            BikeDocument.price,
            aggs = mapOf(
                "min" to MinAgg(BikeDocument.price),
                "max" to MaxAgg(BikeDocument.price),
                "histogram" to HistogramAgg(BikeDocument.price, interval = 10F)
            )
        )
        val ctx = filter.prepare("price", emptyMap())

        val sq = SearchQuery()
        ctx.apply(sq, emptyList())
        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:price.count" to mapOf(
                    "value_count" to mapOf(
                        "field" to "price"
                    ),
                ),
                "qf:price.min" to mapOf(
                    "min" to mapOf(
                        "field" to "price"
                    )
                ),
                "qf:price.max" to mapOf(
                    "max" to mapOf(
                        "field" to "price"
                    )
                ),
                "qf:price.histogram" to mapOf(
                    "histogram" to mapOf(
                        "field" to "price",
                        "interval" to 10F
                    )
                ),
            )
        )

        val priceFacet = ctx.processResult(
            searchResultWithAggs(
                "qf:price.count" to ValueCountAggResult(97L),
                "qf:price.min" to MinAggResult(0.01),
                "qf:price.max" to MaxAggResult(100_000.0),
                "qf:price.histogram" to HistogramAggResult(listOf(
                    HistogramBucket(0.0, 1),
                    HistogramBucket(10.0, 101),
                    HistogramBucket(99_990.0, 3),
                ))
            )
        )
        priceFacet.name shouldBe "price"
        priceFacet.count shouldBe 97L
        priceFacet.from.shouldBeNull()
        priceFacet.to.shouldBeNull()
        priceFacet.aggs.size shouldBe 3
        priceFacet.agg<MinAggResult>("min").value shouldBe 0.01
        priceFacet.agg<MaxAggResult>("max").value shouldBe 100_000.0
        val priceHistogram = priceFacet.agg<HistogramAggResult>("histogram")
        priceHistogram.buckets.size shouldBe 3
    }
}
