package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

interface FunctionScoreExpression : QueryExpression {
    val functions: List<FunctionScore.Function>

    override fun children(): Iterator<Expression<*>> = iterator {
        yieldAll(functions)
    }
}

data class FunctionScore(
    val query: QueryExpression? = null,
    override val functions: List<Function>,
    val boost: Double? = null,
    val scoreMode: ScoreMode? = null,
    val boostMode: BoostMode? = null,
    val minScore: Double? = null,
) : FunctionScoreExpression {
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

    override fun reduce(): QueryExpression? {
        if (functions.isEmpty() && minScore == null) {
            return query?.reduce()
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
        if (hasReducedFunctions) {
            return copy(functions = reducedFunctions)
        }
        return this
    }

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
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
        override val filter: QueryExpression? = null,
    ) : Function() {
        override fun clone() = copy()

        override fun copyWithFilter(filter: QueryExpression?) = copy(filter = filter)

        override fun accept(
            ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler
        ) = accept(ctx, compiler) {
            field("weight", weight)
        }
    }

    data class FieldValueFactor<T> private constructor(
        val field: FieldOperations<T>,
        val factor: Double? = null,
        val missing: T? = null,
        val modifier: Modifier? = null,
        override val filter: QueryExpression? = null,
    ) : Function() {
        companion object {
            operator fun <T: Number> invoke(
                field: FieldOperations<T>,
                factor: Double? = null,
                missing: T? = null,
                modifier: Modifier? = null,
                filter: QueryExpression? = null,
            ) = FieldValueFactor(
                field,
                factor = factor,
                missing = missing,
                modifier = modifier,
                filter = filter,
            )
        }

        override fun clone() = copy()

        override fun copyWithFilter(filter: QueryExpression?) = copy(filter = filter)

        override fun accept(
            ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler
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
            ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler
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
            ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler
        ) = accept(ctx, compiler) {
            obj("random_score") {
                fieldIfNotNull("seed", seed)
                fieldIfNotNull("field", field?.getQualifiedFieldName())
            }
        }
    }
}

data class FunctionScoreNode(
    override val handle: NodeHandle<FunctionScoreNode>,
    var query: QueryExpression?,
    var boost: Double? = null,
    var scoreMode: FunctionScore.ScoreMode? = null,
    var boostMode: FunctionScore.BoostMode? = null,
    var minScore: Double? = null,
    override var functions: MutableList<FunctionScore.Function>,
) : QueryExpressionNode<FunctionScoreNode>(), FunctionScoreExpression {
    override val name: String = "function_score"

    companion object {
        operator fun invoke(
            handle: NodeHandle<FunctionScoreNode>,
            query: QueryExpression?,
            boost: Double? = null,
            scoreMode: FunctionScore.ScoreMode? = null,
            boostMode: FunctionScore.BoostMode? = null,
            minScore: Double? = null,
            functions: List<FunctionScore.Function> = emptyList(),
        ): FunctionScoreNode {
            return FunctionScoreNode(
                handle,
                query,
                boost = boost,
                scoreMode = scoreMode,
                boostMode = boostMode,
                minScore = minScore,
                functions = functions.toMutableList()

            )
        }
    }

    override fun clone() = copy(functions = functions.toMutableList())

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
