package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.aggs.FilterAggResult
import dev.evo.elasticmagic.aggs.ValueCountAggResult
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.Document
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class FacetQueryFilterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    private object ProductDoc : Document() {
        val isAvailable by boolean()
        val sellingType by keyword()
    }

    private object CarDoc : Document() {
        val state by boolean()
        val price by int()
        val year by int()
    }

    private object CarQueryFilter : QueryFilters() {
        val isNew by FacetExpressionsFilter(
            "new",
            listOf(ExpressionValue("true", CarDoc.state.eq(true)))
        )
        val price by FacetExpressionsFilter(
            "price",
            listOf(
                ExpressionValue("*-10000", CarDoc.price.lte(10000)),
                ExpressionValue("10000-20000", CarDoc.price.range(gt = 10000, lte = 20000)),
                ExpressionValue("20000-30000", CarDoc.price.range(gt = 20000, lte = 30000)),
                ExpressionValue("30000-*", CarDoc.price.range(30000))
            ),

            )
    }

    private object ItemQueryFilters : QueryFilters() {
        val sellingType by FacetExpressionsFilter(
            "sellingType",
            listOf(
                ExpressionValue("wholesale", ProductDoc.sellingType.oneOf(listOf("4", "5", "6"))),
                ExpressionValue("retail", ProductDoc.sellingType.oneOf(listOf("1", "2", "3"))),
            ),
            mode = FilterMode.INTERSECT,
        )

        val available by FacetExpressionsFilter(
            "available",
            listOf(
                ExpressionValue("true", ProductDoc.isAvailable.eq(true)),
            ),
        )
    }

    @Test
    fun simpleTest() = testWithCompiler {
        var sq = SearchQuery()
        ItemQueryFilters.apply(sq, emptyMap())
        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf.sellingType:retail" to mapOf(
                    "filter" to mapOf(
                        "terms" to mapOf(
                            "sellingType" to listOf("1", "2", "3")
                        )
                    )
                ),
                "qf.sellingType:wholesale" to mapOf(
                    "filter" to mapOf(
                        "terms" to mapOf(
                            "sellingType" to listOf("4", "5", "6")
                        )
                    )
                ),

                "qf.available:true" to mapOf(
                    "filter" to mapOf(
                        "term" to mapOf(
                            "isAvailable" to true
                        )
                    )
                ),
            )
        )

        sq = SearchQuery()
        ItemQueryFilters.apply(sq, mapOf(listOf("sellingType") to listOf("retail")))
        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf.sellingType.filter" to mapOf(
                    "filter" to mapOf(
                        "terms" to mapOf(
                            "sellingType" to listOf("1", "2", "3")
                        )
                    ),
                    "aggs" to mapOf(
                        "qf.sellingType:retail" to mapOf(
                            "filter" to mapOf(
                                "terms" to mapOf(
                                    "sellingType" to listOf("1", "2", "3")
                                )
                            )
                        ),
                        "qf.sellingType:wholesale" to mapOf(
                            "filter" to mapOf(
                                "terms" to mapOf(
                                    "sellingType" to listOf("4", "5", "6")
                                )
                            )
                        )
                    )
                ),

                "qf.available.filter" to mapOf(
                    "filter" to mapOf(
                        "terms" to mapOf(
                            "sellingType" to listOf("1", "2", "3")
                        )
                    ),
                    "aggs" to mapOf(
                        "qf.available:true" to mapOf(
                            "filter" to mapOf(
                                "term" to mapOf(
                                    "isAvailable" to true
                                )
                            )
                        ),
                    )
                )
            ),
            "post_filter" to mapOf(
                "terms" to mapOf(
                    "sellingType" to listOf("1", "2", "3")
                )
            )
        )
    }

    @Test
    fun testResult() = testWithCompiler {
        var sq = SearchQuery()

        val f = CarQueryFilter.apply(sq, mapOf(listOf("new") to listOf("true")))

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf.isNew:true" to mapOf(
                    "filter" to mapOf(
                        "term" to mapOf(
                            "state" to true
                        )
                    )
                ),

                "qf.price.filter" to mapOf(
                    "filter" to mapOf(
                        "term" to mapOf(
                            "state" to true
                        )
                    ),
                    "aggs" to mapOf(
                        "qf.price:*-10000" to mapOf(
                            "filter" to mapOf(
                                "range" to mapOf(
                                    "price" to mapOf(
                                        "lte" to 10000
                                    )
                                )
                            )
                        ),
                        "qf.price:10000-20000" to mapOf(
                            "filter" to mapOf(
                                "range" to mapOf(
                                    "price" to mapOf(
                                        "gt" to 10000,
                                        "lte" to 20000,
                                    )
                                )
                            )
                        ),
                        "qf.price:20000-30000" to mapOf(
                            "filter" to mapOf(
                                "range" to mapOf(
                                    "price" to mapOf(
                                        "gt" to 20000,
                                        "lte" to 30000,
                                    )
                                )
                            )
                        ),
                        "qf.price:30000-*" to mapOf(
                            "filter" to mapOf(
                                "range" to mapOf(
                                    "price" to mapOf(
                                        "gt" to 30000
                                    )
                                )
                            )
                        ),
                    )
                )
            ),
            "post_filter" to mapOf(
                "term" to mapOf(
                    "state" to true
                )
            )
        )
        val result = f.processResult(
            searchResultWithAggs(
                "qf.isNew:true" to FilterAggResult(82, mapOf()),
                "qf.price.filter" to FilterAggResult(
                    82,
                    mapOf(
                        "qf.price:*-10000" to ValueCountAggResult(11),
                        "qf.price:10000-20000" to ValueCountAggResult(16),
                        "qf.price:20000-30000" to ValueCountAggResult(23),
                        "qf.price:30000-*" to ValueCountAggResult(32),
                    )
                )
            )
        )
        val isNew = result[CarQueryFilter.isNew]
        val price = result[CarQueryFilter.price]
        isNew shouldNotBe null
        price shouldNotBe null
        isNew.name shouldBe "isNew"
        isNew.results.size shouldBe 1
        isNew.results[0].name shouldBe "true"
        isNew.results[0].selected shouldBe true
        isNew.results[0].docCount shouldBe 82

        price.name shouldBe "price"
        price.results.size shouldBe 4
        price.results[0].name shouldBe "*-10000"
        price.results[0].selected shouldBe false
        price.results[0].docCount shouldBe 11
        price.results[1].name shouldBe "10000-20000"
        price.results[1].selected shouldBe false
        price.results[1].docCount shouldBe 16
        price.results[2].name shouldBe "20000-30000"
        price.results[2].selected shouldBe false
        price.results[2].docCount shouldBe 23
        price.results[3].name shouldBe "30000-*"
        price.results[3].selected shouldBe false
        price.results[3].docCount shouldBe 32


        sq = SearchQuery(CarDoc.year.eq(2014))
        val priceFilter = CarQueryFilter.apply(sq, mapOf(listOf("price") to listOf("*-10000", "10000-20000", "null")))
        compile(sq).body shouldContainExactly mapOf(
            "query" to mapOf(
                "term" to mapOf(
                    "year" to 2014
                ),
            ),
            "aggs" to mapOf(
                "qf.isNew.filter" to mapOf(
                    "filter" to mapOf(
                        "bool" to mapOf(
                            "should" to listOf(
                                mapOf(
                                    "range" to mapOf(
                                        "price" to mapOf(
                                            "lte" to 10000
                                        )
                                    )
                                ),
                                mapOf(
                                    "range" to mapOf(
                                        "price" to mapOf(
                                            "gt" to 10000,
                                            "lte" to 20000,
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf.isNew:true" to mapOf(
                            "filter" to mapOf(
                                "term" to mapOf(
                                    "state" to true
                                )
                            )
                        ),
                    )
                ),
                "qf.price:*-10000" to mapOf(
                    "filter" to mapOf(
                        "range" to mapOf(
                            "price" to mapOf(
                                "lte" to 10000
                            )
                        )
                    )
                ),
                "qf.price:10000-20000" to mapOf(
                    "filter" to mapOf(
                        "range" to mapOf(
                            "price" to mapOf(
                                "gt" to 10000,
                                "lte" to 20000,
                            )
                        )
                    )
                ),
                "qf.price:20000-30000" to mapOf(
                    "filter" to mapOf(
                        "range" to mapOf(
                            "price" to mapOf(
                                "gt" to 20000,
                                "lte" to 30000,
                            )
                        )
                    )
                ),
                "qf.price:30000-*" to mapOf(
                    "filter" to mapOf(
                        "range" to mapOf(
                            "price" to mapOf(
                                "gt" to 30000
                            )
                        )
                    )
                ),
            ),
            "post_filter" to mapOf(
                "bool" to mapOf(
                    "should" to listOf(
                        mapOf(
                            "range" to mapOf(
                                "price" to mapOf(
                                    "lte" to 10000
                                )
                            )
                        ),
                        mapOf(
                            "range" to mapOf(
                                "price" to mapOf(
                                    "gt" to 10000,
                                    "lte" to 20000,
                                )
                            )
                        )
                    )
                )
            )
        )

        val result1 = priceFilter.processResult(
            searchResultWithAggs(
                "qf.isNew:true" to FilterAggResult(32, mapOf()),
                "qf.price.filter" to FilterAggResult(
                    82,
                    mapOf(
                        "qf.price:*-10000" to ValueCountAggResult(7),
                        "qf.price:10000-20000" to ValueCountAggResult(11),
                        "qf.price:20000-30000" to ValueCountAggResult(6),
                        "qf.price:30000-*" to ValueCountAggResult(10),
                    )
                )
            )
        )
        val priceResult = result1[CarQueryFilter.price]
        priceResult shouldNotBe null
        priceResult.name shouldBe "price"
        priceResult.results.size shouldBe 4
        priceResult.results[0].name shouldBe "*-10000"
        priceResult.results[0].selected shouldBe true
        priceResult.results[0].docCount shouldBe 7
        priceResult.results[1].name shouldBe "10000-20000"
        priceResult.results[1].selected shouldBe true
        priceResult.results[1].docCount shouldBe 11
        priceResult.results[2].name shouldBe "20000-30000"
        priceResult.results[2].selected shouldBe false
        priceResult.results[2].docCount shouldBe 6
        priceResult.results[3].name shouldBe "30000-*"
        priceResult.results[3].selected shouldBe false
        priceResult.results[3].docCount shouldBe 10

    }
}
