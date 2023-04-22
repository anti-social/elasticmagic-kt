package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.types.BooleanType
import dev.evo.elasticmagic.types.IntType

/**
 * Fiter for attribute values. An attribute value is a pair of 2
 * 32-bit values attribute id and value id combined as a single 64-bit field.
 */
class AttrBoolExpressionFilter(
    val field: FieldOperations<Long>,
    name: String? = null
) : Filter<BaseFilterResult>(name) {

    data class SelectedValues(val attrId: Int, val values: List<Boolean>) {
        fun filterExpression(field: FieldOperations<Long>): QueryExpression {
            return if (values.size == 1) {
                field eq encodeBoolAttrWithValue(attrId, values[0])
            } else {
                field oneOf values.map { v -> encodeBoolAttrWithValue(attrId, v) }
            }
        }
    }

    /**
     * Parses [params] and prepares the [AttrBoolExpressionFilter] for applying.
     *
     * @param name - name of the filter
     * @param params - parameters that should be applied to a search query.
     *   Examples:
     *   - `mapOf(listOf("attrs", "1") to listOf("12", "13"))`
     *   - `mapOf(listOf("attrs", "2", "all") to listOf("101", "102"))
     */
    override fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedAttrBoolExpressionFilter {
        val facetFilters = params
            .asSequence()
            .mapNotNull { (keys, values) ->
                @Suppress("MagicNumber")
                when {
                    keys.isEmpty() -> null
                    keys[0] != paramName -> null
                    keys.size == 2 -> {
                        val attrId = IntType.deserializeTermOrNull(keys[1])
                        if (attrId != null) {
                            val parsedValues = values.mapNotNull(BooleanType::deserializeTermOrNull)
                            if (parsedValues.isNotEmpty()) {
                                attrId to SelectedValues(attrId, parsedValues)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }

                    else -> null
                }
            }.map {
                it.second.filterExpression(field)
            }.toList()

        val facetFilterExpr = when (facetFilters.size) {
            0 -> null
            1 -> facetFilters[0]
            else -> Bool.filter(facetFilters)
        }

        return PreparedAttrBoolExpressionFilter(this, name, paramName, facetFilterExpr)
    }
}

class PreparedAttrBoolExpressionFilter(
    val filter: AttrBoolExpressionFilter,
    name: String,
    paramName: String,
    facetFilterExpr: QueryExpression?,
) : PreparedFilter<BaseFilterResult>(name, paramName, facetFilterExpr) {

    override fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        if (facetFilterExpr != null) {
            searchQuery.filter(facetFilterExpr)
        }
    }

    override fun processResult(searchQueryResult: SearchQueryResult<*>): BaseFilterResult {
        return BaseFilterResult(
            name,
            paramName
        )
    }
}

