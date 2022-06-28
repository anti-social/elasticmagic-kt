package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.AggAwareResult
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.Aggregation
import dev.evo.elasticmagic.aggs.AggregationResult
import dev.evo.elasticmagic.aggs.FilterAgg
import dev.evo.elasticmagic.aggs.SingleBucketAggResult
import dev.evo.elasticmagic.aggs.SingleValueMetricAggResult
import dev.evo.elasticmagic.aggs.ValueCountAgg
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression

/**
 *
 */
class FacetRangeFilter<T>(
    val field: FieldOperations<T>,
    name: String? = null,
    val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : Filter<PreparedFacetRangeFilter<T>, FacetRangeFilterResult<T>>(name) {

    override fun prepare(name: String, params: QueryFilterParams): PreparedFacetRangeFilter<T> {
        val from = params.decodeLastValue(name to "gte", field.getFieldType())
        val to = params.decodeLastValue(name to "lte", field.getFieldType())
        val filterExpression = if (from != null || to != null) {
            field.range(gte = from, lte = to)
        } else {
            null
        }
        return PreparedFacetRangeFilter(this, name, filterExpression, from = from, to = to)
    }
}

class PreparedFacetRangeFilter<T>(
    val filter: FacetRangeFilter<T>,
    name: String,
    facetFilterExpr: QueryExpression?,
    val from: Any?,
    val to: Any?,
) : PreparedFilter<FacetRangeFilterResult<T>>(name, facetFilterExpr) {

    private val countAggName = aggName("count")
    private val filterAggName = aggName("filter")

    private fun aggName(key: String) = "qf:$name.$key"

    override fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        var aggs = buildMap {
            put(countAggName, ValueCountAgg(filter.field))
            putAll(filter.aggs.mapKeys { (key, _) -> aggName(key) })
        }
        if (otherFacetFilterExpressions.isNotEmpty()) {
            aggs = mapOf(
                filterAggName to FilterAgg(
                    if (otherFacetFilterExpressions.size == 1) {
                        otherFacetFilterExpressions[0]
                    } else {
                        Bool(filter = otherFacetFilterExpressions)
                    },
                    aggs = aggs
                )
            )
        }
        searchQuery.aggs(aggs)
        if (facetFilterExpr != null) {
            searchQuery.postFilter(facetFilterExpr)
        }
    }

    override fun processResult(
        searchQueryResult: SearchQueryResult<*>
    ): FacetRangeFilterResult<T> {
        val aggResult = searchQueryResult.aggIfExists<SingleBucketAggResult>(filterAggName) ?: searchQueryResult
        val count = aggResult.agg<SingleValueMetricAggResult<Long>>(countAggName).value
            ?: throw IllegalStateException("value_count aggregation without a value")
        val aggs = filter.aggs.mapValues { (aggKey, _) ->
            val aggName = aggName(aggKey)
            aggResult.aggs[aggName] ?: throw NoSuchElementException(aggName)
        }
        return FacetRangeFilterResult(
            name,
            from = from?.let(filter.field::deserializeTerm),
            to = to?.let(filter.field::deserializeTerm),
            count = count,
            aggs = aggs,
        )
    }
}

data class FacetRangeFilterResult<T>(
    override val name: String,
    val from: T?,
    val to: T?,
    val count: Long,
    override val aggs: Map<String, AggregationResult>,
) : FilterResult, AggAwareResult()
