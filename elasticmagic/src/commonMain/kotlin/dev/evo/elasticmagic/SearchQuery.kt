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
import dev.evo.elasticmagic.query.SearchExt
import dev.evo.elasticmagic.query.Sort
import dev.evo.elasticmagic.query.Source
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

    protected var source: Source? = null
    protected val fields: MutableList<FieldFormat> = mutableListOf()
    protected val docvalueFields: MutableList<FieldFormat> = mutableListOf()
    protected val storedFields: MutableList<FieldOperations<*>> = mutableListOf()
    protected val scriptFields: MutableMap<String, Script> = mutableMapOf()

    protected val rescores: MutableList<Rescore> = mutableListOf()
    protected val sorts: MutableList<Sort> = mutableListOf()

    protected var trackScores: Boolean? = null
    protected var trackTotalHits: Boolean? = null

    protected var size: Int? = null
    protected var from: Int? = null
    protected var terminateAfter: Int? = null

    protected val extensions: MutableList<SearchExt> = mutableListOf()

    protected val params: MutableParams = params.toMutable()

    companion object {
        private fun collectNodes(
            expression: QueryExpression?
        ): Map<NodeHandle<*>, QueryExpressionNode<*>> {
            return buildMap {
                expression?.collect { node ->
                    if (node is QueryExpressionNode<*>) {
                        if (node.handle in this) {
                            throw IllegalArgumentException(
                                "Found duplicated node handle: ${node.handle.name}"
                            )
                        }
                        put(node.handle, node)
                    }
                }
            }
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
            source = source,
            fields = fields,
            docvalueFields = docvalueFields,
            storedFields = storedFields,
            scriptFields = scriptFields,
            size = size,
            from = from,
            terminateAfter = terminateAfter,
            extensions = extensions,
            params = params,
        )
    }

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun self(): T = this as T

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

    @PublishedApi
    @Suppress("FunctionName")
    internal fun updateQueryNodes() {
        this.queryNodes = collectNodes(query)
    }

    /**
     * Allows to replace a specific query expression node using a [handle] of the node.
     *
     * @param handle a handle bound to the specific query expression node.
     * @param block a function that returns new query expression node.
     * @throws IllegalArgumentException if a node specified by the [handle] is missing.
     *
     * @sample samples.code.SearchQuery.queryNode
     */
    inline fun <reified N: QueryExpression> queryNode(
        handle: NodeHandle<N>,
        block: (N) -> N
    ): T {
        val node = requireNotNull(findNode(handle)) {
            "Node handle is not found: ${handle.name}"
        }
        val newNode = QueryExpressionNode(
            handle,
            block(node.expression as N)
        )
        rewriteQuery(newNode)

        return self()
    }

    @PublishedApi
    internal fun findNode(handle: NodeHandle<*>): QueryExpressionNode<*>? {
        return queryNodes[handle]
    }

    @PublishedApi
    internal fun rewriteQuery(newNode: QueryExpressionNode<*>) {
        val query = this.query
        if (query != null) {
            query(query.rewrite(newNode))
        }
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
    @Suppress("UNUSED_PARAMETER")
    fun filter(clear: SearchQuery.CLEAR): T = self {
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
    @Suppress("UNUSED_PARAMETER")
    fun postFilter(clear: SearchQuery.CLEAR): T = self {
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
    fun aggs(aggregations: Map<String, Aggregation<*>>): T = self {
        this.aggregations.putAll(aggregations)
    }

    /**
     * Clears the existing aggregations.
     */
    @Suppress("UNUSED_PARAMETER")
    fun aggs(clear: SearchQuery.CLEAR): T = self {
        aggregations.clear()
    }

    /**
     * Adds [rescores] to the existing query rescorers. Rescoring is executed after
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
    @Suppress("UNUSED_PARAMETER")
    fun rescore(clear: SearchQuery.CLEAR): T = self {
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
     * Adds [sorts] from a list to the existing query sorting expressions.
     *
     * @sample samples.code.SearchQuery.sort
     *
     * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/sort-search-results.html>
     */
    fun sort(sorts: List<Sort>): T = self {
        this.sorts += sorts
    }

    /**
     * Clears the existing sorts.
     */
    @Suppress("UNUSED_PARAMETER")
    fun sort(clear: SearchQuery.CLEAR): T = self {
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

    /**
     * Adds [includes] field lists to the document source fields filtering.
     *
     * <b>Note:</b>
     *
     * If you want to <i>replace</i> source fields call [source] with `null` argument.
     *
     * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/search-fields.html#source-filtering>
     */
    fun source(vararg includes: FieldOperations<*>): T = source(includes.asList())

    /**
     * Adds [includes] and [excludes] field lists to the document source fields filtering.
     *
     * <b>Note:</b>
     *
     * If you want to <i>replace</i> source fields call [source] with `null` argument.
     *
     * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/search-fields.html#source-filtering>
     */
    fun source(
        includes: List<FieldOperations<*>> = emptyList(),
        excludes: List<FieldOperations<*>> = emptyList(),
    ): T = self {
        val source = source
        if (source !is Source.Filter) {
            this.source = Source.Filter(includes, excludes)
        } else {
            this.source = source.copy(
                includes = source.includes + includes,
                excludes = source.excludes + excludes,
            )
        }
    }

    /**
     * Disables or enables document's source filtering.
     *
     * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/search-fields.html#source-filtering>
     */
    fun source(enable: Boolean): T = self {
        source = if (enable) {
            Source.Enable
        } else {
            Source.Disable
        }
    }

    /**
     * Clears source filtering values that were previously set.
     *
     * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/search-fields.html#source-filtering>
     */
    @Suppress("UNUSED_PARAMETER")
    fun source(clear: SearchQuery.CLEAR): T = self {
        this.source = null
    }

    fun fields(vararg fields: FieldFormat): T = self {
        this.fields += fields
    }

    @Suppress("UNUSED_PARAMETER")
    fun fields(clear: SearchQuery.CLEAR): T = self {
        fields.clear()
    }

    fun docvalueFields(vararg fields: FieldFormat): T = self {
        docvalueFields += fields
    }

    @Suppress("UNUSED_PARAMETER")
    fun docvalueFields(clear: SearchQuery.CLEAR): T = self {
        docvalueFields.clear()
    }

    fun storedFields(vararg fields: FieldOperations<*>): T = self {
        storedFields += fields
    }

    @Suppress("UNUSED_PARAMETER")
    fun storedFields(clear: SearchQuery.CLEAR): T = self {
        storedFields.clear()
    }

    fun scriptFields(vararg fields: Pair<String, Script>): T = self {
        scriptFields += fields
    }

    @Suppress("UNUSED_PARAMETER")
    fun scriptFields(clear: SearchQuery.CLEAR): T = self {
        scriptFields.clear()
    }

    fun size(size: Int?): T = self {
        this.size = size
    }

    fun from(from: Int?): T = self {
        this.from = from
    }

    fun terminateAfter(terminateAfter: Int?): T = self {
        this.terminateAfter = terminateAfter
    }

    fun ext(vararg extensions: SearchExt): T = self {
        this.extensions += extensions
    }

    @Suppress("UNUSED_PARAMETER")
    fun ext(clear: SearchQuery.CLEAR): T = self {
        this.extensions.clear()
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

    object CLEAR

    companion object {
        operator fun <S: BaseDocSource> invoke(
            docSourceFactory: () -> S,
            query: QueryExpression? = null,
            params: Params = Params(),
        ): SearchQuery<S> {
            return SearchQuery(
                { docSourceFactory() },
                query = query,
                params = params
            )
        }

        operator fun invoke(
            query: QueryExpression? = null,
            params: Params = Params(),
        ): SearchQuery<DynDocSource> {
            return SearchQuery({ DynDocSource() }, query = query, params = params)
        }
    }

    override fun new(docSourceFactory: (Deserializer.ObjectCtx) -> S): SearchQuery<S> {
        return SearchQuery(docSourceFactory)
    }

    suspend fun execute(index: ElasticsearchIndex): SearchQueryResult<S> {
        return index.search(this)
    }

    suspend fun count(index: ElasticsearchIndex): CountResult {
        return index.count(this)
    }
}

data class SearchQueryWithIndex<S: BaseDocSource>(
    val searchQuery: BaseSearchQuery<S, *>,
    val indexName: String,
)

fun <S: BaseDocSource> BaseSearchQuery<S, *>.usingIndex(
    indexName: String
): SearchQueryWithIndex<S> {
    return SearchQueryWithIndex(this, indexName)
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
    val source: Source?,
    val fields: List<FieldFormat>,
    val docvalueFields: List<FieldFormat>,
    val storedFields: List<FieldOperations<*>>,
    val scriptFields: Map<String, Script>,
    val size: Int?,
    val from: Int?,
    val terminateAfter: Int?,
    val extensions: List<SearchExt>,
    val params: Params,
)
