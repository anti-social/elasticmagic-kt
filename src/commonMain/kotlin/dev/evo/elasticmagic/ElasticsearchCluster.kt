package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.Compiled
import dev.evo.elasticmagic.compile.CompilerProvider
import dev.evo.elasticmagic.compile.CreateIndex
import dev.evo.elasticmagic.compile.usingIndex
import dev.evo.elasticmagic.serde.Serde
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
            contentType = serde.contentType,
        ) {
            val body = compiled.body
            if (compiled.body != null) {
                append(serde.serializer.objToString(compiled.body))
            }
        }
        val result = serde.deserializer.objFromString(response)
        return compiled.processResult(result)
    }
}

class ElasticsearchCluster<OBJ>(
    esTransport: ElasticsearchTransport,
    serde: Serde<OBJ>,
    private val compilers: CompilerProvider,
) : SerializableTransport<OBJ>(esTransport, serde) {

    operator fun get(indexName: String): ElasticsearchIndex<OBJ> {
        return ElasticsearchIndex(indexName, esTransport, serde, compilers)
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
        val createIndex = CreateIndex(
            indexName = indexName,
            settings = settings,
            mapping = mapping,
            aliases = aliases,
            waitForActiveShards = waitForActiveShards,
            masterTimeout = masterTimeout,
            timeout = timeout,
        )
        return request(
            compilers.createIndex.compile(serde.serializer, createIndex)
        )
    }

    // TODO: Merge multiple mappings
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
}

class ElasticsearchIndex<OBJ>(
    val indexName: String,
    esTransport: ElasticsearchTransport,
    serde: Serde<OBJ>,
    private val compilers: CompilerProvider,
) : SerializableTransport<OBJ>(esTransport, serde) {

    suspend fun <S : BaseSource> search(
        searchQuery: BaseSearchQuery<S, *>
    ): SearchQueryResult<S> {
        val compiled = compilers.searchQuery.compile(
            serde.serializer, searchQuery.usingIndex(indexName)
        )
        return request(compiled)
    }
}
