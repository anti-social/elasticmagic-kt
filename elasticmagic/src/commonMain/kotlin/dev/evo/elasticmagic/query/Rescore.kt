package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

abstract class Rescore : NamedExpression {
    abstract val windowSize: Int?

    override fun accept(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
        super.accept(ctx, compiler)
        ctx.fieldIfNotNull("window_size", windowSize)
    }
}

data class QueryRescore(
    val query: QueryExpression,
    val queryWeight: Float? = null,
    val rescoreQueryWeight: Float? = null,
    val scoreMode: ScoreMode? = null,
    override val windowSize: Int? = null,
) : Rescore() {
    override val name = "query"

    enum class ScoreMode : ToValue<String> {
        TOTAL, MULTIPLY, AVG, MAX, MIN;

        override fun toValue() = name.lowercase()
    }

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
        ctx.obj("rescore_query") {
            compiler.visit(this, query)
        }
        ctx.fieldIfNotNull("query_weight", queryWeight)
        ctx.fieldIfNotNull("rescore_query_weight", rescoreQueryWeight)
        ctx.fieldIfNotNull("score_mode", scoreMode?.toValue())
    }
}
