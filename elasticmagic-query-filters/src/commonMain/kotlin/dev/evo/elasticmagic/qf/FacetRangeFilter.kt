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
 * [FacetRangeFilter] filters a search query using a [dev.evo.elasticmagic.query.Range] query.
 * Also calculates number of documents that have value in the [FacetRangeFilter.field].
 *
 * @param field - field to filter a search query with
 * @param name - optional filter name. If omitted, name of a property will be used
 * @param aggs - mapping with aggregations. Can be used to calculate aggregation for a
 *   [FacetRangeFilter.field]. For example, minimum and maximum values or a histogram can be
 *   calculated.
 */
class FacetRangeFilter<T>(
    val field: FieldOperations<T>,
    name: String? = null,
    val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : Filter<PreparedFacetRangeFilter<T>, FacetRangeFilterResult<T>>(name) {

    /**
     * Parses [params] and prepares the [FacetRangeFilter] for applying.
     *
     * @param name - name of the filter
     * @param params - parameters that should be applied to a search query.
     *   Supports 2 operations: `gte` and `lte`. Examples:
     *   - `mapOf(("price" to "gte") to listOf("10"), ("price" to "lte") to listOf("150")))`
     */
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

/**
 * Filter that is ready for applying to a search query.
 */
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

/**
 * [FacetRangeFilterResult] contains result of a [FacetRangeFilter].
 *
 * @param name - name of the [FacetRangeFilter]
 * @param from - value that was applied to a search query via [FieldOperations.gte]
 * @param to - value that was applied to a search query via [FieldOperations.lte]
 * @param count - number of documents that have a value in the [FacetRangeFilter.field]
 * @param aggs - results of additional aggregations
 */
data class FacetRangeFilterResult<T>(
    override val name: String,
    val from: T?,
    val to: T?,
    val count: Long,
    override val aggs: Map<String, AggregationResult>,
) : FilterResult, AggAwareResult()
