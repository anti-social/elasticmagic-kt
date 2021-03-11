package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

interface Expression : SearchQueryCompiler.Visitable {
    val name: String
}

interface QueryExpression : Expression

data class Term(
    val field: FieldOperations,
    val term: Any,
    val boost: Double? = null,
) : QueryExpression {
    override val name = "term"

    override fun accept(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler<*>
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
        compiler: SearchQueryCompiler<*>
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
        compiler: SearchQueryCompiler<*>
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
        compiler: SearchQueryCompiler<*>
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
        compiler: SearchQueryCompiler<*>
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
        compiler: SearchQueryCompiler<*>
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

data class Bool(
    val filter: List<QueryExpression>? = null,
    val should: List<QueryExpression>? = null,
    val must: List<QueryExpression>? = null,
    val mustNot: List<QueryExpression>? = null,
) : QueryExpression {
    override val name = "bool"

    companion object {
        fun filter(vararg exprs: QueryExpression) = Bool(filter = exprs.toList())
        fun should(vararg exprs: QueryExpression) = Bool(should = exprs.toList())
        fun must(vararg exprs: QueryExpression) = Bool(must = exprs.toList())
        fun mustNot(vararg exprs: QueryExpression) = Bool(mustNot = exprs.toList())
    }

    override fun accept(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler<*>
    ) {
        ctx.obj(name) {
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

data class FunctionScore(
    val query: QueryExpression?,
    val boost: Double? = null,
    val scoreMode: ScoreMode? = null,
    val boostMode: BoostMode? = null,
    val functions: List<Function>,
) : QueryExpression {
    enum class ScoreMode {
        MULTIPLY, SUM, AVG, FIRST, MAX, MIN
    }
    enum class BoostMode {
        MULTIPLY, REPLACE, SUM, AVG, MAX, MIN
    }

    override val name = "function_score"

    abstract class Function : Expression {
        abstract val filter: QueryExpression?

        protected inline fun accept(
            ctx: Serializer.ObjectCtx,
            compiler: SearchQueryCompiler<*>,
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

        override fun accept(
            ctx: Serializer.ObjectCtx,
            compiler: SearchQueryCompiler<*>
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

        override fun accept(
            ctx: Serializer.ObjectCtx, compiler:
            SearchQueryCompiler<*>
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

    override fun accept(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler<*>
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
