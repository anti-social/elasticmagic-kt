package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

// TODO: Refactor script creation
// Script.WithSource, Script.WithId shortcuts
data class Script(
    val spec: Spec,
    val lang: String? = null,
    val params: Params = Params(),
) : Expression {
    // FIXME: Don't like it as it is error prone
    constructor(
        source: String? = null,
        id: String? = null,
        lang: String? = null,
        params: Params = Params(),
    ) : this(Spec(source, id), lang, params)

    // TODO: After update kotlin to 1.5 move subclasses outside of Spec
    sealed class Spec : Expression {
        data class Source(val source: String) : Spec(), Expression {
            override fun clone() = copy()

            override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
                ctx.field("source", source)
            }
        }

        data class Id(val id: String) : Spec(), Expression {
            override fun clone() = copy()

            override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
                ctx.field("id", id)
            }
        }

        companion object {
            internal operator fun invoke(source: String?, id: String?): Spec {
                return when {
                    source == null && id == null -> {
                        throw IllegalArgumentException(
                            "Both source and id are missing"
                        )
                    }
                    source != null && id != null -> {
                        throw IllegalArgumentException(
                            "Only source or id allowed, not both"
                        )
                    }
                    source != null -> Source(source)
                    id != null -> Id(id)
                    else -> {
                        error("Unreachable")
                    }
                }
            }
        }
    }

    companion object {
        @Suppress("FunctionNaming")
        fun Source(source: String): Spec.Source = Spec.Source(source)

        @Suppress("FunctionNaming")
        fun Id(id: String): Spec.Id = Spec.Id(id)
    }

    override fun clone() = copy()

    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        when (spec) {
            is Spec.Source -> ctx.field("source", spec.source)
            is Spec.Id -> ctx.field("id", spec.id)
        }
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
