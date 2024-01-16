package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.query.QueryExpression

class ExpressionValue(val name: String, val expr: QueryExpression)

open class SimpleExpressionsFilter(
    name: String? = null,
    val values: List<ExpressionValue> = emptyList(),
    private val mode: FilterMode = FilterMode.UNION,
) : Filter<BaseFilterResult>(name) {

    override fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedSimpleFilter {
        val filterValues = params.getOrElse(listOf(paramName)) { null } ?: return PreparedSimpleFilter(
            name,
            paramName,
            null
        )

        val expression = filterValues.mapNotNull { value ->
            values.find { it.name == value }?.expr
        }

        val filterExpr = getFacetFilterExpr(expression, mode)

        return PreparedSimpleFilter(
            name,
            paramName,
            filterExpr,
        )
    }
}
