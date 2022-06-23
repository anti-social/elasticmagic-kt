package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.AggAwareResult
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.aggs.FilterAgg
import dev.evo.elasticmagic.aggs.SingleBucketAggResult
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.util.OrderedMap

/**
 * [FacetFilterMode] determines a way how values should be filtered:
 *
 * - [UNION] - 2 or more selected values are combined with *OR* operator.
 *   Most of the facet filters should use this mode. For example, you are buying car wheels
 *   of one of the sizes: R16 or R17. So after you choose corresponding values, a search query
 *   will be filtered with in following way: `wheel_size == R16 || wheel_size == R17`
 * - [INTERSECT] - 2 or more selected values are combined using *AND* operator.
 *   Especially useful with multi-valued fields. For example, you want to buy a charger
 *   that supports AA, AAA and 18650 battery types. So when you choose all the required values,
 *   a generated search query should be filtered with
 *   `battery_type == AA && battery_type == AAA && battery_type == 18650`
 */
enum class FacetFilterMode {
    UNION, INTERSECT
}

/**
 * [FacetFilter] calculates counts for a field values and allows a search query
 * to be filtered by those values.
 *
 * @param field - field where values are stored
 * @param name - optional filter name. If omitted, name of a property will be used
 * @param mode - mode to use when combining selected values. See [FacetFilterMode]
 * @param aggFactory - function that creates an aggregation for the [FacetFilter].
 *   Can be used to change aggregation arguments: [TermsAgg.size], [TermsAgg.minDocCount] and others
 */
class FacetFilter<T>(
    private val field: FieldOperations<T>,
    name: String? = null,
    private val mode: FacetFilterMode = FacetFilterMode.UNION,
    private val aggFactory: (FieldOperations<T>) -> TermsAgg<T> = { TermsAgg(it) }
) : Filter<FacetFilterContext, FacetFilterResult<T>>(name) {
    private fun termsAggName(name: String) = "qf:$name"

    private fun filterAggName(name: String) = "qf:$name.filter"

    override fun prepareContext(name: String, params: QueryFilterParams): FacetFilterContext {
        val values = params.decodeValues(name to "", field.getFieldType())
        val filterExpr = when (values.size) {
            0 -> null
            1 -> field.eq(values[0])
            else -> {
                when (mode) {
                    FacetFilterMode.UNION -> field.oneOf(values)
                    FacetFilterMode.INTERSECT -> Bool.filter(values.map { field eq it })
                }

            }
        }
        return FacetFilterContext(name, filterExpr, selectedValues = values)
    }

    override fun apply(
        searchQuery: SearchQuery<*>,
        filterCtx: FilterContext,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        val ctx = filterCtx.cast<FacetFilterContext>()

        val termsAgg = aggFactory(field)
        val aggs = if (otherFacetFilterExpressions.isNotEmpty()) {
            val wrapFilters = if (mode == FacetFilterMode.INTERSECT && ctx.facetFilterExpr != null) {
                otherFacetFilterExpressions + listOf(ctx.facetFilterExpr)
            } else {
                otherFacetFilterExpressions
            }
            mapOf(
                filterAggName(ctx.name) to FilterAgg(
                    Bool(filter = wrapFilters),
                    aggs = mapOf(termsAggName(ctx.name) to termsAgg)
                )
            )
        } else {
            mapOf(termsAggName(ctx.name) to termsAgg)
        }
        searchQuery.aggs(aggs)
    }

    override fun processResult(
        searchQueryResult: SearchQueryResult<*>,
        filterCtx: FilterContext
    ): FacetFilterResult<T> {
        val ctx = filterCtx.cast<FacetFilterContext>()

        val selectedValues = OrderedMap(
            *ctx.selectedValues
                .map { it to true }
                .toTypedArray()
        )
        val isSelected = !selectedValues.isEmpty()
        val values = mutableListOf<FacetFilterValue<T>>()
        for (bucket in getTermsAggResult(ctx.name, searchQueryResult).buckets) {
            values.add(
                FacetFilterValue(
                    bucket.key, bucket.docCount, selectedValues.containsKey(bucket.key)
                )
            )
            selectedValues.remove(bucket.key)
        }

        for (selectedValue in selectedValues.keys) {
            if (selectedValue == null) {
                continue
            }
            values.add(
                FacetFilterValue(field.deserializeTerm(selectedValue), null, true)
            )
        }
        return FacetFilterResult(ctx.name, mode, values, isSelected)
    }

    private fun getTermsAggResult(
        name: String, searchQueryResult: SearchQueryResult<*>
    ): TermsAggResult<T> {
        var aggResult: AggAwareResult = searchQueryResult
        if (aggResult.aggs.containsKey(filterAggName(name))) {
            aggResult = aggResult.agg<SingleBucketAggResult>(filterAggName(name))
        }
        return aggResult.agg(termsAggName(name))
    }
}

/**
 * [FacetFilterContext] is a temporary storage of a [FacetFilter] state.
 */
class FacetFilterContext(
    name: String,
    facetFilterExpr: QueryExpression?,
    val selectedValues: List<Any?>
) : FilterContext(name, facetFilterExpr)

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
    val mode: FacetFilterMode,
    val values: List<FacetFilterValue<T>>,
    val selected: Boolean,
) : FilterResult, Iterable<FacetFilterValue<T>> {
    override fun iterator(): Iterator<FacetFilterValue<T>> {
        return values.iterator()
    }
}

/**
 * [FacetFilterValue] represents bucket of the corresponding terms aggregation.
 *
 * @param value - value of the [FacetFilter]
 * @param count - number of the documents that have such a value
 * @param selected - flag whether the value is selected
 */
data class FacetFilterValue<T>(
    val value: T,
    val count: Long?,
    val selected: Boolean,
)
