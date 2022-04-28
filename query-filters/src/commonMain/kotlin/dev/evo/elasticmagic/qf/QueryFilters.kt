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

    fun apply(searchQuery: SearchQuery<*>, params: QueryFilterParams): Map<String, FilterContext> {
        val filterContexts = prepareContexts(params)
        apply(searchQuery, filterContexts)
        return filterContexts
    }

    private fun prepareContexts(params: QueryFilterParams): Map<String, FilterContext> {
        val appliedFilters = mutableMapOf<String, FilterContext>()
        for (filter in filters.values) {
            appliedFilters[filter.name] = filter.prepareContext(params)
        }
        return appliedFilters
    }

    private fun apply(searchQuery: SearchQuery<*>, filterContexts: Map<String, FilterContext>) {
        val preparedSearchQuery = searchQuery.prepare()
        val postFilters = preparedSearchQuery.postFilters.toList()

        for (filter in filters.values) {
            val filterContext = filterContexts[filter.name]
                ?: throw IllegalStateException("Missing context for filter name: ${filter.name}")
            val otherFacetFilterExpressions = buildList {
                addAll(postFilters)
                for ((filterName, filterCtx) in filterContexts) {
                    if (filterName == filter.name) {
                        continue
                    }
                    if (filterCtx.facetFilterExpr == null) {
                        continue
                    }
                    add(filterCtx.facetFilterExpr)
                }
            }
            if (filterContext.facetFilterExpr != null) {
                searchQuery.postFilter(filterContext.facetFilterExpr)
            }
            filter.apply(searchQuery, filterContext, otherFacetFilterExpressions)
        }
    }

    fun processResult(
        searchQueryResult: SearchQueryResult<*>, filterContexts: Map<String, FilterContext>
    ): QueryFiltersResult {
        val results = OrderedMap<String, FilterResult>()
        for (filter in filters.values) {
            val filterContext = filterContexts[filter.name]
                ?: throw IllegalStateException("Missing context for filter name: ${filter.name}")
            results[filter.name] = filter.processResult(
                searchQueryResult, filterContext
            )
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

abstract class Filter<C: FilterContext, R: FilterResult>(private val name: String?) {
    abstract fun prepareContext(name: String, params: QueryFilterParams): C

    abstract fun apply(
        searchQuery: SearchQuery<*>,
        filterCtx: FilterContext,
        otherFacetFilterExpressions: List<QueryExpression>
    )

    abstract fun processResult(
        searchQueryResult: SearchQueryResult<*>, filterCtx: FilterContext
    ): R

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

open class FilterContext(
    val name: String,
    val facetFilterExpr: QueryExpression?,
) {
    inline fun <reified T: FilterContext> cast(): T {
        require(this is T) {
            "Filter context must be of type ${T::class}"
        }
        return this
    }
}

interface FilterResult {
    val name: String
}

class BoundFilter<C: FilterContext, R: FilterResult>(val name: String, val filter: Filter<C, R>) {
    fun prepareContext(params: QueryFilterParams): C {
        return filter.prepareContext(name, params)
    }

    fun apply(
        searchQuery: SearchQuery<*>,
        filterCtx: FilterContext,
        otherFacetFilterExpressions: List<QueryExpression>,
    ) {
        filter.apply(searchQuery, filterCtx, otherFacetFilterExpressions)
    }

    fun processResult(searchQueryResult: SearchQueryResult<*>, filterCtx: FilterContext): R {
        return filter.processResult(searchQueryResult, filterCtx)
    }
}
