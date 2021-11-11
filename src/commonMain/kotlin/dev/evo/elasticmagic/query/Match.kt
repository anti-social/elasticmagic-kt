package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

/**
 * Represents variants for `minimum_should_match` parameter.
 *
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-minimum-should-match.html>
 */
sealed class MinimumShouldMatch : ToValue<Any> {
    abstract class Simple : MinimumShouldMatch()

    /**
     * A fixed number of matched clauses. Can be negative that means a number of optional clauses.
     */
    data class Count(val count: Int) : Simple() {
        override fun toValue(): Int = count
    }

    /**
     * A percentage of the total number of clauses should be necessary.
     */
    data class Percent(val percent: Int) : Simple() {
        override fun toValue(): String = "$percent%"
    }

    /**
     * A list of pairs where the first value is a positive integer. If the number of
     * matched clauses is greater than it then a specification from the second value is applied.
     */
    data class Combinations(val combinations: List<Pair<Int, Simple>>) : MinimumShouldMatch() {
        constructor(vararg combinations: Pair<Int, Simple>) : this(combinations.toList())

        override fun toValue(): String {
            return combinations.joinToString(" ") { (count, spec) ->
                "$count<${spec.toValue()}"
            }
        }
    }
}

data class Match(
    val field: FieldOperations<String>,
    val query: String,
    val boost: Double? = null,
    val analyzer: String? = null,
    val minimumShouldMatch: MinimumShouldMatch? = null,
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
            "boost" to boost,
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
    val boost: Double? = null,
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
            "boost" to boost,
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
