package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.types.FieldType
import dev.evo.elasticmagic.types.ValueDeserializationException
import dev.evo.elasticmagic.util.OrderedMap

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

typealias QueryFilterParams = Map<Pair<String, String>, List<String>>
typealias MutableQueryFilterParams = MutableMap<Pair<String, String>, MutableList<String>>

private fun <T> FieldType<*, T>.deserializeTermOrNull(term: Any): T? {
    return try {
        deserializeTerm(term)
    } catch (e: ValueDeserializationException) {
        null
    }
}

fun <T> QueryFilterParams.decodeValues(
    key: Pair<String, String>, fieldType: FieldType<*, T>
): List<T> {
    return get(key)?.mapNotNull(fieldType::deserializeTermOrNull) ?: emptyList()
}

fun <T> QueryFilterParams.decodeLastValue(
    key: Pair<String, String>, fieldType: FieldType<*, T>
): T? {
    return decodeValues(key, fieldType).lastOrNull()
}

open class QueryFilters : Iterable<BoundFilter<*, *>> {
    private val filters = OrderedMap<String, BoundFilter<*, *>>()

    fun addFilter(filter: BoundFilter<*, *>) {
        filters[filter.name] = filter
    }

    override fun iterator(): Iterator<BoundFilter<*, *>> {
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
    private val queryFilters: OrderedMap<String, BoundFilter<*, *>>,
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
    operator fun <R: FilterResult> get(filter: BoundFilter<*, R>): R {
        @Suppress("UNCHECKED_CAST")
        return results[filter.name] as R
    }

    override fun iterator(): Iterator<FilterResult> {
        return results.values.iterator()
    }
}

abstract class Filter<C: PreparedFilter<R>, R: FilterResult>(private val name: String?) {
    abstract fun prepare(name: String, params: QueryFilterParams): C

    operator fun provideDelegate(
        thisRef: QueryFilters, property: KProperty<*>
    ): ReadOnlyProperty<QueryFilters, BoundFilter<C, R>> {
        val boundFilter = BoundFilter(name ?: property.name, this)
        thisRef.addFilter(boundFilter)
        return ReadOnlyProperty { _, _ ->
            boundFilter
        }
    }
}

abstract class PreparedFilter<R: FilterResult>(
    val name: String,
    val facetFilterExpr: QueryExpression?,
) {
    abstract fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    )

    abstract fun processResult(
        searchQueryResult: SearchQueryResult<*>
    ): R
}

interface FilterResult {
    val name: String
}

class BoundFilter<C: PreparedFilter<R>, R: FilterResult>(val name: String, val filter: Filter<C, R>) {
    fun prepare(params: QueryFilterParams): C {
        return filter.prepare(name, params)
    }
}
