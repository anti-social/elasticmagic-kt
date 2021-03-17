package dev.evo.elasticmagic

data class FieldFormat(
    val field: Named,
    val format: String? = null,
)

abstract class BaseSearchQuery<S: Source, T: BaseSearchQuery<S, T>>(
    protected val sourceFactory: () -> S,
    protected var query: QueryExpression? = null,
) {
    var queryNodes: Map<NodeHandle<*>, QueryExpressionNode<*>> = collectNodes(query)

    protected val filters: MutableList<QueryExpression> = mutableListOf()
    protected val postFilters: MutableList<QueryExpression> = mutableListOf()

    protected val docvalueFields: MutableList<FieldFormat> = mutableListOf()
    protected val storedFields: MutableList<Named> = mutableListOf()
    protected val scriptFields: MutableMap<String, Script> = mutableMapOf()

    protected val sorts: MutableList<Sort> = mutableListOf()
    protected var trackScores: Boolean? = null

    protected var size: Long? = null
    protected var from: Long? = null

    companion object {
        private fun collectNodes(expression: QueryExpression?): Map<NodeHandle<*>, QueryExpressionNode<*>> {
            val nodes = mutableMapOf<NodeHandle<*>, QueryExpressionNode<*>>()
            expression?.collect { node ->
                if (node is QueryExpressionNode<*>) {
                    nodes[node.handle] = node
                }
            }
            return nodes
        }
    }

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
            minScore: Double? = null,
            functions: List<FunctionScore.Function>,
        ) = FunctionScore(query, boost, scoreMode, boostMode, minScore, functions)

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
        _updateQueryNodes()
    }

    inline fun <reified N: QueryExpressionNode<N>> queryNode(
        handle: NodeHandle<N>,
        block: QueryCtx.(N) -> Unit
    ): T {
        val node = _findNode(handle) ?: error("Node handle not found: $handle")
        QueryCtx.block(node as N)
        _updateQueryNodes()

        @Suppress("UNCHECKED_CAST")
        return this as T
    }

    @Suppress("FunctionName")
    fun _findNode(handle: NodeHandle<*>): QueryExpressionNode<*>? {
        return queryNodes[handle]
    }

    @Suppress("FunctionName")
    fun _updateQueryNodes() {
        this.queryNodes = collectNodes(query)
    }

    fun filter(vararg filters: QueryExpression): T = self {
        this.filters += filters
    }

    fun postFilter(vararg filters: QueryExpression): T = self {
        this.postFilters += filters
    }

    fun sort(vararg sorts: Sort): T = self {
        this.sorts += sorts
    }

    fun clearSort(): T = self {
        sorts.clear()
    }

    fun trackScores(trackScores: Boolean?): T = self {
        this.trackScores = trackScores
    }

    fun docvalueFields(vararg fields: Named): T = self {
        docvalueFields += fields.map(::FieldFormat)
    }

    fun docvalueFields(vararg fields: FieldFormat): T = self {
        docvalueFields += fields
    }

    fun storedFields(vararg fields: Named): T = self {
        storedFields += fields
    }

    fun scriptFields(vararg fields: Pair<String, Script>): T = self {
        scriptFields += fields
    }

    fun size(size: Long): T = self {
        this.size = size
    }

    fun from(from: Long): T = self {
        this.from = from
    }

    fun prepare(): PreparedSearchQuery<S> {
        return PreparedSearchQuery(
            sourceFactory,
            docType = "_doc",
            query = query,
            filters = filters.toList(),
            postFilters = postFilters.toList(),
            sorts = sorts.toList(),
            docvalueFields = docvalueFields,
            storedFields = storedFields,
            scriptFields = scriptFields,
            size = size,
            from = from,
        )
    }

    // suspend fun <R> execute(index: ElasticsearchIndex<R>): SearchQueryResult<R, S> {
    //     return index.search(this)
    // }
    //
    //
    // fun <R> execute(index: ElasticsearchSyncIndex<R>): SearchQueryResult<R, S> {
    //     return index.search(this)
    // }
}

open class SearchQuery<S: Source>(
    sourceFactory: () -> S,
    query: QueryExpression? = null,
) : BaseSearchQuery<S, SearchQuery<S>>(sourceFactory, query) {

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

data class PreparedSearchQuery<S: Source>(
    val sourceFactory: () -> S,
    val docType: String?,
    val query: QueryExpression?,
    val filters: List<QueryExpression>,
    val postFilters: List<QueryExpression>,
    val sorts: List<Sort>,
    val docvalueFields: List<FieldFormat>,
    val storedFields: List<Named>,
    val scriptFields: Map<String, Script>,
    val size: Long?,
    val from: Long?,
)

data class SearchQueryResult<S: Source>(
    val rawResult: Map<String, Any?>?,
    val took: Long,
    val timedOut: Boolean,
    val totalHits: Long?,
    val totalHitsRelation: String?,
    val maxScore: Double?,
    val hits: List<SearchHit<S>>,
)

data class SearchHit<S: Source>(
    val index: String,
    val type: String,
    val id: String,
    val score: Double?,
    val source: S?,
)
