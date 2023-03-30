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

class AttrFacetFilterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    object ProductDoc : Document() {
        val attrs by long()
    }

    @Test
    fun default() = testWithCompiler {
        val filter = AttrFacetFilter(ProductDoc.attrs)

        val ctx = filter.prepare("attrs", emptyMap())
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.full" to mapOf(
                    "terms" to mapOf(
                        "field" to "attrs",
                        "size" to 10_000,
                    )
                )
            )
        )

        val attrs = ctx.processResult(searchResultWithAggs(
            "qf:attrs.full" to TermsAggResult(
                listOf(
                    TermBucket(0x00000001_0000000EL, 18),
                    TermBucket(0x00000010_00000002L, 7),
                    TermBucket(0x00000001_0000000FL, 1),
                ),
            )
        ))

        attrs.name shouldBe "attrs"
        attrs.facets.size shouldBe 2
        attrs.facets[1].shouldNotBeNull().let { facet ->
            facet.attrId shouldBe 1
            facet.values.size shouldBe 2
            facet.values[0].shouldNotBeNull().let { value ->
                value.value shouldBe 14
                value.count shouldBe 18
            }
            facet.values[1].shouldNotBeNull().let { value ->
                value.value shouldBe 15
                value.count shouldBe 1
            }
        }
        attrs.facets[16].shouldNotBeNull().let { facet ->
            facet.attrId shouldBe 16
            facet.values.size shouldBe 1
            facet.values[0].shouldNotBeNull().let { value ->
                value.value shouldBe 2
                value.count shouldBe 7
            }
        }
    }

    @Test
    fun selectedSingle() = testWithCompiler {
        val filter = AttrFacetFilter(ProductDoc.attrs)

        val ctx = filter.prepare(
            "attrs",
            mapOf(
                listOf("attrs", "1") to listOf("8")
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.1" to mapOf(
                    "scripted_metric" to mapOf(
                        "init_script" to mapOf(
                            "source" to PreparedAttrFacetFilter.SELECTED_ATTR_INIT_SCRIPT
                        ),
                        "map_script" to mapOf(
                            "source" to PreparedAttrFacetFilter.SELECTED_ATTR_MAP_SCRIPT
                        ),
                        "combine_script" to mapOf(
                            "source" to PreparedAttrFacetFilter.SELECTED_ATTR_COMBINE_SCRIPT
                        ),
                        "reduce_script" to mapOf(
                            "source" to PreparedAttrFacetFilter.SELECTED_ATTR_REDUCE_SCRIPT
                        ),
                        "params" to mapOf(
                            "attrId" to 1,
                            "attrsField" to "attrs",
                            "size" to 100,
                        )
                    )
                ),
                "qf:attrs.full.filter" to mapOf(
                    "filter" to mapOf(
                        "term" to mapOf(
                            "attrs" to 0x00000001_00000008L
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.full" to mapOf(
                            "terms" to mapOf(
                                "field" to "attrs",
                                "size" to 10_000,
                            )
                        )
                    )
                )
            ),
            "post_filter" to mapOf(
                "term" to mapOf(
                    "attrs" to 0x00000001_00000008L
                )
            )
        )

        val attrs = ctx.processResult(searchResultWithAggs(
            "qf:attrs.1" to ScriptedMetricAggResult(
                listOf(
                    0x00000067_00000008L,
                    0x0000003a_00000004L,
                )
            ),
            "qf:attrs.full.filter" to FilterAggResult(
                92,
                aggs = mapOf(
                    "qf:attrs.full" to TermsAggResult(
                        listOf(
                            TermBucket(0x00000001_00000008L, 103),
                            TermBucket(0x00000002_00000001L, 78),
                            TermBucket(0x00000003_00000001L, 23),
                        ),
                    )
                )
            )
        ))

        attrs.facets.size shouldBe 3
        attrs.facets[1].shouldNotBeNull().let { facet ->
            facet.attrId shouldBe 1
            facet.values.size shouldBe 2
            facet.values[0].shouldNotBeNull().let { value ->
                value.value shouldBe 8
                value.count shouldBe 103
            }
            facet.values[1].shouldNotBeNull().let { value ->
                value.value shouldBe 4
                value.count shouldBe 58
            }
        }
    }

    @Test
    fun selectedMultipleAttrs() = testWithCompiler {
        val filter = AttrFacetFilter(ProductDoc.attrs)

        val ctx = filter.prepare(
            "attrs",
            mapOf(
                listOf("attrs", "1") to listOf("8"),
                listOf("attrs", "2") to listOf("9", "10")
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.1.filter" to mapOf(
                    "filter" to mapOf(
                        "terms" to mapOf(
                            "attrs" to listOf(0x00000002_00000009L, 0x00000002_0000000AL)
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.1" to mapOf(
                            "scripted_metric" to mapOf(
                                "init_script" to mapOf(
                                    "source" to PreparedAttrFacetFilter.SELECTED_ATTR_INIT_SCRIPT
                                ),
                                "map_script" to mapOf(
                                    "source" to PreparedAttrFacetFilter.SELECTED_ATTR_MAP_SCRIPT
                                ),
                                "combine_script" to mapOf(
                                    "source" to PreparedAttrFacetFilter.SELECTED_ATTR_COMBINE_SCRIPT
                                ),
                                "reduce_script" to mapOf(
                                    "source" to PreparedAttrFacetFilter.SELECTED_ATTR_REDUCE_SCRIPT
                                ),
                                "params" to mapOf(
                                    "attrId" to 1,
                                    "attrsField" to "attrs",
                                    "size" to 100,
                                )
                            )
                        )
                    )
                ),
                "qf:attrs.2.filter" to mapOf(
                    "filter" to mapOf(
                        "term" to mapOf(
                            "attrs" to 0x00000001_00000008L
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.2" to mapOf(
                            "scripted_metric" to mapOf(
                                "init_script" to mapOf(
                                    "source" to PreparedAttrFacetFilter.SELECTED_ATTR_INIT_SCRIPT
                                ),
                                "map_script" to mapOf(
                                    "source" to PreparedAttrFacetFilter.SELECTED_ATTR_MAP_SCRIPT
                                ),
                                "combine_script" to mapOf(
                                    "source" to PreparedAttrFacetFilter.SELECTED_ATTR_COMBINE_SCRIPT
                                ),
                                "reduce_script" to mapOf(
                                    "source" to PreparedAttrFacetFilter.SELECTED_ATTR_REDUCE_SCRIPT
                                ),
                                "params" to mapOf(
                                    "attrId" to 2,
                                    "attrsField" to "attrs",
                                    "size" to 100,
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
                                        "attrs" to 0x00000001_00000008L
                                    )
                                ),
                                mapOf(
                                    "terms" to mapOf(
                                        "attrs" to listOf(0x00000002_00000009L, 0x00000002_0000000AL)
                                    )
                                ),
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.full" to mapOf(
                            "terms" to mapOf(
                                "field" to "attrs",
                                "size" to 10_000,
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
                                "attrs" to 0x00000001_00000008L
                            )
                        ),
                        mapOf(
                            "terms" to mapOf(
                                "attrs" to listOf(0x00000002_00000009L, 0x00000002_0000000AL)
                            )
                        ),
                    )
                )
            )
        )
    }
     
    @Test
    fun selectedWithIntersectionMode() = testWithCompiler {
        val filter = AttrFacetFilter(ProductDoc.attrs)

        val ctx = filter.prepare(
            "attrs",
            mapOf(
                listOf("attrs", "1", "all") to listOf("8", "9")
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.full.filter" to mapOf(
                    "filter" to mapOf(
                        "bool" to mapOf(
                            "filter" to listOf(
                                mapOf(
                                    "term" to mapOf(
                                        "attrs" to 0x00000001_00000008L
                                    )
                                ),
                                mapOf(
                                    "term" to mapOf(
                                        "attrs" to 0x00000001_00000009L
                                    )
                                ),
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.full" to mapOf(
                            "terms" to mapOf(
                                "field" to "attrs",
                                "size" to 10_000,
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
                                "attrs" to 0x00000001_00000008L
                            )
                        ),
                        mapOf(
                            "term" to mapOf(
                                "attrs" to 0x00000001_00000009L
                            )
                        ),
                    )
                )
            )
        )
    }

    @Test
    fun selectedMultipleWithDifferentModes() = testWithCompiler {
        val filter = AttrFacetFilter(ProductDoc.attrs)

        val ctx = filter.prepare(
            "attrs",
            mapOf(
                listOf("attrs", "1", "all") to listOf("8", "9"),
                listOf("attrs", "2", "any") to listOf("6", "7"),
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.2.filter" to mapOf(
                    "filter" to mapOf(
                        "bool" to mapOf(
                            "filter" to listOf(
                                mapOf(
                                    "term" to mapOf(
                                        "attrs" to 0x00000001_00000008L
                                    )
                                ),
                                mapOf(
                                    "term" to mapOf(
                                        "attrs" to 0x00000001_00000009L
                                    )
                                ),
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.2" to mapOf(
                            "scripted_metric" to mapOf(
                                "init_script" to mapOf(
                                    "source" to PreparedAttrFacetFilter.SELECTED_ATTR_INIT_SCRIPT
                                ),
                                "map_script" to mapOf(
                                    "source" to PreparedAttrFacetFilter.SELECTED_ATTR_MAP_SCRIPT
                                ),
                                "combine_script" to mapOf(
                                    "source" to PreparedAttrFacetFilter.SELECTED_ATTR_COMBINE_SCRIPT
                                ),
                                "reduce_script" to mapOf(
                                    "source" to PreparedAttrFacetFilter.SELECTED_ATTR_REDUCE_SCRIPT
                                ),
                                "params" to mapOf(
                                    "attrId" to 2,
                                    "attrsField" to "attrs",
                                    "size" to 100,
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
                                    "bool" to mapOf(
                                        "filter" to listOf(
                                            mapOf(
                                                "term" to mapOf(
                                                    "attrs" to 0x00000001_00000008L
                                                )
                                            ),
                                            mapOf(
                                                "term" to mapOf(
                                                    "attrs" to 0x00000001_00000009L
                                                )
                                            ),
                                        )
                                    )
                                ),
                                mapOf(
                                    "terms" to mapOf(
                                        "attrs" to listOf(0x00000002_00000006L, 0x00000002_00000007L)
                                    )
                                ),
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.full" to mapOf(
                            "terms" to mapOf(
                                "field" to "attrs",
                                "size" to 10_000,
                            )
                        )
                    )
                )
            ),
            "post_filter" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "bool" to mapOf(
                                "filter" to listOf(
                                    mapOf(
                                        "term" to mapOf(
                                            "attrs" to 0x00000001_00000008L
                                        )
                                    ),
                                    mapOf(
                                        "term" to mapOf(
                                            "attrs" to 0x00000001_00000009L
                                        )
                                    ),
                                )
                            )
                        ),
                        mapOf(
                            "terms" to mapOf(
                                "attrs" to listOf(0x00000002_00000006L, 0x00000002_00000007L)
                            )
                        ),
                    )
                )
            )
        )
    }
}
