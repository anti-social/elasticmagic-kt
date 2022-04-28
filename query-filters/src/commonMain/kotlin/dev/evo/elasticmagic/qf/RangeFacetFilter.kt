package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.Aggregation
import dev.evo.elasticmagic.aggs.FilterAgg
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression

class RangeFacetFilter<T>(
    private val field: FieldOperations<T>,
    name: String? = null,
) : Filter<RangeFacetFilterContext, RangeFacetFilterResult<T>>(name) {
    private fun enabledAggName(name: String) = "qf.$name.enabled"

    private fun filterAggName(name: String) = "qf.$name.filter"

    override fun prepareContext(name: String, params: QueryFilterParams): RangeFacetFilterContext {
        val from = params.decodeLastValue(name to "gte", field.getFieldType())
        val to = params.decodeLastValue(name to "lte", field.getFieldType())
        val filterExpression = if (from != null || to != null) {
            field.range(gte = from, lte = to)
        } else {
            null
        }
        return RangeFacetFilterContext(name, filterExpression, from = from, to = to)
    }

    override fun apply(
        searchQuery: SearchQuery<*>,
        filterCtx: FilterContext,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        val ctx = filterCtx.cast<RangeFacetFilterContext>()

        var aggs = buildMap<String, Aggregation<*>> {
            put(enabledAggName(ctx.name), FilterAgg(field.ne(null)))
        }
        if (otherFacetFilterExpressions.isNotEmpty()) {
            aggs = mapOf(
                filterAggName(ctx.name) to FilterAgg(
                    Bool(filter = otherFacetFilterExpressions),
                    aggs = aggs
                )
            )
        }
        searchQuery.aggs(aggs)
    }

    override fun processResult(
        searchQueryResult: SearchQueryResult<*>,
        filterCtx: FilterContext
    ): RangeFacetFilterResult<T> {
        val ctx = filterCtx.cast<RangeFacetFilterContext>()
        return RangeFacetFilterResult(
            ctx.name,
            from = ctx.from?.let(field::deserializeTerm),
            to = ctx.to?.let(field::deserializeTerm),
        )
    }
}

class RangeFacetFilterContext(
    name: String,
    facetFilterExpr: QueryExpression?,
    val from: Any?,
    val to: Any?,
) : FilterContext(name, facetFilterExpr)

data class RangeFacetFilterResult<T>(
    override val name: String,
    val from: T?,
    val to: T?,
) : FilterResult
