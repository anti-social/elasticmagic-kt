package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

sealed class Script : ObjExpression {
    protected abstract val by: String
    protected abstract val value: String

    abstract val lang: String?
    abstract val params: Params

    data class Id(
        val id: String,
        override val lang: String? = null,
        override val params: Params = Params(),
    ) : Script() {
        override val by = "id"
        override val value = id

        override fun clone() = copy()
    }

    data class Source(
        val source: String,
        override val lang: String? = null,
        override val params: Params = Params(),
    ) : Script() {
        override val by = "source"
        override val value = source

        override fun clone() = copy()
    }

    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.field(by, value)
        if (lang != null) {
            ctx.field("lang", lang)
        }
        if (params.isNotEmpty()) {
            ctx.obj("params") {
                compiler.visit(this, params)
            }
        }
    }
}
