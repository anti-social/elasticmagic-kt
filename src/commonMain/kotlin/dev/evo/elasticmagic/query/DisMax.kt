package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

interface DisMaxExpression : QueryExpression {
    val queries: List<QueryExpression>

    override fun children(): Iterator<QueryExpression> = iterator {
        yieldAll(queries)
    }
}

data class DisMax(
    override val queries: List<QueryExpression>,
    val tieBreaker: Double? = null,
) : DisMaxExpression {
    override val name = "dis_max"

    override fun clone() = copy()

    override fun reduce(): QueryExpression? {
        return when {
            queries.isEmpty() -> null
            queries.size == 1 -> queries[0]
            else -> this
        }
    }

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.array("queries") {
            compiler.visit(this, queries)
        }
        ctx.fieldIfNotNull("tie_breaker", tieBreaker)
    }
}

data class DisMaxNode(
    override val handle: NodeHandle<DisMaxNode>,
    override var queries: MutableList<QueryExpression>,
    var tieBreaker: Double? = null,
) : QueryExpressionNode<DisMaxNode>(), DisMaxExpression {
    override val name = "dis_max"

    companion object {
        operator fun invoke(
            handle: NodeHandle<DisMaxNode>,
            queries: List<QueryExpression> = emptyList(),
            tieBreaker: Double? = null,
        ): DisMaxNode {
            return DisMaxNode(
                handle,
                queries = queries.toMutableList(),
                tieBreaker = tieBreaker,
            )
        }
    }

    override fun clone() = copy(queries = queries.toMutableList())

    override fun toQueryExpression(): DisMax {
        return DisMax(
            queries = queries,
            tieBreaker = tieBreaker
        )
    }
}
