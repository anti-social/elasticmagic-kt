package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.aggs.ScriptedMetricAggResult
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.Document

import io.kotest.matchers.shouldBe
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull

import kotlin.test.Test

class AttrRangeFacetFilterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    object ProductDoc : Document() {
        val status by int()
        val rangeAttrs by long("range_attrs")
    }

    @Test
    fun default() = testWithCompiler {
        val filter = AttrRangeFacetFilter(ProductDoc.rangeAttrs)

        val ctx = filter.prepare("attrs", emptyMap())
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.full" to mapOf(
                    "scripted_metric" to mapOf(
                        "init_script" to mapOf(
                            "source" to PreparedAttrRangeFacetFilter.ATTRS_INIT_SCRIPT
                        ),
                        "map_script" to mapOf(
                            "source" to PreparedAttrRangeFacetFilter.ATTRS_MAP_SCRIPT
                        ),
                        "combine_script" to mapOf(
                            "source" to PreparedAttrRangeFacetFilter.ATTRS_COMBINE_SCRIPT
                        ),
                        "reduce_script" to mapOf(
                            "source" to PreparedAttrRangeFacetFilter.ATTRS_REDUCE_SCRIPT
                        ),
                        "params" to mapOf(
                            "attrsField" to "range_attrs",
                            "size" to 100,
                        )
                    )
                )
            )
        )

        val rangeAttrs = ctx.processResult(searchResultWithAggs(
            "qf:attrs.full" to ScriptedMetricAggResult(
                listOf(
                    AttrRangeFacet(1, 34, 0.2F, 1.5F),
                    AttrRangeFacet(2, 13, -1.0F, 1.0F),
                )
            ),
        ))

        rangeAttrs.facets.size shouldBe 2
        rangeAttrs.facets[1].shouldNotBeNull().let { facet ->
            facet.attrId shouldBe 1
            facet.count shouldBe 34
            facet.min shouldBe 0.2F
            facet.max shouldBe 1.5F
        }
        rangeAttrs.facets[2].shouldNotBeNull().let { facet ->
            facet.attrId shouldBe 2
            facet.count shouldBe 13
            facet.min shouldBe -1.0F
            facet.max shouldBe 1.0F
        }
    }

    @Test
    fun ignoreParams() = testWithCompiler {
        val filter = AttrRangeFacetFilter(ProductDoc.rangeAttrs)

        val ctx = filter.prepare(
            "attrs",
            "a",
            mapOf(
                emptyList<String>() to listOf("1.0"),
                listOf("a", "1", "gte") to emptyList(),
                listOf("aaa", "1", "gte") to listOf("2.0"),
                listOf("a", "color", "gte") to listOf("3.0"),
                listOf("a", "1", "lte") to listOf("one"),
                listOf("a", "1", "lt") to listOf("1.0"),
                listOf("a", "1", "lte", "") to listOf("2"),
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.full" to mapOf(
                    "scripted_metric" to mapOf(
                        "init_script" to mapOf(
                            "source" to PreparedAttrRangeFacetFilter.ATTRS_INIT_SCRIPT
                        ),
                        "map_script" to mapOf(
                            "source" to PreparedAttrRangeFacetFilter.ATTRS_MAP_SCRIPT
                        ),
                        "combine_script" to mapOf(
                            "source" to PreparedAttrRangeFacetFilter.ATTRS_COMBINE_SCRIPT
                        ),
                        "reduce_script" to mapOf(
                            "source" to PreparedAttrRangeFacetFilter.ATTRS_REDUCE_SCRIPT
                        ),
                        "params" to mapOf(
                            "attrsField" to "range_attrs",
                            "size" to 100,
                        )
                    )
                )
            )
        )
    }

    @Test
    fun otherFacetFilterExpressions() = testWithCompiler {
        val filter = AttrRangeFacetFilter(ProductDoc.rangeAttrs)

        val ctx = filter.prepare("attrs", emptyMap())
        val sq = SearchQuery()
        ctx.apply(sq, listOf(ProductDoc.status eq 1))

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.filter" to mapOf(
                    "filter" to mapOf(
                        "term" to mapOf("status" to 1)
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.full" to mapOf(
                            "scripted_metric" to mapOf(
                                "init_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.ATTRS_INIT_SCRIPT
                                ),
                                "map_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.ATTRS_MAP_SCRIPT
                                ),
                                "combine_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.ATTRS_COMBINE_SCRIPT
                                ),
                                "reduce_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.ATTRS_REDUCE_SCRIPT
                                ),
                                "params" to mapOf(
                                    "attrsField" to "range_attrs",
                                    "size" to 100,
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun selectedSingle() = testWithCompiler {
        val filter = AttrRangeFacetFilter(ProductDoc.rangeAttrs)

        val ctx = filter.prepare(
            "attrs",
            mapOf(
                listOf("attrs", "1", "gte") to listOf("1")
            )
        )
        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly mapOf(
            "aggs" to mapOf(
                "qf:attrs.full.filter" to mapOf(
                    "filter" to mapOf(
                        "range" to mapOf(
                            "range_attrs" to mapOf(
                                "gte" to 0x00000001_3f800000L,
                                "lte" to 0x00000001_7f800000L
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.full" to mapOf(
                            "scripted_metric" to mapOf(
                                "init_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.ATTRS_INIT_SCRIPT
                                ),
                                "map_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.ATTRS_MAP_SCRIPT
                                ),
                                "combine_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.ATTRS_COMBINE_SCRIPT
                                ),
                                "reduce_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.ATTRS_REDUCE_SCRIPT
                                ),
                                "params" to mapOf(
                                    "attrsField" to "range_attrs",
                                    "size" to 100,
                                )
                            )
                        )
                    )
                ),
                "qf:attrs.1" to mapOf(
                    "scripted_metric" to mapOf(
                        "init_script" to mapOf(
                            "source" to PreparedAttrRangeFacetFilter.SINGLE_ATTR_INIT_SCRIPT
                        ),
                        "map_script" to mapOf(
                            "source" to PreparedAttrRangeFacetFilter.SINGLE_ATTR_MAP_SCRIPT
                        ),
                        "combine_script" to mapOf(
                            "source" to PreparedAttrRangeFacetFilter.SINGLE_ATTR_COMBINE_SCRIPT
                        ),
                        "reduce_script" to mapOf(
                            "source" to PreparedAttrRangeFacetFilter.SINGLE_ATTR_REDUCE_SCRIPT
                        ),
                        "params" to mapOf(
                            "attrId" to 1,
                            "attrsField" to "range_attrs",
                        )
                    )
                ),
            ),
            "post_filter" to mapOf(
                "range" to mapOf(
                    "range_attrs" to mapOf(
                        "gte" to 0x00000001_3f800000L,
                        "lte" to 0x00000001_7f800000L
                    )
                )
            )
        )

        val rangeAttrs = ctx.processResult(searchResultWithAggs(
            "qf:attrs.full" to ScriptedMetricAggResult(
                listOf(
                    AttrRangeFacet(1, 34, 0.2F, 1.5F),
                    AttrRangeFacet(2, 13, -1.0F, 1.0F),
                )
            ),
            "qf:attrs.1" to ScriptedMetricAggResult(
                AttrRangeFacet(1, 11, 0.5F, 1.2F)
            )
        ))

        rangeAttrs.facets.size shouldBe 2
        rangeAttrs.facets[1].shouldNotBeNull().let { facet ->
            facet.attrId shouldBe 1
            facet.count shouldBe 11
            facet.min shouldBe 0.5F
            facet.max shouldBe 1.2F
        }
        rangeAttrs.facets[2].shouldNotBeNull().let { facet ->
            facet.attrId shouldBe 2
            facet.count shouldBe 13
            facet.min shouldBe -1.0F
            facet.max shouldBe 1.0F
        }
    }

    @Test
    fun selectedMultiple() = testWithCompiler {
        val filter = AttrRangeFacetFilter(ProductDoc.rangeAttrs)

        var ctx = filter.prepare(
            "attrs",
            mapOf(
                listOf("attrs", "1", "lte") to listOf("1"),
                listOf("attrs", "2", "gte") to listOf("-1"),
                listOf("attrs", "2", "lte") to listOf("1"),
            )
        )
        var sq = SearchQuery()
        ctx.apply(sq, emptyList())

        val expectedBody = mapOf(
            "aggs" to mapOf(
                "qf:attrs.full.filter" to mapOf(
                    "filter" to mapOf(
                        "bool" to mapOf(
                            "filter" to listOf(
                                mapOf(
                                    "bool" to mapOf(
                                        "should" to listOf(
                                            mapOf(
                                                "range" to mapOf(
                                                    "range_attrs" to mapOf(
                                                        "gte" to 0x00000001_00000000L,
                                                        "lte" to 0x00000001_3f800000L
                                                    )
                                                )
                                            ),
                                            mapOf(
                                                "range" to mapOf(
                                                    "range_attrs" to mapOf(
                                                        "gte" to 0x00000001_80000000L,
                                                        "lte" to 0x00000001_ff800000L
                                                    )
                                                )
                                            )
                                        )
                                    )
                                ),
                                mapOf(
                                    "bool" to mapOf(
                                        "should" to listOf(
                                            mapOf(
                                                "range" to mapOf(
                                                    "range_attrs" to mapOf(
                                                        "gte" to 0x00000002_00000000L,
                                                        "lte" to 0x00000002_3f800000L
                                                    )
                                                )
                                            ),
                                            mapOf(
                                                "range" to mapOf(
                                                    "range_attrs" to mapOf(
                                                        "gte" to 0x00000002_80000000L,
                                                        "lte" to 0x00000002_bf800000L
                                                    )
                                                )
                                            ),
                                        )
                                    )
                                ),
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.full" to mapOf(
                            "scripted_metric" to mapOf(
                                "init_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.ATTRS_INIT_SCRIPT
                                ),
                                "map_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.ATTRS_MAP_SCRIPT
                                ),
                                "combine_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.ATTRS_COMBINE_SCRIPT
                                ),
                                "reduce_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.ATTRS_REDUCE_SCRIPT
                                ),
                                "params" to mapOf(
                                    "attrsField" to "range_attrs",
                                    "size" to 100,
                                )
                            )
                        )
                    )
                ),
                "qf:attrs.1.filter" to mapOf(
                    "filter" to mapOf(
                        "bool" to mapOf(
                            "should" to listOf(
                                mapOf(
                                    "range" to mapOf(
                                        "range_attrs" to mapOf(
                                            "gte" to 0x00000002_00000000L,
                                            "lte" to 0x00000002_3f800000L
                                        )
                                    )
                                ),
                                mapOf(
                                    "range" to mapOf(
                                        "range_attrs" to mapOf(
                                            "gte" to 0x00000002_80000000L,
                                            "lte" to 0x00000002_bf800000L
                                        )
                                    )
                                ),
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.1" to mapOf(
                            "scripted_metric" to mapOf(
                                "init_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.SINGLE_ATTR_INIT_SCRIPT
                                ),
                                "map_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.SINGLE_ATTR_MAP_SCRIPT
                                ),
                                "combine_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.SINGLE_ATTR_COMBINE_SCRIPT
                                ),
                                "reduce_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.SINGLE_ATTR_REDUCE_SCRIPT
                                ),
                                "params" to mapOf(
                                    "attrId" to 1,
                                    "attrsField" to "range_attrs",
                                )
                            )
                        )
                    )
                ),
                "qf:attrs.2.filter" to mapOf(
                    "filter" to mapOf(
                        "bool" to mapOf(
                            "should" to listOf(
                                mapOf(
                                    "range" to mapOf(
                                        "range_attrs" to mapOf(
                                            "gte" to 0x00000001_00000000L,
                                            "lte" to 0x00000001_3f800000L
                                        )
                                    )
                                ),
                                mapOf(
                                    "range" to mapOf(
                                        "range_attrs" to mapOf(
                                            "gte" to 0x00000001_80000000L,
                                            "lte" to 0x00000001_ff800000L
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    "aggs" to mapOf(
                        "qf:attrs.2" to mapOf(
                            "scripted_metric" to mapOf(
                                "init_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.SINGLE_ATTR_INIT_SCRIPT
                                ),
                                "map_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.SINGLE_ATTR_MAP_SCRIPT
                                ),
                                "combine_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.SINGLE_ATTR_COMBINE_SCRIPT
                                ),
                                "reduce_script" to mapOf(
                                    "source" to PreparedAttrRangeFacetFilter.SINGLE_ATTR_REDUCE_SCRIPT
                                ),
                                "params" to mapOf(
                                    "attrId" to 2,
                                    "attrsField" to "range_attrs",
                                )
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
                                "should" to listOf(
                                    mapOf(
                                        "range" to mapOf(
                                            "range_attrs" to mapOf(
                                                "gte" to 0x00000001_00000000L,
                                                "lte" to 0x00000001_3f800000L
                                            )
                                        )
                                    ),
                                    mapOf(
                                        "range" to mapOf(
                                            "range_attrs" to mapOf(
                                                "gte" to 0x00000001_80000000L,
                                                "lte" to 0x00000001_ff800000L
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                        mapOf(
                            "bool" to mapOf(
                                "should" to listOf(
                                    mapOf(
                                        "range" to mapOf(
                                            "range_attrs" to mapOf(
                                                "gte" to 0x00000002_00000000L,
                                                "lte" to 0x00000002_3f800000L
                                            )
                                        )
                                    ),
                                    mapOf(
                                        "range" to mapOf(
                                            "range_attrs" to mapOf(
                                                "gte" to 0x00000002_80000000L,
                                                "lte" to 0x00000002_bf800000L
                                            )
                                        )
                                    ),
                                )
                            )
                        ),
                    )
                )
            )
        )

        compile(sq).body shouldContainExactly expectedBody

        ctx = filter.prepare(
            "attrs",
            mapOf(
                listOf("attrs", "1", "lte") to listOf("1"),
                listOf("attrs", "2", "lte") to listOf("1"),
                listOf("attrs", "2", "gte") to listOf("-1"),
            )
        )
        sq = SearchQuery()
        ctx.apply(sq, emptyList())

        compile(sq).body shouldContainExactly expectedBody
    }
}
