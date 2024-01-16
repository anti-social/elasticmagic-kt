package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.QueryExpression

fun getFacetFilterExpr(expression: List<QueryExpression>, mode: FilterMode) = when (expression.size) {
    0 -> null
    else -> {
        when (mode) {
            FilterMode.UNION -> maybeWrapBool(Bool::should, expression)
            FilterMode.INTERSECT -> maybeWrapBool(Bool::must, expression)
        }
    }
}

