package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.Aggregation
import dev.evo.elasticmagic.aggs.FilterAgg
import dev.evo.elasticmagic.aggs.MaxAgg
import dev.evo.elasticmagic.aggs.MinAgg
import dev.evo.elasticmagic.aggs.SingleBucketAggResult
import dev.evo.elasticmagic.aggs.SingleDoubleValueAgg
import dev.evo.elasticmagic.aggs.SingleValueMetricAggResult
import dev.evo.elasticmagic.aggs.ValueCountAgg
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression

/**
 *
 */
class RangeFacetFilter<T>(
    private val field: FieldOperations<T>,
    name: String? = null,
) : Filter<RangeFacetFilterContext, RangeFacetFilterResult<T>>(name) {
    private fun countAggName(name: String) = "qf.$name.count"
    private fun minAggName(name: String) = "qf.$name.min"
    private fun maxAggName(name: String) = "qf.$name.max"

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
            put(countAggName(ctx.name), ValueCountAgg(field))
            put(minAggName(ctx.name), MinAgg(field))
            put(maxAggName(ctx.name), MaxAgg(field))
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
        val aggResult = searchQueryResult.aggIfExists<SingleBucketAggResult>(filterAggName(ctx.name)) ?: searchQueryResult
        val count = aggResult.agg<SingleValueMetricAggResult<Long>>(countAggName(ctx.name)).value!!
        val min = aggResult.agg<SingleValueMetricAggResult<Double>>(minAggName(ctx.name)).value!!
        val max = aggResult.agg<SingleValueMetricAggResult<Double>>(maxAggName(ctx.name)).value!!
        return RangeFacetFilterResult(
            ctx.name,
            from = ctx.from?.let(field::deserializeTerm),
            to = ctx.to?.let(field::deserializeTerm),
            count = count,
            min = min,
            max = max,
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
    val count: Long,
    val min: Double?,
    val max: Double?,
) : FilterResult
