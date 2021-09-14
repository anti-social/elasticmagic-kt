package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.BulkRequest
import dev.evo.elasticmagic.compile.Compiled
import dev.evo.elasticmagic.compile.CompilerProvider
import dev.evo.elasticmagic.compile.CreateIndexRequest
import dev.evo.elasticmagic.compile.UpdateMappingRequest
import dev.evo.elasticmagic.compile.usingIndex
import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.serde.toMap
import dev.evo.elasticmagic.transport.ElasticsearchException
import dev.evo.elasticmagic.transport.ElasticsearchTransport
import dev.evo.elasticmagic.transport.Method

internal typealias Parameters = Map<String, List<String>>

internal fun Parameters(vararg params: Pair<String, Any?>): Parameters {
    val parameters = mutableMapOf<String, List<String>>()
    for ((k, v) in params) {
        val w = when (v) {
            null -> continue
            is List<*> -> v.mapNotNull(::parameterToString)
            else -> parameterToString(v)?.let { listOf(it) }
        } ?: continue
        parameters[k] = w
    }
    return parameters
}

internal fun parameterToString(v: Any?): String? {
    return when (v) {
        null -> null
        is Number -> v.toString()
        is Boolean -> v.toString()
        is CharSequence -> v.toString()
        else -> throw IllegalArgumentException(
            "Request parameter must be one of [Number, Boolean, String] but was ${v::class}"
        )
    }
}

internal fun Params.toRequestParameters(): Parameters {
    return Parameters(*this.toList().toTypedArray())
}

abstract class SerializableTransport<OBJ>(
    protected val esTransport: ElasticsearchTransport,
    protected val serde: Serde<OBJ>,
) {
    protected suspend fun <R> request(compiled: Compiled<OBJ, R>): R {
        val response = esTransport.request(
            compiled.method,
            compiled.path,
            compiled.parameters,
            contentType = serde.contentType,
        ) {
            // println("${compiled.method} ${compiled.path} ${compiled.parameters}")
            if (compiled.body != null) {
                append(serde.serializer.objToString(compiled.body))
            }
        }
        val result = serde.deserializer.objFromString(
            // Index exists API returns empty response
            response.ifBlank { "{}" }
        )
        return compiled.processResult(result)
    }
}

internal expect class CompilersHolder(
    compilers: CompilerProvider?,
    fetch: suspend () -> CompilerProvider
) {
    suspend fun get(): CompilerProvider
}

class ElasticsearchCluster<OBJ>(
    esTransport: ElasticsearchTransport,
    serde: Serde<OBJ>,
    compilers: CompilerProvider? = null,
) : SerializableTransport<OBJ>(esTransport, serde) {

    private var _compilers = CompilersHolder(compilers) {
        CompilerProvider(fetchVersion())
    }

    operator fun get(indexName: String): ElasticsearchIndex<OBJ> {
        return ElasticsearchIndex(indexName, esTransport, serde, ::getCompilers)
    }

    suspend fun fetchVersion(): ElasticsearchVersion {
        val response = esTransport.request(
            Method.GET,"",
            contentType = serde.contentType
        )
        val result = serde.deserializer.objFromString(response)
        val versionObj = result.obj("version")
        val rawEsVersion = versionObj.string("number")
        val (major, minor, patch) = rawEsVersion.split('.')
        return ElasticsearchVersion(
            major.toInt(), minor.toInt(), patch.toInt()
        )
    }

    private suspend fun getCompilers(): CompilerProvider {
        return _compilers.get()
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
        val createIndex = CreateIndexRequest(
            indexName = indexName,
            settings = settings,
            mapping = mapping,
            aliases = aliases,
            waitForActiveShards = waitForActiveShards,
            masterTimeout = masterTimeout,
            timeout = timeout,
        )
        return request(
            getCompilers().createIndex.compile(serde.serializer, createIndex)
        )
    }

    // TODO: Merge multiple mappings
    // Possibly it's worth explicitly merge multiple documents:
    // mergeDocuments(ProductDoc, CompanyDoc)
    // suspend fun createIndex(
    //     indexName: String,
    //     settings: Params,
    //     mappings: List<Document>,
    //     aliases: Params = Params(),
    //     waitForActiveShards: Boolean? = null,
    //     masterTimeout: String? = null,
    //     timeout: String? = null,
    // ): CreateIndexResult {
    //
    // }

    suspend fun deleteIndex(
        indexName: String,
        allowNoIndices: Boolean? = null,
        masterTimeout: String? = null,
        timeout: String? = null,
    ): DeleteIndexResult {
        val compiled = Compiled<OBJ, DeleteIndexResult>(
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
        return request(compiled)
    }

    suspend fun indexExists(
        indexName: String,
        allowNoIndices: Boolean? = null,
        ignoreUnavailable: Boolean? = null,
    ): Boolean {
        val compiled = Compiled<OBJ, Boolean>(
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
            request(compiled)
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
        val updateMapping = UpdateMappingRequest(
            indexName = indexName,
            mapping = mapping,
            allowNoIndices = allowNoIndices,
            ignoreUnavailable = ignoreUnavailable,
            writeIndexOnly = writeIndexOnly,
            masterTimeout = masterTimeout,
            timeout = timeout,
        )
        return request(
            getCompilers().updateMapping.compile(serde.serializer, updateMapping)
        )
    }
}

class ElasticsearchIndex<OBJ>(
    val name: String,
    esTransport: ElasticsearchTransport,
    serde: Serde<OBJ>,
    private val getCompilers: suspend () -> CompilerProvider
) : SerializableTransport<OBJ>(esTransport, serde) {

    suspend fun <S : BaseDocSource> search(
        searchQuery: BaseSearchQuery<S, *>
    ): SearchQueryResult<S> {
        val compiled = getCompilers().searchQuery.compile(
            serde.serializer, searchQuery.usingIndex(name)
        )
        return request(compiled)
    }

    suspend fun bulk(
        actions: List<Action<*>>,
        refresh: Refresh? = null,
        timeout: String? = null,
        params: Params = Params(),
    ): BulkResult {
        val bulk = BulkRequest(
            name,
            actions,
            refresh = refresh,
            timeout = timeout,
            params = params,
        )
        val compiled = getCompilers().bulk.compile(serde.serializer, bulk)
        val response = esTransport.request(
            compiled.method,
            compiled.path,
            compiled.parameters,
            contentType = "application/x-ndjson",
        ) {
            // println("${compiled.method} ${compiled.path} ${compiled.parameters}")
            if (compiled.body != null) {
                for ((header, source) in compiled.body) {
                    append(serde.serializer.objToString(header))
                    append("\n")
                    if (source != null) {
                        append(serde.serializer.objToString(source))
                        append("\n")
                    }
                }
            }
        }
        val result = serde.deserializer.objFromString(response)
        return compiled.processResult(result)
    }
}
