package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.query.Sort
import dev.evo.elasticmagic.types.KeywordType

class SortFilter(
    private vararg val sortValues: SortFilterValue,
    name: String? = null,
) : Filter<SortFilterContext, SortFilterResult>(name) {
    init {
        require(sortValues.isNotEmpty()) {
            "Expected at least one sort value"
        }
    }

    private val sorts = sortValues.associateBy(SortFilterValue::value)

    val values = sortValues.toList()

    override fun prepareContext(name: String, params: QueryFilterParams): SortFilterContext {
        val selectedValue = params.decodeLastValue(name to "", KeywordType)
            ?.let { sorts[it] }
            ?: sortValues[0]
        return SortFilterContext(name, selectedValue)
    }

    override fun apply(
        searchQuery: SearchQuery<*>,
        filterCtx: FilterContext,
        otherFacetFilterExpressions: List<QueryExpression>
    ) {
        val ctx = filterCtx.cast<SortFilterContext>()
        searchQuery.sort(*ctx.selectedValue.sorts.toTypedArray())
    }

    override fun processResult(
        searchQueryResult: SearchQueryResult<*>,
        filterCtx: FilterContext
    ): SortFilterResult {
        val ctx = filterCtx.cast<SortFilterContext>()
        return SortFilterResult(
            ctx.name,
            values = sortValues.map { SortFilterValueResult(it.value, it == ctx.selectedValue) }
        )
    }
}

class SortFilterValue(
    val value: String,
    val sorts: List<Sort>
)

class SortFilterContext(
    name: String,
    val selectedValue: SortFilterValue,
) : FilterContext(name, null)

data class SortFilterResult(
    override val name: String,
    val values: List<SortFilterValueResult>,
) : FilterResult

data class SortFilterValueResult(
    val value: String,
    val selected: Boolean,
)
