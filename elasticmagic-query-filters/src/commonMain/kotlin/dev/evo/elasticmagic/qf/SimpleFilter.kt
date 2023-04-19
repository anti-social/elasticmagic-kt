package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression


open class SimpleFilter<T>(
    val field: FieldOperations<T>,
    name: String? = null,
    val mode: FilterMode = FilterMode.UNION,
) : Filter<PreparedSimpleFilter, BaseFilterResult>(name) {

    override fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedSimpleFilter {
        println("SimpleFilter.prepare: name=$name, paramName=$paramName, params=$params")
        println(field.getFieldType())
        val filterValues = params.decodeValues(paramName, field.getFieldType())

        println("SimpleFilter.prepare: filterValues=$filterValues")
        return PreparedSimpleFilter(
            name,
            paramName,
            mode.filterByValues(field, filterValues),
        )
    }
}

/**
 * Filter that is ready for applying to a search query.
 */

open class BaseFilterResult(
    override val name: String,
    override val paramName: String,
) : FilterResult

class PreparedSimpleFilter(
    name: String,
    paramName: String,
    facetFilterExpr: QueryExpression?,
) : PreparedFilter<BaseFilterResult>(name, paramName, facetFilterExpr) {
    override fun apply(searchQuery: SearchQuery<*>, otherFacetFilterExpressions: List<QueryExpression>) {
        if (facetFilterExpr != null) {
            searchQuery.filter(facetFilterExpr)
        }
    }

    override fun processResult(
        searchQueryResult: SearchQueryResult<*>,
    ): BaseFilterResult {
        return BaseFilterResult(name, paramName)
    }
}
