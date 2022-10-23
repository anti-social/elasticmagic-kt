package dev.evo.elasticmagic

import dev.evo.elasticmagic.bulk.Action
import dev.evo.elasticmagic.bulk.Refresh
import dev.evo.elasticmagic.compile.ActionCompiler
import dev.evo.elasticmagic.compile.PreparedBulk
import dev.evo.elasticmagic.compile.CompilerSet
import dev.evo.elasticmagic.compile.PreparedCreateIndex
import dev.evo.elasticmagic.compile.PreparedUpdateMapping
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.transport.BulkRequest
import dev.evo.elasticmagic.transport.ElasticsearchException
import dev.evo.elasticmagic.transport.ElasticsearchTransport
import dev.evo.elasticmagic.transport.ApiRequest
import dev.evo.elasticmagic.transport.Method
import dev.evo.elasticmagic.transport.Parameters

import kotlinx.coroutines.CompletableDeferred

internal fun Params.toRequestParameters(): Parameters {
    return Parameters(*this.toList().toTypedArray())
}

class ElasticsearchCluster(
    val transport: ElasticsearchTransport,
    val apiSerde: Serde,
    val bulkSerde: Serde.OneLineJson,
    private val compilers: CompilerSet? = null,
) {
    private val esVersion = CompletableDeferred<Version<*>>()
    private val sniffedCompilers = CompletableDeferred<CompilerSet>()

    constructor(
        transport: ElasticsearchTransport,
        serde: Serde.OneLineJson,
        compilers: CompilerSet? = null,
    ): this(
        transport,
        apiSerde = serde,
        bulkSerde = serde,
        compilers = compilers,
    )

    operator fun get(indexName: String): ElasticsearchIndex {
        return ElasticsearchIndex(indexName, this)
    }

    private suspend fun fetchVersion(): Version<*> {
        val result = transport.request(
            ApiRequest(Method.GET,"", serde = apiSerde)
        )
        val versionObj = result.obj("version")
        val distribution = versionObj.stringOrNull("distribution") ?: "elasticsearch"
        val rawEsVersion = versionObj.string("number")
        val versionParts = rawEsVersion.split('.')
        val major = versionParts[0].toInt()
        val minor = versionParts[1].toInt()
        val patch = versionParts[2].toInt()
        return when (distribution) {
            "opensearch" -> Version.Opensearch(major, minor, patch)
            "elasticsearch" -> Version.Elasticsearch(major, minor, patch)
            else -> throw IllegalStateException("Unknown distribution: $distribution")
        }
    }

    suspend fun getVersion(): Version<*> {
        if (!esVersion.isCompleted) {
            // Only first value will be set
            esVersion.complete(fetchVersion())
        }
        return esVersion.await()
    }

    suspend fun getCompilers(): CompilerSet {
        if (compilers != null) {
            return compilers
        }
        if (!sniffedCompilers.isCompleted) {
            // Only first value will be set
            sniffedCompilers.complete(CompilerSet(getVersion()))
        }
        return sniffedCompilers.await()
    }

    suspend fun createIndex(
        indexName: String,
        mapping: Document,
        settings: Params = Params(),
        aliases: Params = Params(),
        waitForActiveShards: Boolean? = null,
        masterTimeout: String? = null,
        timeout: String? = null
    ): CreateIndexResult {
        val createIndex = PreparedCreateIndex(
            indexName = indexName,
            settings = settings,
            mapping = mapping,
            aliases = aliases,
            waitForActiveShards = waitForActiveShards,
            masterTimeout = masterTimeout,
            timeout = timeout,
        )
        return transport.request(
            getCompilers().createIndex.compile(apiSerde, createIndex)
        )
    }

    suspend fun deleteIndex(
        indexName: String,
        allowNoIndices: Boolean? = null,
        masterTimeout: String? = null,
        timeout: String? = null,
    ): DeleteIndexResult {
        val request = ApiRequest(
            method = Method.DELETE,
            path = indexName,
            parameters = Parameters(
                "allow_no_indices" to allowNoIndices?.toString(),
                "master_timeout" to masterTimeout,
                "timeout" to timeout,
            ),
            body = null,
            serde = apiSerde,
            processResponse = { ctx ->
                DeleteIndexResult(
                    acknowledged = ctx.boolean("acknowledged"),
                )
            }
        )
        return transport.request(request)
    }

    suspend fun indexExists(
        indexName: String,
        allowNoIndices: Boolean? = null,
        ignoreUnavailable: Boolean? = null,
    ): Boolean {
        val request = ApiRequest(
            method = Method.HEAD,
            path = indexName,
            parameters = Parameters(
                "allow_no_indices" to allowNoIndices?.toString(),
                "ignore_unavailable" to ignoreUnavailable?.toString(),
            ),
            body = null,
            serde = apiSerde,
            processResponse = { true }
        )
        return try {
            transport.request(request)
        } catch (e: ElasticsearchException.NotFound) {
            false
        }
    }

    suspend fun updateMapping(
        indexName: String,
        mapping: Document,
        allowNoIndices: Boolean? = null,
        ignoreUnavailable: Boolean? = null,
        writeIndexOnly: Boolean? = null,
        masterTimeout: String? = null,
        timeout: String? = null,
    ): UpdateMappingResult {
        val updateMapping = PreparedUpdateMapping(
            indexName = indexName,
            mapping = mapping,
            allowNoIndices = allowNoIndices,
            ignoreUnavailable = ignoreUnavailable,
            writeIndexOnly = writeIndexOnly,
            masterTimeout = masterTimeout,
            timeout = timeout,
        )
        return transport.request(
            getCompilers().updateMapping.compile(apiSerde, updateMapping)
        )
    }

    suspend fun multiSearch(searchQueries: List<SearchQueryWithIndex<*>>): MultiSearchQueryResult {
        val compiled = getCompilers().multiSearchQuery.compile(
            bulkSerde, searchQueries
        )
        return transport.request(compiled)
    }
}

class ElasticsearchIndex(
    val name: String,
    val cluster: ElasticsearchCluster,
) {
    val transport: ElasticsearchTransport = cluster.transport

    suspend fun <S : BaseDocSource> search(
        searchQuery: BaseSearchQuery<S, *>
    ): SearchQueryResult<S> {
        val compiled = cluster.getCompilers().searchQuery.compile(
            cluster.apiSerde, searchQuery.usingIndex(name)
        )
        return transport.request(compiled)
    }

    suspend fun multiSearch(
        searchQueries: List<BaseSearchQuery<*, *>>
    ): MultiSearchQueryResult {
        return cluster.multiSearch(searchQueries.map { query -> query.usingIndex(name) })
    }

    suspend fun bulk(
        actions: List<Action<*>>,
        refresh: Refresh? = null,
        timeout: String? = null,
        params: Params = Params(),
    ): BulkResult {
        val bulk = PreparedBulk(
            name,
            actions,
            refresh = refresh,
            timeout = timeout,
            params = params,
        )
        val compiled = cluster.getCompilers()
            .bulk
            .compile(cluster.bulkSerde.serializer, bulk)
        return transport.request(
            BulkRequest(
                Method.POST,
                compiled.path,
                parameters = compiled.parameters,
                body = compiled.body.flatMap(ActionCompiler.Compiled::toList),
                serde = cluster.bulkSerde,
                processResponse = compiled.processResult,
            )
        )
    }
}
