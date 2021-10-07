package dev.evo.elasticmagic

import dev.evo.elasticmagic.aggs.Aggregation
import dev.evo.elasticmagic.query.NodeHandle
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.query.QueryExpressionNode
import dev.evo.elasticmagic.query.Rescore
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.query.Sort
import dev.evo.elasticmagic.query.ToValue
import dev.evo.elasticmagic.query.collect
import dev.evo.elasticmagic.serde.Deserializer

data class FieldFormat(
    val field: FieldOperations,
    val format: String? = null,
)

enum class SearchType : ToValue {
    QUERY_THEN_FETCH, DFS_QUERY_THEN_FETCH;

    override fun toValue() = name.lowercase()
}

@Suppress("UnnecessaryAbstractClass")
abstract class BaseSearchQuery<S: BaseDocSource, T: BaseSearchQuery<S, T>>(
    protected val sourceFactory: (obj: Deserializer.ObjectCtx) -> S,
    protected var query: QueryExpression? = null,
    params: Params = Params(),
) {
    protected var queryNodes: Map<NodeHandle<*>, QueryExpressionNode<*>> = collectNodes(query)

    protected val filters: MutableList<QueryExpression> = mutableListOf()
    protected val postFilters: MutableList<QueryExpression> = mutableListOf()

    protected val aggregations: MutableMap<String, Aggregation<*>> = mutableMapOf()

    protected val docvalueFields: MutableList<FieldFormat> = mutableListOf()
    protected val storedFields: MutableList<FieldOperations> = mutableListOf()
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

    fun aggs(vararg aggregations: Pair<String, Aggregation<*>>): T = self {
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

    fun sort(vararg fields: FieldOperations): T = self {
        this.sorts += fields.map(::Sort)
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

    fun docvalueFields(vararg fields: FieldOperations): T = self {
        docvalueFields += fields.map(::FieldFormat)
    }

    fun docvalueFields(vararg fields: FieldFormat): T = self {
        docvalueFields += fields
    }

    fun storedFields(vararg fields: FieldOperations): T = self {
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
            val value = if (rawValue is ToValue) {
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

    fun version(version: Boolean?): T = self {
        params.putNotNullOrRemove("version", version)
    }

    fun seqNoPrimaryTerm(seqNoPrimaryTerm: Boolean?): T = self {
        params.putNotNullOrRemove("seq_no_primary_term", seqNoPrimaryTerm)
    }

    fun prepare(): PreparedSearchQuery<S> {
        return PreparedSearchQuery(
            sourceFactory,
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
}

open class SearchQuery<S: BaseDocSource>(
    sourceFactory: (obj: Deserializer.ObjectCtx) -> S,
    query: QueryExpression? = null,
    params: Params = Params(),
) : BaseSearchQuery<S, SearchQuery<S>>(sourceFactory, query, params) {

    companion object {
        operator fun <S: BaseDocSource> invoke(
            sourceFactory: () -> S,
            query: QueryExpression? = null,
            params: Params = Params(),
        ): SearchQuery<S> {
            return SearchQuery(
                { _ -> sourceFactory() },
                query = query,
                params = params
            )
        }

        operator fun invoke(
            query: QueryExpression? = null,
            params: Params = Params(),
        ): SearchQuery<DynDocSource> {
            return SearchQuery(::DynDocSource, query = query, params = params)
        }
    }

    /**
     * Clones this search query builder.
     */
    fun clone(): SearchQuery<S> {
        val cloned = SearchQuery(sourceFactory)
        cloned.query = query?.clone()
        cloned.queryNodes = queryNodes
        cloned.filters.addAll(filters)
        cloned.postFilters.addAll(postFilters)
        cloned.aggregations.putAll(aggregations)
        cloned.rescores.addAll(rescores)
        cloned.sorts.addAll(sorts)
        cloned.trackScores = trackScores
        cloned.trackTotalHits = trackTotalHits
        cloned.docvalueFields.addAll(docvalueFields)
        cloned.storedFields.addAll(storedFields)
        cloned.scriptFields.putAll(scriptFields)
        cloned.size = size
        cloned.from = from
        cloned.terminateAfter = terminateAfter
        cloned.params.putAll(params)
        return cloned
    }

    suspend fun execute(index: ElasticsearchIndex<*>): SearchQueryResult<S> {
        return index.search(this)
    }
}

data class PreparedSearchQuery<S: BaseDocSource>(
    val sourceFactory: (obj: Deserializer.ObjectCtx) -> S,
    val query: QueryExpression?,
    val filters: List<QueryExpression>,
    val postFilters: List<QueryExpression>,
    val aggregations: Map<String, Aggregation<*>>,
    val rescores: List<Rescore>,
    val sorts: List<Sort>,
    val trackScores: Boolean?,
    val trackTotalHits: Boolean?,
    val docvalueFields: List<FieldFormat>,
    val storedFields: List<FieldOperations>,
    val scriptFields: Map<String, Script>,
    val size: Long?,
    val from: Long?,
    val terminateAfter: Long?,
    val params: Params,
)
