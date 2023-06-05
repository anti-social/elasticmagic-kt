package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.ToValue
import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

data class FunctionScore(
    val query: QueryExpression? = null,
    val functions: List<Function>,
    val boost: Float? = null,
    val scoreMode: ScoreMode? = null,
    val boostMode: BoostMode? = null,
    val minScore: Float? = null,
) : QueryExpression {
    override val name = "function_score"

    enum class ScoreMode : ToValue<String> {
        MULTIPLY, SUM, AVG, FIRST, MAX, MIN;

        override fun toValue() = name.lowercase()
    }
    enum class BoostMode : ToValue<String> {
        MULTIPLY, REPLACE, SUM, AVG, MAX, MIN;

        override fun toValue() = name.lowercase()
    }

    override fun clone() = copy()

    override fun children(): Iterator<Expression<*>> = iterator {
        if (query != null) {
            yield(query)
        }
        yieldAll(functions)
    }

    override fun rewrite(newNode: QueryExpressionNode<*>): FunctionScore {
        if (query != null) {
            val newQuery = query.rewrite(newNode)
            if (newQuery != query) {
                return copy(query = newQuery)
            }
        }
        replaceNodeInExpressions(functions, { it.rewrite(newNode) }) {
            return copy(functions = it)
        }
        return this
    }

    override fun reduce(): QueryExpression? {
        val reducedQuery = query?.reduce()
        if (functions.isEmpty() && minScore == null) {
            return reducedQuery
        }
        val reducedFunctions = ArrayList<Function>(functions.size)
        var hasReducedFunctions = false
        for (fn in functions) {
            val reducedFn = fn.reduce()
            reducedFunctions.add(reducedFn)
            if (reducedFn !== fn) {
                hasReducedFunctions = true
            }
        }
        if (reducedQuery != query || hasReducedFunctions) {
            return copy(query = reducedQuery, functions = reducedFunctions)
        }
        return this
    }

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: BaseSearchQueryCompiler
    ) {
        if (query != null) {
            ctx.obj("query") {
                compiler.visit(this, query)
            }
        }
        ctx.fieldIfNotNull("boost", boost)
        ctx.fieldIfNotNull("score_mode", scoreMode?.toValue())
        ctx.fieldIfNotNull("boost_mode", boostMode?.toValue())
        ctx.fieldIfNotNull("min_score", minScore)
        ctx.array("functions") {
            compiler.visit(this, functions)
        }
    }

    abstract class Function : ObjExpression {
        abstract val filter: QueryExpression?

        override fun children(): Iterator<Expression<*>>? {
            val filter = filter
            if (filter != null) {
                return iterator { yield(filter) }
            }
            return null
        }

        override fun rewrite(newNode: QueryExpressionNode<*>): Function {
            val newFilter = filter?.rewrite(newNode)
            if (newFilter != filter) {
                return copyWithFilter(newFilter)
            }
            return this
        }

        override fun reduce(): Function {
            val reducedFilter = filter?.reduce()
            if (reducedFilter !== filter) {
                return copyWithFilter(reducedFilter)
            }
            return this
        }

        protected abstract fun copyWithFilter(filter: QueryExpression?): Function

        protected inline fun accept(
            ctx: Serializer.ObjectCtx,
            compiler: BaseSearchQueryCompiler,
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
        val weight: Float,
        override val filter: QueryExpression? = null,
    ) : Function() {
        override fun clone() = copy()

        override fun copyWithFilter(filter: QueryExpression?) = copy(filter = filter)

        override fun accept(
            ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler
        ) = accept(ctx, compiler) {
            field("weight", weight)
        }
    }

    data class FieldValueFactor<T: Number>(
        val field: FieldOperations<T>,
        val factor: Float? = null,
        val missing: T? = null,
        val modifier: Modifier? = null,
        override val filter: QueryExpression? = null,
    ) : Function() {
        override fun clone() = copy()

        override fun copyWithFilter(filter: QueryExpression?) = copy(filter = filter)

        override fun accept(
            ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler
        ) = accept(ctx, compiler) {
            obj("field_value_factor") {
                field("field", field.getQualifiedFieldName())
                fieldIfNotNull("factor", factor)
                missing?.let { missing ->
                    field("missing", field.serializeTerm(missing))
                }
                fieldIfNotNull("modifier", modifier?.toValue())
            }
        }

        enum class Modifier : ToValue<String> {
            LOG, LOG1P, LOG2P, LN, LN1P, LN2P, SQUARE, SQRT, RECIPROCAL;

            override fun toValue() = name.lowercase()
        }
    }

    data class ScriptScore(
        val script: Script,
        override val filter: QueryExpression? = null,
    ) : Function() {
        override fun clone() = copy()

        override fun copyWithFilter(filter: QueryExpression?) = copy(filter = filter)

        override fun accept(
            ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler
        ) = accept(ctx, compiler) {
            obj("script_score") {
                obj("script") {
                    compiler.visit(this, script)
                }
            }
        }
    }

    data class RandomScore(
        val seed: Any? = null,
        val field: FieldOperations<*>? = null,
        override val filter: QueryExpression? = null,
    ) : Function() {
        override fun clone() = copy()

        override fun copyWithFilter(filter: QueryExpression?) = copy(filter = filter)

        override fun accept(
            ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler
        ) = accept(ctx, compiler) {
            obj("random_score") {
                fieldIfNotNull("seed", seed)
                fieldIfNotNull("field", field?.getQualifiedFieldName())
            }
        }
    }
}
