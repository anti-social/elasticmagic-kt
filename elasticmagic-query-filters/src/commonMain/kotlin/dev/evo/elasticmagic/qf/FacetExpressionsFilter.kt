package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.FilterAgg
import dev.evo.elasticmagic.aggs.LongValueAggResult
import dev.evo.elasticmagic.aggs.SingleBucketAggResult
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.QueryExpression

data class FacetExpressionValue(val name: String, val selected: Boolean, val docCount: Long)

class FacetExpressionFilterResult(
    override val name: String,
    override val paramName: String,
    val results: List<FacetExpressionValue>,
    val mode: FilterMode
) : FilterResult

class PreparedFacetExpressionFilter(
    name: String,
    paramName: String,
    private val selectedFilterExprs: List<ExpressionValue>,
    private val facetFilterExprs: List<ExpressionValue>,
    val mode: FilterMode,
) : PreparedFilter<FacetExpressionFilterResult>(
    name,
    paramName,
    getFacetFilterExpr(selectedFilterExprs.map { it.expr }, mode)
) {
    private val filterAggName = "qf.$name.filter"

    private fun makeAggName(value: String) = "qf.$name:$value"

    override fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {

        val filterAggs = facetFilterExprs.associate { makeAggName(it.name) to FilterAgg(it.expr) }
        val filters =
            if (mode == FilterMode.INTERSECT) (otherFacetFilterExpressions + facetFilterExpr).mapNotNull { it }
            else otherFacetFilterExpressions

        val aggs = if (filters.isNotEmpty()) {
            val filtered = filters.filter { f -> facetFilterExprs.all { it.name != f.name } }
            mapOf(
                filterAggName to FilterAgg(
                    maybeWrapBool(Bool::must, filtered),
                    aggs = filterAggs
                )
            )
        } else filterAggs

        searchQuery.aggs(aggs)
        if (facetFilterExpr != null) {
            searchQuery.postFilter(facetFilterExpr)
        }
    }

    override fun processResult(
        searchQueryResult: SearchQueryResult<*>,
    ): FacetExpressionFilterResult {
        val selectedNames = selectedFilterExprs.map { it.name }
        val agg = searchQueryResult.aggs[filterAggName]

        val parsedAggs = if (agg != null)
            (agg as SingleBucketAggResult).aggs
        else
            searchQueryResult.aggs

        val results = facetFilterExprs.map { facetFilterExpr ->
            val filterAgg = parsedAggs[makeAggName(facetFilterExpr.name)]
            val docCount = if (filterAgg is SingleBucketAggResult) {
                filterAgg.docCount
            } else {
                (filterAgg as LongValueAggResult).value
            }
            FacetExpressionValue(
                facetFilterExpr.name,
                selectedNames.contains(facetFilterExpr.name),
                docCount
            )
        }

        return FacetExpressionFilterResult(name, paramName, results, mode)
    }
}

open class FacetExpressionsFilter(
    name: String? = null,
    private val allValues: List<ExpressionValue> = emptyList(),
    val mode: FilterMode = FilterMode.UNION,
) : Filter<FacetExpressionFilterResult>(name) {

    override fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedFacetExpressionFilter {
        val filterValues = params.getOrElse(listOf(paramName), ::emptyList)

        val selectedValues = filterValues.mapNotNull { value ->
            allValues.find { it.name == value }
        }

        return PreparedFacetExpressionFilter(
            name,
            paramName,
            selectedValues,
            allValues,
            mode,
        )
    }
}
