package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression


open class SimpleFilter<T>(
    val field: FieldOperations<T>,
    name: String? = null,
    val mode: FilterMode = FilterMode.UNION,
) : Filter<BaseFilterResult>(name) {

    override fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedSimpleFilter {
        val filterValues = params.decodeValues(paramName, field.getFieldType())
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
    private val filterExpression: QueryExpression?,
) : PreparedFilter<BaseFilterResult>(name, paramName, null) {
    override fun apply(searchQuery: SearchQuery<*>, otherFacetFilterExpressions: List<QueryExpression>) {
        if (filterExpression != null) {
            searchQuery.filter(filterExpression)
        }
    }

    override fun processResult(
        searchQueryResult: SearchQueryResult<*>,
    ): BaseFilterResult {
        return BaseFilterResult(name, paramName)
    }
}
