package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

interface BoolExpression : QueryExpression {
    val filter: List<QueryExpression>
    val should: List<QueryExpression>
    val must: List<QueryExpression>
    val mustNot: List<QueryExpression>

    override fun children(): Iterator<QueryExpression> = iterator {
        yieldAll(filter)
        yieldAll(should)
        yieldAll(must)
        yieldAll(mustNot)
    }
}

data class Bool(
    override val filter: List<QueryExpression> = emptyList(),
    override val should: List<QueryExpression> = emptyList(),
    override val must: List<QueryExpression> = emptyList(),
    override val mustNot: List<QueryExpression> = emptyList(),
    val minimumShouldMatch: Any? = null,
) : BoolExpression {
    override val name = "bool"

    companion object {
        fun filter(vararg expressions: QueryExpression) = Bool(filter = expressions.toList())
        fun should(vararg expressions: QueryExpression) = Bool(should = expressions.toList())
        fun must(vararg expressions: QueryExpression) = Bool(must = expressions.toList())
        fun mustNot(vararg expressions: QueryExpression) = Bool(mustNot = expressions.toList())
    }

    override fun clone() = copy()

    override fun reduce(): QueryExpression? {
        val filter = filter.mapNotNull { it.reduce() }
        val should = should.mapNotNull { it.reduce() }
        val must = must.mapNotNull { it.reduce() }
        val mustNot = mustNot.mapNotNull { it.reduce() }
        return when {
            filter.isEmpty() && should.isEmpty() && must.isEmpty() && mustNot.isEmpty() -> {
                null
            }
            filter.isEmpty() && should.size == 1 && must.isEmpty() && mustNot.isEmpty() -> {
                should[0]
            }
            filter.isEmpty() && should.isEmpty() && must.size == 1 && mustNot.isEmpty() -> {
                must[0]
            }
            else -> {
                Bool(
                    filter = filter,
                    should = should,
                    must = must,
                    mustNot = mustNot,
                    minimumShouldMatch = minimumShouldMatch,
                )
            }
        }
    }

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.fieldIfNotNull("minimum_should_match", minimumShouldMatch)
        if (filter.isNotEmpty()) {
            ctx.array("filter") {
                compiler.visit(this, filter)
            }
        }
        if (should.isNotEmpty()) {
            ctx.array("should") {
                compiler.visit(this, should)
            }
        }
        if (must.isNotEmpty()) {
            ctx.array("must") {
                compiler.visit(this, must)
            }
        }
        if (mustNot.isNotEmpty()) {
            ctx.array("must_not") {
                compiler.visit(this, mustNot)
            }
        }
    }
}

data class BoolNode(
    override val handle: NodeHandle<BoolNode>,
    override var filter: MutableList<QueryExpression>,
    override var should: MutableList<QueryExpression>,
    override var must: MutableList<QueryExpression>,
    override var mustNot: MutableList<QueryExpression>,
    var minimumShouldMatch: Any? = null,
) : QueryExpressionNode<BoolNode>(), BoolExpression {
    override val name: String = "bool"

    companion object {
        operator fun invoke(
            handle: NodeHandle<BoolNode>,
            filter: List<QueryExpression> = emptyList(),
            should: List<QueryExpression> = emptyList(),
            must: List<QueryExpression> = emptyList(),
            mustNot: List<QueryExpression> = emptyList(),
            minimumShouldMatch: Any? = null,
        ): BoolNode {
            return BoolNode(
                handle,
                filter = filter.toMutableList(),
                should = should.toMutableList(),
                must = must.toMutableList(),
                mustNot = mustNot.toMutableList(),
                minimumShouldMatch = minimumShouldMatch,
            )
        }
    }

    override fun clone() = copy(
        filter = filter.toMutableList(),
        should = should.toMutableList(),
        must = must.toMutableList(),
        mustNot = mustNot.toMutableList()
    )

    override fun toQueryExpression(): Bool {
        return Bool(
            filter = filter,
            should = should,
            must = must,
            mustNot = mustNot,
            minimumShouldMatch = minimumShouldMatch,
        )
    }
}
