package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.query.Sort
import dev.evo.elasticmagic.types.KeywordType

/**
 * [SortFilter] applies a sorting to a search query.
 *
 * @param sortValues - values to sort a search query with
 * @param name - optional filter name. If omitted, name of a property will be used
 */
class SortFilter(
    vararg sortValues: SortFilterValue,
    name: String? = null,
) : Filter<PreparedSortFilter, SortFilterResult>(name) {
    init {
        if (sortValues.isEmpty()) {
            throw IllegalArgumentException("Expected at least one sort value")
        }
    }

    companion object {
        /**
         * Shortcut that creates a [SortFilter] from pairs of name and list of sorts.
         */
        operator fun invoke(vararg sortValues: Pair<String, List<Sort>>, name: String? = null): SortFilter {
            return SortFilter(
                *sortValues.map { (name, sorts) -> SortFilterValue(name, sorts) }.toTypedArray(),
                name = name
            )
        }
    }

    private val valuesByName = sortValues.associateBy(SortFilterValue::value)

    val values = sortValues.toList()

    /**
     * Parses [params] and prepares the [SortFilter] for applying.
     *
     * @param name - name of the [SortFilter]
     * @param params - parameters that should be applied to a search query.
     *   Examples:
     *   - `mapOf(("sort" to "") to listOf("price"))`
     */
    override fun prepare(name: String, params: QueryFilterParams): PreparedSortFilter {
        val selectedValue = params.decodeLastValue(name to "", KeywordType)
            ?.let { valuesByName[it] }
            ?: values[0]
        return PreparedSortFilter(this, name, selectedValue)
    }
}

/**
 * [SortFilterValue] holds a value of a sort and a list of [Sort] that should be applied when
 * the value is selected.
 */
class SortFilterValue(
    val value: String,
    val sorts: List<Sort>
)

/**
 * Filter that is ready for applying to a search query.
 */
class PreparedSortFilter(
    val filter: SortFilter,
    name: String,
    val selectedValue: SortFilterValue,
) : PreparedFilter<SortFilterResult>(name, null) {
    override fun apply(
        searchQuery: SearchQuery<*>,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        searchQuery.sort(selectedValue.sorts)
    }

    override fun processResult(
        searchQueryResult: SearchQueryResult<*>,
    ): SortFilterResult {
        return SortFilterResult(
            name,
            values = filter.values.map { SortFilterValueResult(it.value, it == selectedValue) }
        )
    }
}

/**
 * Result of the [SortFilter].
 *
 * @param name - name of the [SortFilter]
 * @param values - values result
 */
data class SortFilterResult(
    override val name: String,
    val values: List<SortFilterValueResult>,
) : FilterResult

/**
 * Represents result of the [SortFilterValue].
 *
 * @param value - value of the corresponding [SortFilterValue]
 * @param selected - whether the value was selected
 */
data class SortFilterValueResult(
    val value: String,
    val selected: Boolean,
)
