package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.query.Range
import dev.evo.elasticmagic.types.FloatType
import dev.evo.elasticmagic.types.IntType

fun encodeRangeAttrWithValue(attrId: Int, value: Float): Long {
    return (attrId.toLong() shl 32) or java.lang.Float.floatToIntBits(value).toLong()
}

class AttrRangeFacetFilter(
    val field: FieldOperations<Long>,
    name: String? = null
) : Filter<PreparedAttrRangeFacetFilter, AttrRangeFacetFilterResult>(name) {

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
     *    floats ⤹  *                  *  ⤸ floats
     *               *                * 
     *                 **           **
     *                    **     **
     *                       ***
     *                       | |
     *    0x{attr_id}_80000000 0x{attr_id}_7f800000
     *             -0.0                 +Inf
     *
     */
    sealed class SelectedValue {
        abstract val attrId: Int

        abstract fun filterExpression(field: FieldOperations<Long>): QueryExpression

        data class Gte(override val attrId: Int, val gte: Float) : SelectedValue() {
            fun lte(lte: Float): SelectedValue {
                return Between(attrId, gte, lte)
            }

            override fun filterExpression(field: FieldOperations<Long>): QueryExpression {
                if (gte >= 0.0F) {
                    return field.range(
                        gte = encodeRangeAttrWithValue(attrId, gte),
                        lte = encodeRangeAttrWithValue(attrId, Float.POSITIVE_INFINITY)
                    )
                }
                return field.range(
                    gte = encodeRangeAttrWithValue(attrId, 0.0F),
                    lte = encodeRangeAttrWithValue(attrId, gte)
                )
            }
        }

        data class Lte(override val attrId: Int, val lte: Float) : SelectedValue() {
            fun gte(gte: Float): SelectedValue {
                return Between(attrId, gte, lte)
            }

            override fun filterExpression(field: FieldOperations<Long>): QueryExpression {
                if (lte <= -0.0F) {
                    return field.range(
                        gte = encodeRangeAttrWithValue(attrId, lte),
                        lte = encodeRangeAttrWithValue(attrId, Float.NEGATIVE_INFINITY)
                    )
                }
                return Bool.should(
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

        data class Between(override val attrId: Int, val gte: Float, val lte: Float) : SelectedValue() {
            override fun filterExpression(field: FieldOperations<Long>): QueryExpression {
                if (gte >= 0.0F) {
                    return field.range(
                        gte=encodeRangeAttrWithValue(attrId, gte),
                        lte=encodeRangeAttrWithValue(attrId, lte)
                    )
                }
                if (lte <= -0.0F) {
                    return field.range(
                        gte=encodeRangeAttrWithValue(attrId, lte),
                        lte=encodeRangeAttrWithValue(attrId, gte)
                    )
                }
                return Bool.should(
                    field.range(
                        gte=encodeRangeAttrWithValue(attrId, 0.0F),
                        lte=encodeRangeAttrWithValue(attrId, lte)
                    ),
                    field.range(
                        gte=encodeRangeAttrWithValue(attrId, -0.0F),
                        lte=encodeRangeAttrWithValue(attrId, gte)
                    )
                )
            }
        }
    }

/*     data class SelectedValue(val attrId: Int, val gte: Float?, val lte: Float?) {
        fun filterExpression(field: FieldOperations<Long>): QueryExpression {
            val range = Range

            return if (valueIds.size == 1) {
                field eq encodeAttrWithValue(attrId, valueIds[0])
            } else if (mode == FacetFilterMode.UNION){
                field oneOf valueIds.map { v -> encodeAttrWithValue(attrId, v) }
            } else {
                Bool.filter(valueIds.map { v -> field eq encodeAttrWithValue(attrId, v) })
            }
        }
    }
 */
    override fun prepare(name: String, params: QueryFilterParams): PreparedAttrRangeFacetFilter {
        val selectedValues = buildMap<_, SelectedValue> {
            for ((keys, values) in params) {
                when {
                    keys.isEmpty() -> {}
                    values.isEmpty() -> {}
                    keys[0] != name -> {}
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
    override fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        if (facetFilterExpr != null) {
            searchQuery.postFilter(facetFilterExpr)
        }
    }

    override fun processResult(searchQueryResult: SearchQueryResult<*>): AttrRangeFacetFilterResult {
        TODO("not implemented")
    }
}

data class AttrRangeFacetFilterResult(
    override val name: String,
) : FilterResult