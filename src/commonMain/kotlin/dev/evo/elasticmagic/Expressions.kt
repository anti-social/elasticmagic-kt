package dev.evo.elasticmagic

interface Expression {
    val name: String
}

interface QueryExpression : Expression

data class Term(
    val field: FieldOperations,
    val term: Any,
    val boost: Double? = null,
) : QueryExpression {
    override val name = "term"
}

data class Exists(
    val field: FieldOperations,
) : QueryExpression {
    override val name = "exists"
}

data class Range(
    val field: FieldOperations,
    val gt: Any? = null,
    val gte: Any? = null,
    val lt: Any? = null,
    val lte: Any? = null,
) : QueryExpression {
    override val name = "range"
}

data class Match(
    val field: FieldOperations,
    val query: String,
    val analyzer: String? = null,
    val minimumShouldMatch: Any? = null,
    val params: Params? = null,
) : QueryExpression {
    override val name = "match"
}

class MatchAll : QueryExpression {
    override val name = "match_all"
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
    }

    data class Weight(
        val weight: Double,
        override val filter: QueryExpression?,
    ) : Function() {
        override val name = "weight"
    }

    data class FieldValueFactor(
        val field: FieldOperations,
        val factor: Double? = null,
        val missing: Double? = null,
        override val filter: QueryExpression? = null,
    ) : Function() {
        override val name = "field_value_factor"
    }
}

