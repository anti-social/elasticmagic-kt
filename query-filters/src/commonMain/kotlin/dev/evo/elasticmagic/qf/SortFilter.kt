package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.query.Sort
import dev.evo.elasticmagic.types.KeywordType

class SortFilter(
    vararg val sortValues: SortFilterValue,
    name: String? = null,
) : Filter<SortFilterContext, SortFilterResult>(name) {
    init {
        if (sortValues.isEmpty()) {
            throw IllegalArgumentException("Expected at least one sort value")
        }
    }

    companion object {
        operator fun invoke(vararg sortValues: Pair<String, List<Sort>>): SortFilter {
            return SortFilter(
                *sortValues.map { (name, sorts) -> SortFilterValue(name, sorts) }.toTypedArray()
            )
        }
    }

    private val sorts = sortValues.associateBy(SortFilterValue::value)

    val values = sortValues.toList()

    override fun prepare(name: String, params: QueryFilterParams): SortFilterContext {
        val selectedValue = params.decodeLastValue(name to "", KeywordType)
            ?.let { sorts[it] }
            ?: sortValues[0]
        return SortFilterContext(this, name, selectedValue)
    }
}

class SortFilterValue(
    val value: String,
    val sorts: List<Sort>
)

class SortFilterContext(
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
            values = filter.sortValues.map { SortFilterValueResult(it.value, it == selectedValue) }
        )
    }
}

data class SortFilterResult(
    override val name: String,
    val values: List<SortFilterValueResult>,
) : FilterResult

data class SortFilterValueResult(
    val value: String,
    val selected: Boolean,
)
