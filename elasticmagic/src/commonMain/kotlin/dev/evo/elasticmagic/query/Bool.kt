package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

data class Bool(
    val filter: List<QueryExpression> = emptyList(),
    val should: List<QueryExpression> = emptyList(),
    val must: List<QueryExpression> = emptyList(),
    val mustNot: List<QueryExpression> = emptyList(),
    val minimumShouldMatch: MinimumShouldMatch? = null,
    val boost: Float? = null,
) : QueryExpression {
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

    override fun children(): Iterator<QueryExpression> = iterator {
        yieldAll(filter)
        yieldAll(should)
        yieldAll(must)
        yieldAll(mustNot)
    }

    override fun rewrite(newNode: QueryExpressionNode<*>): Bool {
        replaceNodeInExpressions(filter, { it.rewrite(newNode) }) {
            return copy(filter = it)
        }
        replaceNodeInExpressions(should, { it.rewrite(newNode) }) {
            return copy(should = it)
        }
        replaceNodeInExpressions(must, { it.rewrite(newNode) }) {
            return copy(must = it)
        }
        replaceNodeInExpressions(mustNot, { it.rewrite(newNode) }) {
            return copy(mustNot = it)
        }
        return this
    }
    
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
