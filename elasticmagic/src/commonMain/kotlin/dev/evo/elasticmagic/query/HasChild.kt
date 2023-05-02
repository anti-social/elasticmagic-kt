package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

data class HasChild(
    val query: QueryExpression,
    val type: String,
    val maxChildren: Int? = null,
    val minChildren: Int? = null,
    val scoreMode: FunctionScore.ScoreMode? = null,
    val params: Params? = null,
) : QueryExpression {

    override val name: String
        get() = "has_child"

    override fun clone(): QueryExpression = copy()


    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: BaseSearchQueryCompiler
    ) {
        ctx.field("type", type)
        ctx.fieldIfNotNull("max_children", maxChildren)
        ctx.fieldIfNotNull("min_children", minChildren)
        ctx.fieldIfNotNull("score_mode", scoreMode?.name?.lowercase())
        ctx.fieldIfNotNull("ignore_unmapped", params?.get("ignore_unmapped"))

        ctx.obj("query") {
            compiler.visit(this, query)
        }
    }
}
