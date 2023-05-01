package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.serde.Serializer

data class HasParent(
    val query: QueryExpression,
    val parentType: String,
    val score: Boolean = false,
    val params: Params? = null,
) : QueryExpression {

    override val name: String
        get() = "has_parent"

    override fun clone(): QueryExpression = copy()


    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: BaseSearchQueryCompiler
    ) {
        ctx.field("parent_type", parentType)
        ctx.fieldIfNotNull("score", score)
        ctx.fieldIfNotNull("ignore_unmapped", params?.get("ignore_unmapped"))

        ctx.obj("query") {
            compiler.visit(this, query)
        }
    }
}