package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

data class DisMax(
    val queries: List<QueryExpression>,
    val tieBreaker: Float? = null,
) : QueryExpression {
    override val name = "dis_max"

    override fun clone() = copy()

    override fun children(): Iterator<QueryExpression> = iterator {
        yieldAll(queries)
    }

    override fun rewrite(newNode: QueryExpressionNode<*>): DisMax {
        replaceNodeInExpressions(queries, { it.rewrite(newNode) }) {
            return copy(queries = it)
        }
        return this
    }

    override fun reduce(): QueryExpression? {
        return when {
            queries.isEmpty() -> null
            queries.size == 1 -> queries[0]
            else -> this
        }
    }

    override fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
        ctx.array("queries") {
            compiler.visit(this, queries)
        }
        ctx.fieldIfNotNull("tie_breaker", tieBreaker)
    }
}
