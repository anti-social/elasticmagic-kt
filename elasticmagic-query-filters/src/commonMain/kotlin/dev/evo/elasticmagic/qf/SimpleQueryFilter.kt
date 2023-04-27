package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.QueryExpression

open class SimpleQueryFilter(
    name: String? = null,
    val values: List<SimpleQueryValue> = emptyList(),
    private val mode: FilterMode = FilterMode.UNION,
) : Filter<BaseFilterResult>(name) {

    override fun prepare(name: String, paramName: String, params: QueryFilterParams): PreparedSimpleFilter {
        val filterValues = params.getOrElse(listOf(paramName)) { null } ?: return PreparedSimpleFilter(
            name,
            paramName,
            null
        )

        val expression = mutableListOf<QueryExpression>()

        filterValues.map { value ->
            values.firstOrNull { it.name == value }?.let {
                expression.add(it.expr)
            }

        }
        val filterExpr = when (expression.size) {
            0 -> null
            else -> {
                when (mode) {
                    FilterMode.UNION -> maybeWrapBool(Bool::should, expression)
                    FilterMode.INTERSECT -> maybeWrapBool(Bool::must, expression)
                }
            }
        }

        return PreparedSimpleFilter(
            name,
            paramName,
            filterExpr,
        )
    }
}
