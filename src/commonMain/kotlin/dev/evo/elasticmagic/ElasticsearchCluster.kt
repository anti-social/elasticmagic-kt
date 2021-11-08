package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.ActionCompiler
import dev.evo.elasticmagic.compile.PreparedBulk
import dev.evo.elasticmagic.compile.CompilerSet
import dev.evo.elasticmagic.compile.PreparedCreateIndex
import dev.evo.elasticmagic.compile.PreparedUpdateMapping
import dev.evo.elasticmagic.compile.usingIndex
import dev.evo.elasticmagic.bulk.Action
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.bulk.Refresh
import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.transport.Request
import dev.evo.elasticmagic.transport.ElasticsearchException
import dev.evo.elasticmagic.transport.ElasticsearchTransport
import dev.evo.elasticmagic.transport.Method
import dev.evo.elasticmagic.transport.Parameters

import kotlinx.coroutines.CompletableDeferred

internal fun Params.toRequestParameters(): Parameters {
    return Parameters(*this.toList().toTypedArray())
}

class ElasticsearchCluster(
    val transport: ElasticsearchTransport,
    serde: Serde? = null,
    private val compilers: CompilerSet? = null,
) {
    val serde = serde ?: transport.serde

    private val esVersion = CompletableDeferred<ElasticsearchVersion>()
    private val sniffedCompilers = CompletableDeferred<CompilerSet>()

    operator fun get(indexName: String): ElasticsearchIndex {
        return ElasticsearchIndex(indexName, transport, serde, this)
    }

    private suspend fun fetchVersion(): ElasticsearchVersion {
        val result = transport.request(
            Request(Method.GET,"")
        )
        val versionObj = result.obj("version")
        val rawEsVersion = versionObj.string("number")
        val (major, minor, patch) = rawEsVersion.split('.')
        return ElasticsearchVersion(
            major.toInt(), minor.toInt(), patch.toInt()
        )
    }

    suspend fun getVersion(): ElasticsearchVersion {
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
            getCompilers().createIndex.compile(serde.serializer, createIndex)
        )
    }

    suspend fun deleteIndex(
        indexName: String,
        allowNoIndices: Boolean? = null,
        masterTimeout: String? = null,
        timeout: String? = null,
    ): DeleteIndexResult {
        val request = Request(
            method = Method.DELETE,
            path = indexName,
            parameters = Parameters(
                "allow_no_indices" to allowNoIndices?.toString(),
                "master_timeout" to masterTimeout,
                "timeout" to timeout,
            ),
            body = null,
            processResult = { ctx ->
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
        val request = Request(
            method = Method.HEAD,
            path = indexName,
            parameters = Parameters(
                "allow_no_indices" to allowNoIndices?.toString(),
                "ignore_unavailable" to ignoreUnavailable?.toString(),
            ),
            body = null,
            processResult = { true }
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
            getCompilers().updateMapping.compile(serde.serializer, updateMapping)
        )
    }
}

class ElasticsearchIndex(
    val name: String,
    private val transport: ElasticsearchTransport,
    private val serde: Serde,
    val cluster: ElasticsearchCluster,
) {

    suspend fun <S : BaseDocSource> search(
        searchQuery: BaseSearchQuery<S, *>
    ): SearchQueryResult<S> {
        val compiled = cluster.getCompilers().searchQuery.compile(
            serde.serializer, searchQuery.usingIndex(name)
        )
        return transport.request(compiled)
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
        val compiled = cluster.getCompilers().bulk.compile(serde.serializer, bulk)
        return transport.bulkRequest(
            Request(
                compiled.method,
                compiled.path,
                parameters = compiled.parameters,
                body = compiled.body?.flatMap(ActionCompiler.Compiled::toList),
                processResult = compiled.processResult,
            )
        )
    }
}
