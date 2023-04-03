package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.aggs.FilterAggResult
import dev.evo.elasticmagic.aggs.ScriptedMetricAggResult
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.aggs.TermBucket
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.Document

import io.kotest.matchers.shouldBe
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull

import kotlin.test.Test

class AttrBoolFacetFilterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    object ProductDoc : Document() {
        val status by int()
        val attrs by long()
    }

    @Test
    fun default() = testWithCompiler {
        val filter = AttrBoolFacetFilter(ProductDoc.attrs)

        val ctx = filter.prepare("attrs", emptyMap())
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.full" to mapOf(
                    "terms" to mapOf(
                        "field" to "attrs",
                        "size" to 100,
                    )
                )
            )
        )

        val attrs = ctx.processResult(searchResultWithAggs(
            "qf:attrs.full" to TermsAggResult(
                listOf(
                    TermBucket(0b0000_0011L, 18),
                    TermBucket(0b0010_0001L, 7),
                    TermBucket(0b0000_0010L, 1),
                ),
            )
        ))

        attrs.name shouldBe "attrs"
        attrs.facets.size shouldBe 2
        attrs.facets[1].shouldNotBeNull().let { facet ->
            facet.attrId shouldBe 1
            facet.values.size shouldBe 2
            facet.values[0].shouldNotBeNull().let { value ->
                value.value shouldBe true
                value.count shouldBe 18
            }
            facet.values[1].shouldNotBeNull().let { value ->
                value.value shouldBe false
                value.count shouldBe 1
            }
        }
        attrs.facets[16].shouldNotBeNull().let { facet ->
            facet.attrId shouldBe 16
            facet.values.size shouldBe 1
            facet.values[0].shouldNotBeNull().let { value ->
                value.value shouldBe true
                value.count shouldBe 7
            }
        }
    }

    @Test
    fun ignoreParams() = testWithCompiler {
        val filter = AttrBoolFacetFilter(ProductDoc.attrs)

        val ctx = filter.prepare(
            "attrs",
            mapOf(
                listOf<String>() to listOf("true"),
                listOf("other_attrs") to listOf("true"),
                listOf("attrs") to listOf("true"),
                listOf("attrs", "aaa") to listOf("true"),
                listOf("attrs", "1") to listOf(),
                listOf("attrs", "1") to listOf("1"),
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.full" to mapOf(
                    "terms" to mapOf(
                        "field" to "attrs",
                        "size" to 100,
                    )
                )
            )
        )
    }

    @Test
    fun otherFacetFilters() = testWithCompiler {
        val filter = AttrBoolFacetFilter(ProductDoc.attrs)

        val ctx = filter.prepare("attrs", emptyMap())
        val sq = SearchQuery()
        ctx.apply(sq, listOf(ProductDoc.status eq 1))

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.filter" to mapOf(
                    "filter" to mapOf(
                        "term" to mapOf(
                            "status" to 1
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.full" to mapOf(
                            "terms" to mapOf(
                                "field" to "attrs",
                                "size" to 100,
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun selectSingle() = testWithCompiler {
        val filter = AttrBoolFacetFilter(ProductDoc.attrs)

        val ctx = filter.prepare(
            "attrs",
            mapOf(
                listOf("attrs", "1") to listOf("true"),
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.1" to mapOf(
                    "scripted_metric" to mapOf(
                        "init_script" to mapOf(
                            "source" to PreparedAttrBoolFacetFilter.SELECTED_ATTR_INIT_SCRIPT
                        ),
                        "map_script" to mapOf(
                            "source" to PreparedAttrBoolFacetFilter.SELECTED_ATTR_MAP_SCRIPT
                        ),
                        "combine_script" to mapOf(
                            "source" to PreparedAttrBoolFacetFilter.SELECTED_ATTR_COMBINE_SCRIPT
                        ),
                        "reduce_script" to mapOf(
                            "source" to PreparedAttrBoolFacetFilter.SELECTED_ATTR_REDUCE_SCRIPT
                        ),
                        "params" to mapOf(
                            "attrId" to 1,
                            "attrsField" to "attrs",
                        )
                    )
                ),
                "qf:attrs.full.filter" to mapOf(
                    "filter" to mapOf(
                        "term" to mapOf(
                            "attrs" to 0b0000_0011L
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.full" to mapOf(
                            "terms" to mapOf(
                                "field" to "attrs",
                                "size" to 100,
                            )
                        )
                    )
                )
            ),
            "post_filter" to mapOf(
                "term" to mapOf(
                    "attrs" to 0b0000_0011L
                )
            )
        )

        val attrs = ctx.processResult(searchResultWithAggs(
            "qf:attrs.1" to ScriptedMetricAggResult(
                listOf(67, 0)
            ),
            "qf:attrs.full.filter" to FilterAggResult(
                92,
                aggs = mapOf(
                    "qf:attrs.full" to TermsAggResult(
                        listOf(
                            TermBucket(0b0000_0011L, 103),
                            TermBucket(0b0000_1101L, 78),
                            TermBucket(0b0000_1100L, 23),
                        ),
                    )
                )
            )
        ))

        attrs.facets.size shouldBe 2
        attrs.facets[1].shouldNotBeNull().let { facet ->
            facet.attrId shouldBe 1
            facet.values.size shouldBe 2
            facet.values[0].shouldNotBeNull().let { value ->
                value.value shouldBe false
                value.count shouldBe 67
            }
            facet.values[1].shouldNotBeNull().let { value ->
                value.value shouldBe true
                value.count shouldBe 0
            }
        }
        attrs.facets[6].shouldNotBeNull().let { facet ->
            facet.attrId shouldBe 6
            facet.values.size shouldBe 2
            facet.values[0].shouldNotBeNull().let { value ->
                value.value shouldBe true
                value.count shouldBe 78
            }
            facet.values[1].shouldNotBeNull().let { value ->
                value.value shouldBe false
                value.count shouldBe 23
            }
        }
    }

    @Test
    fun selectMultiple() = testWithCompiler {
        val filter = AttrBoolFacetFilter(ProductDoc.attrs)

        val ctx = filter.prepare(
            "attrs",
            mapOf(
                listOf("attrs", "1") to listOf("true"),
                listOf("attrs", "2") to listOf("false", "true")
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.1.filter" to mapOf(
                    "filter" to mapOf(
                        "terms" to mapOf(
                            "attrs" to listOf(0b0000_0100L, 0b0000_0101L)
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.1" to mapOf(
                            "scripted_metric" to mapOf(
                                "init_script" to mapOf(
                                    "source" to PreparedAttrBoolFacetFilter.SELECTED_ATTR_INIT_SCRIPT
                                ),
                                "map_script" to mapOf(
                                    "source" to PreparedAttrBoolFacetFilter.SELECTED_ATTR_MAP_SCRIPT
                                ),
                                "combine_script" to mapOf(
                                    "source" to PreparedAttrBoolFacetFilter.SELECTED_ATTR_COMBINE_SCRIPT
                                ),
                                "reduce_script" to mapOf(
                                    "source" to PreparedAttrBoolFacetFilter.SELECTED_ATTR_REDUCE_SCRIPT
                                ),
                                "params" to mapOf(
                                    "attrId" to 1,
                                    "attrsField" to "attrs",
                                )
                            )
                        )
                    )
                ),
                "qf:attrs.2.filter" to mapOf(
                    "filter" to mapOf(
                        "term" to mapOf(
                            "attrs" to 0b0000_0011L
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.2" to mapOf(
                            "scripted_metric" to mapOf(
                                "init_script" to mapOf(
                                    "source" to PreparedAttrBoolFacetFilter.SELECTED_ATTR_INIT_SCRIPT
                                ),
                                "map_script" to mapOf(
                                    "source" to PreparedAttrBoolFacetFilter.SELECTED_ATTR_MAP_SCRIPT
                                ),
                                "combine_script" to mapOf(
                                    "source" to PreparedAttrBoolFacetFilter.SELECTED_ATTR_COMBINE_SCRIPT
                                ),
                                "reduce_script" to mapOf(
                                    "source" to PreparedAttrBoolFacetFilter.SELECTED_ATTR_REDUCE_SCRIPT
                                ),
                                "params" to mapOf(
                                    "attrId" to 2,
                                    "attrsField" to "attrs",
                                )
                            )
                        )
                    )
                ),
                "qf:attrs.full.filter" to mapOf(
                    "filter" to mapOf(
                        "bool" to mapOf(
                            "filter" to listOf(
                                mapOf(
                                    "term" to mapOf(
                                        "attrs" to 0b0000_0011L
                                    )
                                ),
                                mapOf(
                                    "terms" to mapOf(
                                        "attrs" to listOf(0b0000_0100L, 0b0000_0101L)
                                    )
                                ),
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.full" to mapOf(
                            "terms" to mapOf(
                                "field" to "attrs",
                                "size" to 100,
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
                                "attrs" to 0b0000_0011L
                            )
                        ),
                        mapOf(
                            "terms" to mapOf(
                                "attrs" to listOf(0b0000_0100L, 0b0000_0101L)
                            )
                        ),
                    )
                )
            )
        )
    }
}
