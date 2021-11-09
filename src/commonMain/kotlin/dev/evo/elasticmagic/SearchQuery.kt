package dev.evo.elasticmagic

import dev.evo.elasticmagic.aggs.Aggregation
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.query.FieldFormat
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.NodeHandle
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.query.QueryExpressionNode
import dev.evo.elasticmagic.query.Rescore
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.query.Sort
import dev.evo.elasticmagic.query.ToValue
import dev.evo.elasticmagic.query.collect
import dev.evo.elasticmagic.serde.Deserializer

enum class SearchType : ToValue<String> {
    QUERY_THEN_FETCH, DFS_QUERY_THEN_FETCH;

    override fun toValue() = name.lowercase()
}

/**
 * An abstract class that holds all the search query builder methods. Inheritors of the class
 * can implement some shortcut methods. For instance [SearchQuery.execute] which can be
 * suspendable or blocking.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class BaseSearchQuery<S: BaseDocSource, T: BaseSearchQuery<S, T>>(
    protected val docSourceFactory: (obj: Deserializer.ObjectCtx) -> S,
    protected var query: QueryExpression? = null,
    params: Params = Params(),
) {
    protected var queryNodes: Map<NodeHandle<*>, QueryExpressionNode<*>> = collectNodes(query)

    protected val filters: MutableList<QueryExpression> = mutableListOf()
    protected val postFilters: MutableList<QueryExpression> = mutableListOf()

    protected val aggregations: MutableMap<String, Aggregation<*>> = mutableMapOf()

    protected val fields: MutableList<FieldFormat> = mutableListOf()
    protected val docvalueFields: MutableList<FieldFormat> = mutableListOf()
    protected val storedFields: MutableList<FieldOperations<*>> = mutableListOf()
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
        private fun collectNodes(
            expression: QueryExpression?
        ): Map<NodeHandle<*>, QueryExpressionNode<*>> {
            val nodes = mutableMapOf<NodeHandle<*>, QueryExpressionNode<*>>()
            expression?.collect { node ->
                if (node is QueryExpressionNode<*>) {
                    nodes[node.handle] = node
                }
            }
            return nodes
        }
    }

    protected abstract fun new(docSourceFactory: (obj: Deserializer.ObjectCtx) -> S): T

    /**
     * Clones this search query builder.
     */
    fun clone(): T {
        val cloned = new(docSourceFactory)
        cloned.query = query?.clone()
        cloned.queryNodes = queryNodes
        cloned.filters.addAll(filters)
        cloned.postFilters.addAll(postFilters)
        cloned.aggregations.putAll(aggregations)
        cloned.rescores.addAll(rescores)
        cloned.sorts.addAll(sorts)
        cloned.trackScores = trackScores
        cloned.trackTotalHits = trackTotalHits
        cloned.fields.addAll(fields)
        cloned.docvalueFields.addAll(docvalueFields)
        cloned.storedFields.addAll(storedFields)
        cloned.scriptFields.putAll(scriptFields)
        cloned.size = size
        cloned.from = from
        cloned.terminateAfter = terminateAfter
        cloned.params.putAll(params)
        return cloned
    }

    /**
     * Makes an immutable view of the search query. Be careful when using this method.
     *
     * <b>Note:</b>
     *
     * Returned [PreparedSearchQuery] is just a view of the [SearchQuery], thus changes in
     * the search query are reflected in the [PreparedSearchQuery].
     * Therefore [PreparedSearchQuery] should only be used from the same thread as the underlying
     * [SearchQuery]. If you really need to share [PreparedSearchQuery] between threads
     * you should clone the [SearchQuery]:
     *
     * ```kotlin
     * val preparedQuery = searchQuery.clone().prepare()
     * ```
     */
    fun prepare(): PreparedSearchQuery<S> {
        return PreparedSearchQuery(
            docSourceFactory,
            query = query,
            filters = filters,
            postFilters = postFilters,
            aggregations = aggregations,
            rescores = rescores,
            sorts = sorts,
            trackScores = trackScores,
            trackTotalHits = trackTotalHits,
            fields = fields,
            docvalueFields = docvalueFields,
            storedFields = storedFields,
            scriptFields = scriptFields,
            size = size,
            from = from,
            terminateAfter = terminateAfter,
            params = params,
        )
    }

    @Suppress("UNCHECKED_CAST")
    protected fun self(): T = this as T

    protected fun self(block: () -> Unit): T {
        block()
        return self()
    }

    /**
     * Replaces main query expression.
     *
     * @param query a new query that should replace an existing one.
     *
     * @sample samples.code.SearchQuery.query
     *
     * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html>
     */
    fun query(query: QueryExpression?): T = self {
        this.query = query
        updateQueryNodes()
    }

    /**
     * Allows modifying specific query expression node using [handle] of the node.
     *
     * @param handle a handle bound to the specific query expression node.
     * @param block a function that modifies the query expression node.
     * @throws IllegalArgumentException if a node specified by the [handle] is missing.
     *
     * @sample samples.code.SearchQuery.queryNode
     */
    inline fun <reified N: QueryExpressionNode<N>> queryNode(
        handle: NodeHandle<N>,
        block: (N) -> Unit
    ): T {
        val node = requireNotNull(findNode(handle)) {
            "Node handle not found: $handle"
        }
        block(node as N)
        updateQueryNodes()

        @Suppress("UNCHECKED_CAST")
        return this as T
    }

    @PublishedApi
    @Suppress("FunctionName")
    internal fun findNode(handle: NodeHandle<*>): QueryExpressionNode<*>? {
        return queryNodes[handle]
    }

    @PublishedApi
    @Suppress("FunctionName")
    internal fun updateQueryNodes() {
        this.queryNodes = collectNodes(query)
    }

    /**
     * Combines all the filter expressions together and wraps the existing query
     * using the [dev.evo.elasticmagic.query.Bool] query expression.
     *
     * @param filters query filters which will be appended to the existing filters.
     *
     * @sample samples.code.SearchQuery.filter
     *
     * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/query-filter-context.html#filter-context>
     */
    fun filter(vararg filters: QueryExpression): T = self {
        this.filters += filters
    }

    /**
     * Clears the existing filters.
     */
    fun clearFilter(): T = self {
        filters.clear()
    }

    /**
     * Filter expressions in the post filter will be applied after the aggregations are
     * calculated. Useful for building faceted filtering.
     *
     * @param filters query filters which will be appended to the existing post filters.
     *
     * @sample samples.code.SearchQuery.postFilter
     *
     * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/filter-search-results.html#post-filter>
     */
    fun postFilter(vararg filters: QueryExpression): T = self {
        this.postFilters += filters
    }

    /**
     * Clears the existing post filters.
     */
    fun clearPostFilter(): T = self {
        postFilters.clear()
    }

    /**
     * Adds [aggregations] to the existing query aggregations.
     *
     * @param aggregations pairs of the aggregation name and the aggregation itself.
     * The aggregation name can be used to retrieve an aggregation result using
     * [SearchQueryResult.aggs] method.
     *
     * @sample samples.code.SearchQuery.aggs
     *
     * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations.html>
     */
    fun aggs(vararg aggregations: Pair<String, Aggregation<*>>): T = self {
        this.aggregations.putAll(aggregations)
    }

    /**
     * Clears the existing aggregations.
     */
    fun clearAggs(): T = self {
        aggregations.clear()
    }

    /**
     * Adds [rescorers] to the existing query rescorers. Rescoring is executed after
     * post filter phase.
     *
     * @sample samples.code.SearchQuery.rescore
     *
     * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/filter-search-results.html#rescore>
     */
    fun rescore(vararg rescores: Rescore): T = self {
        this.rescores += rescores
    }

    /**
     * Clears the existing rescorers.
     */
    fun clearRescore(): T = self {
        rescores.clear()
    }

    /**
     * Adds [sorts] to the existing query sorting expressions.
     *
     * @sample samples.code.SearchQuery.sort
     *
     * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/sort-search-results.html>
     */
    fun sort(vararg sorts: Sort): T = self {
        this.sorts += sorts
    }

    /**
     * Clears the existing sorts.
     */
    fun clearSort(): T = self {
        sorts.clear()
    }

    /**
     * If [trackScores] is `true` forces computing scores even when sorting on a field.
     *
     * @see <https://www.elastic.co/guide/en/elasticsearch/reference/7.15/sort-search-results.html#_track_scores>
     */
    fun trackScores(trackScores: Boolean?): T = self {
        this.trackScores = trackScores
    }

    /**
     * When [trackTotalHits] is `true` the search query will always count the total number of
     * hits that match the query.
     *
     * @see <https://www.elastic.co/guide/en/elasticsearch/reference/7.15/search-your-data.html#track-total-hits>
     */
    fun trackTotalHits(trackTotalHits: Boolean?): T = self {
        this.trackTotalHits = trackTotalHits
    }

    fun fields(vararg fields: FieldFormat): T = self {
        this.fields += fields
    }

    fun clearFields(): T = self {
        fields.clear()
    }

    fun docvalueFields(vararg fields: FieldFormat): T = self {
        docvalueFields += fields
    }

    fun clearDocvalueFields(): T = self {
        docvalueFields.clear()
    }

    fun storedFields(vararg fields: FieldOperations<*>): T = self {
        storedFields += fields
    }

    fun clearStoredFields(): T = self {
        storedFields.clear()
    }

    fun scriptFields(vararg fields: Pair<String, Script>): T = self {
        scriptFields += fields
    }

    fun clearScriptFields(): T = self {
        scriptFields.clear()
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
            val value = if (rawValue is ToValue<*>) {
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
}

/**
 * An asynchronous version of search query.
 */
open class SearchQuery<S: BaseDocSource>(
    docSourceFactory: (obj: Deserializer.ObjectCtx) -> S,
    query: QueryExpression? = null,
    params: Params = Params(),
) : BaseSearchQuery<S, SearchQuery<S>>(docSourceFactory, query, params) {

    companion object {
        operator fun <S: BaseDocSource> invoke(
            docSourceFactory: () -> S,
            query: QueryExpression? = null,
            params: Params = Params(),
        ): SearchQuery<S> {
            return SearchQuery(
                { _ -> docSourceFactory() },
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

    override fun new(docSourceFactory: (obj: Deserializer.ObjectCtx) -> S): SearchQuery<S> {
        return SearchQuery(docSourceFactory)
    }

    suspend fun execute(index: ElasticsearchIndex): SearchQueryResult<S> {
        return index.search(this)
    }
}

/**
 * A prepared search query is a public read-only view to a search query.
 * Mainly it is used to compile a search query.
 */
data class PreparedSearchQuery<S: BaseDocSource>(
    val docSourceFactory: (obj: Deserializer.ObjectCtx) -> S,
    val query: QueryExpression?,
    val filters: List<QueryExpression>,
    val postFilters: List<QueryExpression>,
    val aggregations: Map<String, Aggregation<*>>,
    val rescores: List<Rescore>,
    val sorts: List<Sort>,
    val trackScores: Boolean?,
    val trackTotalHits: Boolean?,
    val fields: List<FieldFormat>,
    val docvalueFields: List<FieldFormat>,
    val storedFields: List<FieldOperations<*>>,
    val scriptFields: Map<String, Script>,
    val size: Long?,
    val from: Long?,
    val terminateAfter: Long?,
    val params: Params,
)
