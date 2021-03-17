package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

interface Expression : SearchQueryCompiler.Visitable {
    val name: String

    fun children(): Iterator<Expression>? {
        return null
    }

    fun reduce(): Expression? {
        return this
    }
}

internal inline fun Expression.collect(process: (Expression) -> Unit) {
    val stack = ArrayList<Expression>()
    stack.add(this)

    while (true) {
        val currentExpression = stack.removeLastOrNull() ?: break
        process(currentExpression)

        val children = currentExpression.children()
        if (children != null) {
            for (child in children) {
                stack.add(child)
            }
        }
    }
}

interface QueryExpression : Expression {
    override fun reduce(): QueryExpression? {
        return this
    }
}

@Suppress("UNUSED")
data class NodeHandle<T: QueryExpressionNode<T>>(val name: String)

abstract class QueryExpressionNode<T: QueryExpressionNode<T>>(
    val handle: NodeHandle<T>,
) : QueryExpression {
    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        toQueryExpression().accept(ctx, compiler)
    }

    override fun reduce(): QueryExpression? {
        return toQueryExpression().reduce()
    }

    abstract fun toQueryExpression(): QueryExpression
}

data class Term(
    val field: FieldOperations,
    val term: Any,
    val boost: Double? = null,
) : QueryExpression {
    override val name = "term"

    override fun accept(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.obj(name) {
            if (boost != null) {
                obj(field.getQualifiedFieldName()) {
                    field("value", term)
                    field("boost", boost)
                }
            } else {
                field(field.getQualifiedFieldName(), term)
            }
        }
    }
}

data class Exists(
    val field: FieldOperations,
) : QueryExpression {
    override val name = "exists"

    override fun accept(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.obj(name) {
            field("field", field.getQualifiedFieldName())
        }
    }
}

data class Range(
    val field: FieldOperations,
    val gt: Any? = null,
    val gte: Any? = null,
    val lt: Any? = null,
    val lte: Any? = null,
) : QueryExpression {
    override val name = "range"

    override fun accept(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.obj(name) {
            obj(field.getQualifiedFieldName()) {
                if (gt != null) {
                    field("gt", gt)
                }
                if (gte != null) {
                    field("gte", gte)
                }
                if (lt != null) {
                    field("lt", lt)
                }
                if (lte != null) {
                    field("lte", lte)
                }
            }
        }
    }
}

data class Match(
    val field: FieldOperations,
    val query: String,
    val analyzer: String? = null,
    val minimumShouldMatch: Any? = null,
    val params: Params? = null,
) : QueryExpression {
    override val name = "match"

    override fun accept(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        val params = Params(
            params,
            "analyzer" to analyzer,
            "minimum_should_match" to minimumShouldMatch,
        )
        ctx.obj(name) {
            if (params.isEmpty()) {
                field(field.getQualifiedFieldName(), query)
            } else {
                obj(field.getQualifiedFieldName()) {
                    compiler.visit(this, params)
                }
            }
        }
    }
}

class MatchAll : QueryExpression {
    override val name = "match_all"

    override fun accept(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.obj(name) {}
    }
}

data class MultiMatch(
    val query: String,
    val fields: List<FieldOperations>,
    val type: Type? = null,
    val boost: Double? = null,
    val params: Params? = null,
) : QueryExpression {
    enum class Type {
        BEST_FIELDS, MOST_FIELDS, CROSS_FIELDS, PHRASE, PHRASE_PREFIX;
    }

    override val name = "multi_match"

    override fun accept(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        val params = Params(
            params,
            "query" to query,
            "fields" to fields.map { field -> field.getQualifiedFieldName() },
            "type" to type?.name?.toLowerCase(),
            "boost" to boost,
        )
        ctx.obj(name) {
            compiler.visit(this, params)
        }
    }
}

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
                copy(
                    filter = filter,
                    should = should,
                    must = must,
                    mustNot = mustNot,
                )
            }
        }
    }

    override fun accept(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.obj(name) {
            if (minimumShouldMatch != null) {
                field("minimum_should_match", minimumShouldMatch)
            }
            if (!filter.isNullOrEmpty()) {
                array("filter") {
                    compiler.visit(this, filter)
                }
            }
            if (!should.isNullOrEmpty()) {
                array("should") {
                    compiler.visit(this, should)
                }
            }
            if (!must.isNullOrEmpty()) {
                array("must") {
                    compiler.visit(this, must)
                }
            }
            if (!mustNot.isNullOrEmpty()) {
                array("must_not") {
                    compiler.visit(this, mustNot)
                }
            }
        }
    }
}

class BoolNode(
    handle: NodeHandle<BoolNode>,
    filter: List<QueryExpression> = emptyList(),
    should: List<QueryExpression> = emptyList(),
    must: List<QueryExpression> = emptyList(),
    mustNot: List<QueryExpression> = emptyList(),
    private val minimumShouldMatch: Any? = null,
) : QueryExpressionNode<BoolNode>(handle), BoolExpression {
    override val name: String = "bool"

    override var filter: MutableList<QueryExpression> = filter.toMutableList()
    override var should: MutableList<QueryExpression> = should.toMutableList()
    override var must: MutableList<QueryExpression> = must.toMutableList()
    override var mustNot: MutableList<QueryExpression> = mustNot.toMutableList()

    override fun toQueryExpression(): QueryExpression {
        return Bool(
            filter = filter,
            should = should,
            must = must,
            mustNot = mustNot,
            minimumShouldMatch = minimumShouldMatch,
        )
    }
}

interface FunctionScoreExpression : QueryExpression{
    val functions: List<FunctionScore.Function>

    override fun children(): Iterator<Expression> = iterator {
        yieldAll(functions)
    }
}

data class FunctionScore(
    val query: QueryExpression?,
    val boost: Double? = null,
    val scoreMode: ScoreMode? = null,
    val boostMode: BoostMode? = null,
    val minScore: Double? = null,
    override val functions: List<Function>,
) : FunctionScoreExpression {
    enum class ScoreMode {
        MULTIPLY, SUM, AVG, FIRST, MAX, MIN
    }
    enum class BoostMode {
        MULTIPLY, REPLACE, SUM, AVG, MAX, MIN
    }

    override val name = "function_score"

    abstract class Function : Expression {
        abstract val filter: QueryExpression?

        override fun children(): Iterator<Expression>? {
            val filter = filter
            if (filter != null) {
                return iterator { yield(filter) }
            }
            return null
        }

        fun reduceFilter(): QueryExpression? {
            return filter?.reduce()
        }

        protected inline fun accept(
            ctx: Serializer.ObjectCtx,
            compiler: SearchQueryCompiler,
            block: Serializer.ObjectCtx.() -> Unit
        ) {
            val fn = filter
            if (fn != null) {
                ctx.obj("filter") {
                    compiler.visit(this, fn)
                }
            }
            ctx.block()
        }
    }

    data class Weight(
        val weight: Double,
        override val filter: QueryExpression?,
    ) : Function() {
        override val name = "weight"

        override fun reduce(): Expression? {
            return copy(
                filter = reduceFilter()
            )
        }

        override fun accept(
            ctx: Serializer.ObjectCtx,
            compiler: SearchQueryCompiler
        ) {
            super.accept(ctx, compiler) {
                field(name, weight)
            }
        }
    }

    data class FieldValueFactor(
        val field: FieldOperations,
        val factor: Double? = null,
        val missing: Double? = null,
        override val filter: QueryExpression? = null,
    ) : Function() {
        override val name = "field_value_factor"

        override fun reduce(): Expression? {
            return copy(
                filter = reduceFilter()
            )
        }

        override fun accept(
            ctx: Serializer.ObjectCtx, compiler:
            SearchQueryCompiler
        ) {
            super.accept(ctx, compiler) {
                ctx.obj(name) {
                    field("field", field.getQualifiedFieldName())
                    if (factor != null) {
                        field("factor", factor)
                    }
                    if (missing != null) {
                        field("missing", missing)
                    }
                }
            }
        }
    }

    override fun reduce(): QueryExpression? {
        val query = query?.reduce()
        if (functions.isEmpty() && minScore == null) {
            return query?.reduce()
        }
        return this
    }

    override fun accept(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.obj(name) {
            if (query != null) {
                obj("query") {
                    compiler.visit(this, query)
                }
            }
            if (boost != null) {
                field("boost", boost)
            }
            if (scoreMode != null) {
                field("score_mode", scoreMode.name.toLowerCase())
            }
            if (boostMode != null) {
                field("boost_mode", boostMode.name.toLowerCase())
            }
            array("functions") {
                compiler.visit(this, functions)
            }
        }
    }
}

class FunctionScoreNode(
    handle: NodeHandle<FunctionScoreNode>,
    var query: QueryExpression?,
    var boost: Double? = null,
    var scoreMode: FunctionScore.ScoreMode? = null,
    var boostMode: FunctionScore.BoostMode? = null,
    var minScore: Double? = null,
    functions: List<FunctionScore.Function> = emptyList(),
) : QueryExpressionNode<FunctionScoreNode>(handle), FunctionScoreExpression {
    override val name: String = "function_score"

    override var functions: MutableList<FunctionScore.Function> = functions.toMutableList()

    override fun toQueryExpression(): QueryExpression {
        return FunctionScore(
            query = query,
            boost = boost,
            scoreMode = scoreMode,
            boostMode = boostMode,
            minScore = minScore,
            functions = functions,
        )
    }
}
