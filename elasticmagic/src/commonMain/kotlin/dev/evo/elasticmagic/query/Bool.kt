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
    val minimumShouldMatch: MinimumShouldMatch? = null,
    val boost: Float? = null,
) : BoolExpression {
    override val name = "bool"

    companion object {
        fun filter(expressions: List<QueryExpression>) = Bool(filter = expressions)
        fun filter(vararg expressions: QueryExpression) = filter(expressions.toList())
        fun should(expressions: List<QueryExpression>) = Bool(should = expressions)
        fun should(vararg expressions: QueryExpression) = should(expressions.toList())
        fun must(expressions: List<QueryExpression>) = Bool(must = expressions)
        fun must(vararg expressions: QueryExpression) = must(expressions.toList())
        fun mustNot(expressions: List<QueryExpression>) = Bool(mustNot = expressions)
        fun mustNot(vararg expressions: QueryExpression) = mustNot(expressions.toList())
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
        if (minimumShouldMatch != null) {
            ctx.field("minimum_should_match", minimumShouldMatch.toValue())
        }
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
    var minimumShouldMatch: MinimumShouldMatch? = null,
) : QueryExpressionNode<BoolNode>(), BoolExpression {
    override val name: String = "bool"

    companion object {
        operator fun invoke(
            handle: NodeHandle<BoolNode>,
            filter: List<QueryExpression> = emptyList(),
            should: List<QueryExpression> = emptyList(),
            must: List<QueryExpression> = emptyList(),
            mustNot: List<QueryExpression> = emptyList(),
            minimumShouldMatch: MinimumShouldMatch? = null,
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
