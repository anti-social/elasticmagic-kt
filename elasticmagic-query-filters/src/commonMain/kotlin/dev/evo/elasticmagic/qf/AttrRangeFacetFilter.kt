package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.AggAwareResult
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.Aggregation
import dev.evo.elasticmagic.aggs.FilterAgg
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.aggs.FilterAggResult
import dev.evo.elasticmagic.doc.BoundRuntimeField
import dev.evo.elasticmagic.doc.RootFieldSet
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.query.Range
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.types.FloatType
import dev.evo.elasticmagic.types.IntType
import dev.evo.elasticmagic.types.LongType

fun encodeRangeAttrWithValue(attrId: Int, value: Float): Long {
    return (attrId.toLong() shl 32) or (value.toBits().toLong() and 0x00000000_ffffffffL)
}

class AttrRangeFacetFilter(
    val field: FieldOperations<Long>,
    paramName: String? = null
) : Filter<PreparedAttrRangeFacetFilter, AttrRangeFacetFilterResult>(paramName) {

    /**
     * 
     *             -Inf                 +0.0
     *    0x{attr_id}_ff800000 0x{attr_id}_00000000
     *                       | |
     *                       ***
     *                    **     **
     *                 **           **
     *                *               *
     *    negative   *                 *   positive
     *    floats ⤹   *                 *  ⤸ floats
     *                *               *
     *                 **           **
     *                    **     **
     *                       ***
     *                       |||
     *    0x{attr_id}_80000000|0x{attr_id}_7f800000
     *           -0.0         |            +Inf
     *                0x{attr_id}_7fc00000
     *                       NaN
     */
    sealed class SelectedValue {
        abstract val attrId: Int

        abstract fun filterExpression(field: FieldOperations<Long>): QueryExpression

        data class Gte(override val attrId: Int, val gte: Float) : SelectedValue() {
            fun lte(lte: Float): SelectedValue {
                return Between(attrId, gte, lte)
            }

            override fun filterExpression(field: FieldOperations<Long>): QueryExpression {
                return when {
                    gte == 0.0F -> Bool.should(
                        field.eq(encodeRangeAttrWithValue(attrId, -0.0F)),
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, 0.0F),
                            lte = encodeRangeAttrWithValue(attrId, Float.POSITIVE_INFINITY)
                        )
                    )
                    gte > 0.0F -> field.range(
                        gte = encodeRangeAttrWithValue(attrId, gte),
                        lte = encodeRangeAttrWithValue(attrId, Float.POSITIVE_INFINITY)
                    )
                    else -> field.range(
                        gte = encodeRangeAttrWithValue(attrId, 0.0F),
                        lte = encodeRangeAttrWithValue(attrId, gte)
                    )
                }
            }
        }

        data class Lte(override val attrId: Int, val lte: Float) : SelectedValue() {
            fun gte(gte: Float): SelectedValue {
                return Between(attrId, gte, lte)
            }

            override fun filterExpression(field: FieldOperations<Long>): QueryExpression {
                return when {
                    lte == 0.0F -> Bool.should(
                        field.eq(encodeRangeAttrWithValue(attrId, 0.0F)),
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, -0.0F),
                            lte = encodeRangeAttrWithValue(attrId, Float.NEGATIVE_INFINITY),
                        )
                    )
                    lte < 0.0F -> field.range(
                        gte = encodeRangeAttrWithValue(attrId, lte),
                        lte = encodeRangeAttrWithValue(attrId, Float.NEGATIVE_INFINITY)
                    )
                    else -> Bool.should(
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, 0.0F),
                            lte = encodeRangeAttrWithValue(attrId, lte),
                        ),
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, -0.0F),
                            lte = encodeRangeAttrWithValue(attrId, Float.NEGATIVE_INFINITY)
                        )
                    )
                }
            }
        }

        data class Between(override val attrId: Int, val gte: Float, val lte: Float) : SelectedValue() {
            override fun filterExpression(field: FieldOperations<Long>): QueryExpression {
                return when {
                    gte == 0.0F && lte == 0.0F -> field.oneOf(
                        encodeRangeAttrWithValue(attrId, 0.0F),
                        encodeRangeAttrWithValue(attrId, -0.0F),
                    )
                    gte > lte -> field.eq(
                        encodeRangeAttrWithValue(attrId, Float.NaN),
                    )
                    gte == 0.0F -> Bool.should(
                        field.eq(encodeRangeAttrWithValue(attrId, -0.0F)),
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, 0.0F),
                            lte = encodeRangeAttrWithValue(attrId, lte)
                        )
                    )
                    lte == 0.0F -> Bool.should(
                        field.eq(encodeRangeAttrWithValue(attrId, 0.0F)),
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, -0.0F),
                            lte = encodeRangeAttrWithValue(attrId, gte)
                        )
                    )
                    gte > 0.0F && lte > 0.0F -> field.range(
                        gte = encodeRangeAttrWithValue(attrId, gte),
                        lte = encodeRangeAttrWithValue(attrId, lte)
                    )
                    gte < 0.0F && lte < 0.0F -> field.range(
                        gte = encodeRangeAttrWithValue(attrId, lte),
                        lte = encodeRangeAttrWithValue(attrId, gte)
                    )
                    else -> Bool.should(
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, 0.0F),
                            lte = encodeRangeAttrWithValue(attrId, lte)
                        ),
                        field.range(
                            gte = encodeRangeAttrWithValue(attrId, -0.0F),
                            lte = encodeRangeAttrWithValue(attrId, gte)
                        )
                    )
                }
            }
        }
    }

    override fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedAttrRangeFacetFilter {
        val selectedValues = buildMap<_, SelectedValue> {
            for ((keys, values) in params) {
                when {
                    keys.isEmpty() -> {}
                    values.isEmpty() -> {}
                    keys[0] != paramName -> {}
                    keys.size == 3 -> {
                        val attrId = IntType.deserializeTermOrNull(keys[1]) ?: continue
                        val value = FloatType.deserializeTermOrNull(values.last()) ?: continue
                        when (keys[2]){
                            "gte" -> {
                                val selectedValue = getOrPut(attrId) { SelectedValue.Gte(attrId, value) }
                                if (selectedValue is SelectedValue.Lte) {
                                    put(attrId, selectedValue.gte(value))
                                } else {
                                    put(attrId, selectedValue)
                                }
                            }
                            "lte" -> {
                                val selectedValue = getOrPut(attrId) { SelectedValue.Lte(attrId, value) }
                                if (selectedValue is SelectedValue.Gte) {
                                    put(attrId, selectedValue.lte(value))
                                } else {
                                    put(attrId, selectedValue)
                                }
                            }
                            else -> {}
                        }
                    }
                    else -> {}
                }
            }
        }
        println(selectedValues)

        val facetFilters = selectedValues.values.map { w ->
            w.filterExpression(field)
        }
        val facetFilterExpr = when (facetFilters.size) {
            0 -> null
            1 -> facetFilters[0]
            else -> Bool.filter(facetFilters)
        }

        return PreparedAttrRangeFacetFilter(this, name, facetFilterExpr, selectedValues)
    }
}

class PreparedAttrRangeFacetFilter(
    val filter: AttrRangeFacetFilter,
    name: String,
    facetFilterExpr: QueryExpression?,
    val selectedValues: Map<Int, AttrRangeFacetFilter.SelectedValue>
) : PreparedFilter<AttrRangeFacetFilterResult>(name, facetFilterExpr) {

    companion object {
        const val DEFAULT_ATTR_IDS_AGG_SIZE = 100
        internal val ATTR_IDS_SCRIPT = """
            int attrsLen = doc[params.attrsField].size();
            if (attrsLen > 0) {
                for (def attrValue : doc[params.attrsField]) {
                    long attrId = attrValue >> 32;
                    emit(attrId);
                }
            }
        """.trimIndent()
    }

    private val attrIdsAggName = "qf:$name.attr_ids"
    private val attrIdsFilterAggName = "qf:$name.attr_ids.filter"

    override fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
/*         for ((attrId, selectedAttrValues) in selectedValues) {

        }
 */
        val attrIdsFieldName = "_qf_${name}_attr_range_ids"
        val attrIdsField = BoundRuntimeField(
            attrIdsFieldName, LongType,
            Script.Source(
                ATTR_IDS_SCRIPT,
                params = mapOf(
                    "attrsField" to filter.field,
                )
            ),
            RootFieldSet
        )
        searchQuery.runtimeMappings(
            attrIdsFieldName to attrIdsField
        )

        val aggs = buildMap<_, Aggregation<*>> {
            val fullAttrIdsAgg = TermsAgg(
                attrIdsField, size = DEFAULT_ATTR_IDS_AGG_SIZE
            )
            if (facetFilterExpr != null) {
                put(
                    attrIdsFilterAggName,
                    FilterAgg(facetFilterExpr, aggs = mapOf(attrIdsAggName to fullAttrIdsAgg))
                )
            } else {
                // FIXME: name of aggregation when multiple filters share the same name
                put(
                    attrIdsAggName,
                    fullAttrIdsAgg
                )
            }
        }
        searchQuery.aggs(aggs)

        if (facetFilterExpr != null) {
            searchQuery.postFilter(facetFilterExpr)
        }
    }

    override fun processResult(searchQueryResult: SearchQueryResult<*>): AttrRangeFacetFilterResult {
        var aggResult = searchQueryResult as AggAwareResult

        val attrsAgg = (aggResult.aggIfExists<FilterAggResult>(attrIdsFilterAggName) ?: aggResult)
            .agg<TermsAggResult<Long>>(attrIdsAggName)
        val facets = buildMap {
            for (bucket in attrsAgg.buckets) {
                val attrId = bucket.key.toInt()
                put(attrId, AttrRangeFacet(attrId, 0.0F, 0.0F))
            }
        }

        return AttrRangeFacetFilterResult(name, facets)
    }
}

data class AttrRangeFacetFilterResult(
    override val name: String,
    val facets: Map<Int, AttrRangeFacet>,
) : FilterResult

data class AttrRangeFacet(
    val attrId: Int,
    val minValue: Float,
    val maxValue: Float,
)
