package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression


class AttrRangeExpressionFilter(
    val field: FieldOperations<Long>,
    name: String? = null
) : Filter<BaseFilterResult>(name) {

    override fun prepare(
        name: String,
        paramName: String,
        params: QueryFilterParams
    ): PreparedAttrRangeExpressionFilter {
        val facetFilters = getAttrRangeFacetSelectedValues(params, paramName)
            .values.map { w ->
                w.filterExpression(field)
            }

        val facetFilterExpr = when (facetFilters.size) {
            0 -> null
            1 -> facetFilters[0]
            else -> Bool.filter(facetFilters)
        }

        return PreparedAttrRangeExpressionFilter(this, name, paramName, facetFilterExpr)
    }
}

class PreparedAttrRangeExpressionFilter(
    val filter: AttrRangeExpressionFilter,
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

