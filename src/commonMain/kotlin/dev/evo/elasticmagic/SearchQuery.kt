package dev.evo.elasticmagic

abstract class BaseSearchQuery<T: BaseSearchQuery<T>>(
    protected var docType: String? = null,
    protected var query: QueryExpression? = null,
) {
    protected val filters = mutableListOf<QueryExpression>()

    object QueryCtx {
        val bool = Bool

        fun multiMatch(
            query: String,
            fields: List<FieldOperations>,
            type: MultiMatch.Type? = null,
            boost: Double? = null,
        ) = MultiMatch(query, fields, type, boost)

        fun functionScore(
            query: QueryExpression?,
            boost: Double? = null,
            scoreMode: FunctionScore.ScoreMode? = null,
            boostMode: FunctionScore.BoostMode? = null,
            functions: List<FunctionScore.Function>,
        ) = FunctionScore(query, boost, scoreMode, boostMode, functions)

        fun weight(
            weight: Double,
            filter: QueryExpression? = null,
        ) = FunctionScore.Weight(weight, filter)

        fun fieldValueFactor(
            field: FieldOperations,
            factor: Double? = null,
            missing: Double? = null,
            filter: QueryExpression? = null
        ) = FunctionScore.FieldValueFactor(field, factor, missing, filter)
    }

    @Suppress("UNCHECKED_CAST")
    protected fun self(): T = this as T

    protected fun self(block: () -> Unit): T {
        block()
        return self()
    }

    fun query(block: QueryCtx.() -> QueryExpression?): T = query(QueryCtx.block())

    fun query(query: QueryExpression?): T = self {
        this.query = query
    }

    fun filter(vararg filters: QueryExpression): T = self {
        this.filters += filters
    }

    fun prepare(): PreparedSearchQuery {
        return PreparedSearchQuery(
            docType = docType,
            query = query,
            filters = filters.toList(),
        )
    }
}

open class SearchQuery(
    docType: String? = null, query: QueryExpression? = null
) : BaseSearchQuery<SearchQuery>(docType, query) {

    constructor(query: QueryExpression? = null) : this(null, query)
    constructor(block: QueryCtx.() -> QueryExpression?) : this(null, QueryCtx.block())
    constructor(docType: String? = null, block: QueryCtx.() -> QueryExpression?) : this(docType, QueryCtx.block())

    // fun usingIndex(index: Index) = BoundSearchQuery(index, docType, this)
}

data class PreparedSearchQuery(
    val docType: String? = null,
    val query: QueryExpression? = null,
    val filters: List<QueryExpression> = emptyList(),
)