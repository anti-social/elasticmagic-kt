package dev.evo.elasticmagic

data class FieldFormat(
    val field: Named,
    val format: String? = null,
)

enum class SearchType : ExpressionValue {
    QUERY_THEN_FETCH, DFS_QUERY_THEN_FETCH;

    override fun toValue(): Any {
        return name.toLowerCase()
    }
}

abstract class BaseSearchQuery<S: BaseSource, T: BaseSearchQuery<S, T>>(
    protected val sourceFactory: () -> S,
    protected var query: QueryExpression? = null,
    params: Params = Params(),
) {
    protected var queryNodes: Map<NodeHandle<*>, QueryExpressionNode<*>> = collectNodes(query)

    protected val filters: MutableList<QueryExpression> = mutableListOf()
    protected val postFilters: MutableList<QueryExpression> = mutableListOf()

    protected val aggregations: MutableMap<String, Aggregation> = mutableMapOf()

    protected val docvalueFields: MutableList<FieldFormat> = mutableListOf()
    protected val storedFields: MutableList<Named> = mutableListOf()
    protected val scriptFields: MutableMap<String, Script> = mutableMapOf()

    protected val rescores: MutableList<Rescore> = mutableListOf()
    protected val sorts: MutableList<Sort> = mutableListOf()

    protected var trackScores: Boolean? = null
    protected var trackTotalHits: Boolean? = null

    protected var size: Long? = null
    protected var from: Long? = null
    protected var terminateAfter: Long? = null

    protected val params: MutableParams = params.toMutable()

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

    @Suppress("UNCHECKED_CAST")
    protected fun self(): T = this as T

    protected fun self(block: () -> Unit): T {
        block()
        return self()
    }

    fun query(query: QueryExpression?): T = self {
        this.query = query
        _updateQueryNodes()
    }

    inline fun <reified N: QueryExpressionNode<N>> queryNode(
        handle: NodeHandle<N>,
        block: (N) -> Unit
    ): T {
        val node = _findNode(handle) ?: error("Node handle not found: $handle")
        block(node as N)
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

    fun aggs(vararg aggregations: Pair<String, Aggregation>): T = self {
        this.aggregations.putAll(aggregations)
    }

    fun clearAggs(): T = self {
        aggregations.clear()
    }

    fun rescore(vararg rescores: Rescore): T = self {
        this.rescores += rescores
    }

    fun clearRescore(): T = self {
        rescores.clear()
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

    fun trackTotalHits(trackTotalHits: Boolean?): T = self {
        this.trackTotalHits = trackTotalHits
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

    fun size(size: Long?): T = self {
        this.size = size
    }

    fun from(from: Long?): T = self {
        this.from = from
    }

    fun terminateAfter(terminateAfter: Long?): T = self {
        this.terminateAfter = terminateAfter
    }

    fun searchParams(vararg params: Pair<String, Any?>): T = self {
        for ((key, rawValue) in params) {
            val value = if (rawValue is ExpressionValue) {
                rawValue.toValue()
            } else {
                rawValue
            }
            this.params.putNotNullOrRemove(key, value)
        }
    }

    fun searchType(searchType: SearchType?): T = self {
        params.putNotNullOrRemove("search_type", searchType?.toValue())
    }

    fun routing(routing: String?): T = self {
        params.putNotNullOrRemove("routing", routing)
    }

    fun routing(routing: Long?): T = self {
        params.putNotNullOrRemove("routing", routing)
    }

    fun requestCache(requestCache: Boolean?): T = self {
        params.putNotNullOrRemove("request_cache", requestCache)
    }

    fun stats(stats: String?): T = self {
        params.putNotNullOrRemove("stats", stats)
    }

    fun prepare(): PreparedSearchQuery<S> {
        return PreparedSearchQuery(
            sourceFactory,
            docType = "_doc",
            query = query,
            filters = filters.toList(),
            postFilters = postFilters.toList(),
            aggregations = aggregations.toMap(),
            rescores = rescores.toList(),
            sorts = sorts.toList(),
            trackScores = trackScores,
            trackTotalHits = trackTotalHits,
            docvalueFields = docvalueFields,
            storedFields = storedFields,
            scriptFields = scriptFields,
            size = size,
            from = from,
            terminateAfter = terminateAfter,
            params = params,
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

open class SearchQuery<S: BaseSource>(
    sourceFactory: () -> S,
    query: QueryExpression? = null,
    params: Params = Params(),
) : BaseSearchQuery<S, SearchQuery<S>>(sourceFactory, query, params) {

    companion object {
        operator fun invoke(
            query: QueryExpression? = null,
            params: Params = Params(),
        ): SearchQuery<StdSource> {
            return SearchQuery(::StdSource, query = query, params = params)
        }
    }
}

data class PreparedSearchQuery<S: BaseSource>(
    val sourceFactory: () -> S,
    val docType: String?,
    val query: QueryExpression?,
    val filters: List<QueryExpression>,
    val postFilters: List<QueryExpression>,
    val aggregations: Map<String, Aggregation>,
    val rescores: List<Rescore>,
    val sorts: List<Sort>,
    val trackScores: Boolean?,
    val trackTotalHits: Boolean?,
    val docvalueFields: List<FieldFormat>,
    val storedFields: List<Named>,
    val scriptFields: Map<String, Script>,
    val size: Long?,
    val from: Long?,
    val terminateAfter: Long?,
    val params: Params,
)
