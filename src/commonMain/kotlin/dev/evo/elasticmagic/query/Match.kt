package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

// TODO: seled class for minimumShouldMatch argument
data class Match(
    val field: FieldOperations<String>,
    val query: String,
    val analyzer: String? = null,
    val minimumShouldMatch: Any? = null,
    val params: Params? = null,
) : QueryExpression {
    override val name = "match"

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        val params = Params(
            params,
            "analyzer" to analyzer,
            "minimum_should_match" to minimumShouldMatch,
        )
        if (params.isEmpty()) {
            ctx.field(field.getQualifiedFieldName(), query)
        } else {
            ctx.obj(field.getQualifiedFieldName()) {
                field("query", query)
                compiler.visit(this, params)
            }
        }
    }
}

data class MatchPhrase(
    val field: FieldOperations<String>,
    val query: String,
    val slop: Int? = null,
    val analyzer: String? = null,
    val params: Params? = null,
) : QueryExpression {
    override val name = "match_phrase"

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        val params = Params(
            params,
            "slop" to slop,
            "analyzer" to analyzer,
        )
        if (params.isEmpty()) {
            ctx.field(field.getQualifiedFieldName(), query)
        } else {
            ctx.obj(field.getQualifiedFieldName()) {
                field("query", query)
                compiler.visit(this, params)
            }
        }
    }
}

object MatchAll : QueryExpression {
    override val name = "match_all"

    override fun clone() = this

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {}
}

data class MultiMatch(
    val query: String,
    val fields: List<FieldOperations<String>>,
    val type: Type? = null,
    val boost: Double? = null,
    val params: Params? = null,
) : QueryExpression {
    override val name = "multi_match"

    enum class Type : ToValue<String> {
        BEST_FIELDS, MOST_FIELDS, CROSS_FIELDS, PHRASE, PHRASE_PREFIX;

        override fun toValue() = name.lowercase()
    }

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.field("query", query)
        ctx.array("fields") {
            compiler.visit(this, fields.map(FieldOperations<*>::getQualifiedFieldName))
        }
        ctx.fieldIfNotNull("type", type?.toValue())
        ctx.fieldIfNotNull("boost", boost)
        if (!params.isNullOrEmpty()) {
            compiler.visit(ctx, params)
        }
    }
}