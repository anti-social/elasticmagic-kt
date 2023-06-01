package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.AggAwareResult
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.AggregationResult
import dev.evo.elasticmagic.aggs.FilterAggResult
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.util.OrderedMap
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

open class QueryFilters : Iterable<BoundFilter<*>> {
    private val filters = OrderedMap<String, BoundFilter<*>>()

    fun addFilter(filter: BoundFilter<*>) {
        filters[filter.name] = filter
    }

    override fun iterator(): Iterator<BoundFilter<*>> {
        return filters.values.iterator()
    }

    fun apply(searchQuery: SearchQuery<*>, params: QueryFilterParams): AppliedQueryFilters {
        val preparedFilters = prepare(params)
        apply(searchQuery, preparedFilters)
        return AppliedQueryFilters(filters, preparedFilters)
    }

    private fun prepare(params: QueryFilterParams): Map<String, PreparedFilter<*>> {
        val appliedFilters = mutableMapOf<String, PreparedFilter<*>>()
        for (filter in filters.values) {
            appliedFilters[filter.name] = filter.prepare(params)
        }
        return appliedFilters
    }

    private fun apply(searchQuery: SearchQuery<*>, preparedFilters: Map<String, PreparedFilter<*>>) {
        for (filter in filters.values) {
            val preparedFilter = preparedFilters[filter.name]
                ?: throw IllegalStateException("Missing prepared filter for a name: ${filter.name}")

            val otherFacetFilterExpressions = preparedFilters.mapNotNull { (n, f) ->
                if (n != filter.name && f.facetFilterExpr != null) {
                    f.facetFilterExpr
                } else {
                    null
                }
            }
            preparedFilter.apply(searchQuery, otherFacetFilterExpressions)
        }
    }

}

class AppliedQueryFilters(
    private val queryFilters: OrderedMap<String, BoundFilter<*>>,
    private val preparedFilters: Map<String, PreparedFilter<*>>
) {
    fun processResult(searchQueryResult: SearchQueryResult<*>): QueryFiltersResult {
        val results = OrderedMap<String, FilterResult>()
        for (filter in queryFilters.values) {
            val preparedFilter = preparedFilters[filter.name]
                ?: throw IllegalStateException("Missing prepared filter for a name: ${filter.name}")
            results[filter.name] = preparedFilter.processResult(searchQueryResult)
        }
        return QueryFiltersResult(results)
    }
}

class QueryFiltersResult(
    private val results: OrderedMap<String, FilterResult>
) : Iterable<FilterResult> {
    operator fun <R : FilterResult> get(filter: BoundFilter<R>): R {
        @Suppress("UNCHECKED_CAST")
        return results[filter.name] as R
    }

    override fun iterator(): Iterator<FilterResult> {
        return results.values.iterator()
    }
}

abstract class Filter<R : FilterResult>(val name: String?) {
    abstract fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedFilter<R>

    fun prepare(name: String, params: QueryFilterParams): PreparedFilter<R> = prepare(name, name, params)

    operator fun provideDelegate(
        thisRef: QueryFilters, property: KProperty<*>
    ): ReadOnlyProperty<QueryFilters, BoundFilter<R>> {
        val boundFilter = BoundFilter(property.name, name ?: property.name, this)
        thisRef.addFilter(boundFilter)
        return ReadOnlyProperty { _, _ ->
            boundFilter
        }
    }
}

abstract class PreparedFilter<T : FilterResult>(
    val name: String,
    val paramName: String,
    val facetFilterExpr: QueryExpression?,
) {
    abstract fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    )

    abstract fun processResult(
        searchQueryResult: SearchQueryResult<*>
    ): T
}

interface FilterResult {
    val name: String
    val paramName: String
}

class BoundFilter<R : FilterResult>(
    val name: String,
    val paramName: String,
    val filter: Filter<R>
) {
    fun prepare(params: QueryFilterParams): PreparedFilter<R> {
        return filter.prepare(name, paramName, params)
    }
}

fun AggAwareResult.unwrapFilterAgg(filterAggName: String): AggAwareResult {
    return aggIfExists<FilterAggResult>(filterAggName) ?: this
}

inline fun <reified T : AggregationResult> AggAwareResult.facetAgg(aggName: String): T {
    val filterAggName = "$aggName.filter"
    return unwrapFilterAgg(filterAggName).agg<T>(aggName)
}

internal fun maybeWrapBool(
    boolFactory: (List<QueryExpression>) -> Bool,
    expressions: List<QueryExpression>
): QueryExpression {
    return if (expressions.size == 1) {
        expressions[0]
    } else {
        boolFactory(expressions)
    }
}
