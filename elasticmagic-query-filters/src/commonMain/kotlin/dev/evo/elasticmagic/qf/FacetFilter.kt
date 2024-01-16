package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.AggAwareResult
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.AggregationResult
import dev.evo.elasticmagic.aggs.FilterAgg
import dev.evo.elasticmagic.aggs.SingleBucketAggResult
import dev.evo.elasticmagic.aggs.TermBucket
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.util.OrderedMap

/**
 * [FilterMode] determines a way how values should be filtered.
 */
enum class FilterMode {
    /**
     * 2 or more selected values are combined with *OR* operator.
     * Most of the facet filters should use this mode. For example, you are buying car wheels
     * of one of the sizes: R16 or R17. So after you choose corresponding values, a search query
     * will be filtered with in following way: `wheel_size == R16 || wheel_size == R17`
     */
    UNION,
    // TODO
    // UNION_MULTIVALUE,
    /**
     * 2 or more selected values are combined using *AND* operator.
     * Especially useful with multi-valued fields. For example, you want to buy a charger
     * that supports AA, AAA and 18650 battery types. So when you choose all the required values,
     * a generated search query should be filtered with
     * `battery_type == AA && battery_type == AAA && battery_type == 18650`
     */
    INTERSECT;

    fun <T> filterByValues(field: FieldOperations<T>, filterValues: List<T & Any>): QueryExpression? {
        return when (filterValues.size) {
            0 -> null
            1 -> field.eq(filterValues[0])
            else -> {
                when (this) {
                    UNION -> field.oneOf(filterValues)
                    INTERSECT -> Bool.filter(filterValues.map { field eq it })
                }

            }
        }
    }
}

/**
 * [FacetFilter] calculates counts for a field values and allows a search query
 * to be filtered by those values.
 *
 * @param field - field where values are stored
 * @param name - optional filter name. If omitted, name of a property will be used
 * @param mode - mode to use when combining selected values. See [FilterMode]
 * @param termsAgg - terms aggregation for the [FacetFilter].
 *   Can be used to change aggregation arguments: [TermsAgg.size], [TermsAgg.minDocCount] and others
 */
class FacetFilter<T, V>(
    val field: FieldOperations<V>,
    name: String? = null,
    val mode: FilterMode = FilterMode.UNION,
    val termsAgg: TermsAgg<T>
) : Filter<FacetFilterResult<T>>(name) {

    companion object {
        /**
         * A shortcut to create a [FacetFilter] without a custom terms aggregation.
         */
        operator fun <T> invoke(
            field: FieldOperations<T>,
            name: String? = null,
            mode: FilterMode = FilterMode.UNION,
        ): FacetFilter<T, T> {
            return FacetFilter(field, name = name, mode = mode, termsAgg = TermsAgg(field))
        }

        /**
         * A shortcut to create a [FacetFilter] with a custom terms aggregation using a lambda
         * which accepts filter's [FacetFilter.field] and returns the aggregation.
         *
         * @param termsAggFactory - factory that creates terms aggregation for a [FacetFilter.field]
         */
        operator fun <T> invoke(
            field: FieldOperations<T>,
            name: String? = null,
            mode: FilterMode = FilterMode.UNION,
            termsAggFactory: (FieldOperations<T>) -> TermsAgg<T>
        ): FacetFilter<T, T> {
            return FacetFilter(field, name = name, mode = mode, termsAgg = termsAggFactory(field))
        }
    }

    /**
     * Parses [params] and prepares the [FacetFilter] for applying.
     *
     * @param name - name of the filter
     * @param params - parameters that should be applied to a search query.
     *   Examples:
     *   - `mapOf(listOf("manufacturer") to listOf("Giant", "Cube"))`
     */
    override fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedFacetFilter<T> {
        val filterValues = params.decodeValues(paramName, field.getFieldType())
        return PreparedFacetFilter(
            this,
            name,
            paramName,
            mode.filterByValues(field, filterValues),
            selectedValues = params.decodeValues(name, termsAgg.value.getValueType()),
        )
    }
}

/**
 * Filter that is ready for applying to a search query.
 */
class PreparedFacetFilter<T>(
    val filter: FacetFilter<T, *>,
    name: String,
    paramName: String,
    facetFilterExpr: QueryExpression?,
    val selectedValues: List<Any>,
) : PreparedFilter<FacetFilterResult<T>>(name, paramName, facetFilterExpr) {
    private val termsAggName = "qf:$name"

    private val filterAggName = "qf:$name.filter"

    override fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        val aggs = if (otherFacetFilterExpressions.isNotEmpty()) {
            val aggFilters = if (filter.mode == FilterMode.INTERSECT && facetFilterExpr != null) {
                otherFacetFilterExpressions + listOf(facetFilterExpr)
            } else {
                otherFacetFilterExpressions
            }
            mapOf(
                filterAggName to FilterAgg(
                    maybeWrapBool(Bool::filter, aggFilters),
                    aggs = mapOf(termsAggName to filter.termsAgg)
                )
            )
        } else {
            mapOf(termsAggName to filter.termsAgg)
        }
        searchQuery.aggs(aggs)
        if (facetFilterExpr != null) {
            searchQuery.postFilter(facetFilterExpr)
        }
    }

    override fun processResult(
        searchQueryResult: SearchQueryResult<*>,
    ): FacetFilterResult<T> {
        val selectedValues = OrderedMap(
            *selectedValues
                .map { it to true }
                .toTypedArray()
        )
        val isSelected = !selectedValues.isEmpty()
        val values = mutableListOf<FacetFilterValue<T>>()
        for (bucket in getTermsAggResult(searchQueryResult).buckets) {
            values.add(
                FacetFilterValue(
                    bucket, selectedValues.containsKey(bucket.key)
                )
            )
            selectedValues.remove(bucket.key)
        }

        for (selectedValue in selectedValues.keys) {
            val bucketValue = filter.termsAgg.value.deserializeTerm(selectedValue)
            values.add(
                FacetFilterValue(
                    TermBucket(bucketValue, 0, 0), true
                )
            )
        }
        return FacetFilterResult(name, paramName, filter.mode, values, isSelected)
    }

     fun getTermsAggResult(
        searchQueryResult: SearchQueryResult<*>
    ): TermsAggResult<T> {
        var aggResult: AggAwareResult = searchQueryResult
        if (aggResult.aggs.containsKey(filterAggName)) {
            aggResult = aggResult.agg<SingleBucketAggResult>(filterAggName)
        }
        return aggResult.agg(termsAggName)
    }
}

/**
 * [FacetFilterResult] represents result of a [FacetFilter].
 *
 * @param name - name of the [FacetFilter]
 * @param mode - mode of the [FacetFilter]
 * @param values - list of facet filter values that keep a value and a count
 * @param selected - flag whether there is at least one selected value in the [FacetFilter]
 */
data class FacetFilterResult<T>(
    override val name: String,
    override val paramName: String,
    val mode: FilterMode,
    val values: List<FacetFilterValue<T>>,
    val selected: Boolean,
) : FilterResult, Iterable<FacetFilterValue<T>> by values


/**
 * [FacetFilterValue] represents bucket of the corresponding terms aggregation.
 *
 * @param bucket - corresponding aggregation bucket
 * @param selected - flag whether the value is selected
 */
data class FacetFilterValue<T>(
    val bucket: TermBucket<T>,
    val selected: Boolean,
) : AggAwareResult() {
    /**
     * Value of the [FacetFilter]
     */
    val value: T = bucket.key

    /**
     * Number of the documents that have such a value
     */
    val count: Long = bucket.docCount

    override val aggs: Map<String, AggregationResult>
        get() = bucket.aggs
}
