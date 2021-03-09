package dev.evo.elasticmagic

abstract class BaseSearchQuery<S: Source, T: BaseSearchQuery<S, T>>(
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
            docType = "_doc",
            query = query,
            filters = filters.toList(),
        )
    }

    suspend fun execute(index: ElasticsearchIndex): SearchQueryResult<S> {
        return index.search(this)
    }


    fun execute(index: ElasticsearchSyncIndex): SearchQueryResult<S> {
        return index.search(this)
    }
}

open class SearchQuery<S: Source>(
    protected val sourceFactory: () -> S,
    query: QueryExpression? = null,
) : BaseSearchQuery<S, SearchQuery<S>>(query) {

    companion object {
        operator fun invoke(query: QueryExpression? = null): SearchQuery<StdSource> {
            return SearchQuery(::StdSource, query = query)
        }

        operator fun <S: Source> invoke(
            sourceFactory: () -> S,
            block: QueryCtx.() -> QueryExpression?
        ): SearchQuery<S> {
            return SearchQuery(sourceFactory, query = QueryCtx.block())
        }

        operator fun invoke(block: QueryCtx.() -> QueryExpression?): SearchQuery<StdSource> {
            return SearchQuery(::StdSource, query = QueryCtx.block())
        }
    }
}

data class PreparedSearchQuery(
    val docType: String? = null,
    val query: QueryExpression? = null,
    val filters: List<QueryExpression> = emptyList(),
)

data class SearchQueryResult<T: Source>(
    val took: Long,
    val timedOut: Boolean,
    val totalHits: Long?,
    val totalHitsRelation: String?,
    val maxScore: Double,
    val hits: List<SearchHit<T>>,
)

data class SearchHit<T: Source>(
    val index: String,
    val type: String,
    val id: String,
    val score: Double?,
    val source: T,
)
