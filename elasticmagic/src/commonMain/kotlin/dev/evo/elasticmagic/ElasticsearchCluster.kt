package dev.evo.elasticmagic

import dev.evo.elasticmagic.bulk.Action
import dev.evo.elasticmagic.compile.ActionCompiler
import dev.evo.elasticmagic.compile.CompilerSet
import dev.evo.elasticmagic.compile.ElasticsearchFeatures
import dev.evo.elasticmagic.compile.PreparedBulk
import dev.evo.elasticmagic.compile.PreparedCreateIndex
import dev.evo.elasticmagic.compile.PreparedUpdateMapping
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.transport.ApiRequest
import dev.evo.elasticmagic.transport.BulkRequest
import dev.evo.elasticmagic.transport.ElasticsearchException
import dev.evo.elasticmagic.transport.ElasticsearchTransport
import dev.evo.elasticmagic.transport.Method
import dev.evo.elasticmagic.transport.Parameters
import dev.evo.elasticmagic.util.toTimeoutString
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

internal fun Params.toRequestParameters(): Parameters {
    val entries = this.map { (key, value) ->
        key to when (value) {
            is ToValue<*> -> value.toValue()
            is Duration -> {
                if (key == "timeout") {
                    value.toTimeoutString()
                } else {
                    value
                }
            }

            else -> value
        }
    }
    return Parameters(*entries.toTypedArray())
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
    ) : this(
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
            ApiRequest(Method.GET, "", serde = apiSerde)
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
            sniffedCompilers.complete(
                CompilerSet(ElasticsearchFeatures(getVersion()))
            )
        }
        return sniffedCompilers.await()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun ping(): PingResult {
        val request = ApiRequest(
            method = Method.HEAD,
            path = "",
            body = null,
            serde = apiSerde,
            processResponse = { resp -> resp.statusCode }
        )
        val (statusCode, responseTime) = measureTimedValue {
            try {
                transport.request(request)
            } catch (ex: ElasticsearchException.Transport) {
                ex.statusCode
            }
        }
        return PingResult(statusCode, responseTime.inWholeMilliseconds)
    }

    suspend fun <P, T> checkAsyncResult(
        task: AsyncResult<P, T>,
    ): TaskResult<P, T> {
        return checkAsyncResult(
            task,
            waitForCompletion = false,
        )
    }

    suspend fun <P, T> waitAsyncResult(
        task: AsyncResult<P, T>,
    ): TaskResult<P, T & Any> {
        return checkAsyncResult(
            AsyncResult(
                task.task,
                task.createStatus,
                createResponse = { ctx ->
                    task.createResponse(ctx) ?: error("Missing [response] key in a task result")
                }
            ),
            waitForCompletion = true,
        )
    }

    private suspend fun <P, T> checkAsyncResult(
        task: AsyncResult<P, T>,
        waitForCompletion: Boolean,
    ): TaskResult<P, T> {
        return transport.request(
            ApiRequest(
                method = Method.GET,
                path = "_tasks/${task.task}",
                parameters = Parameters(
                    "wait_for_completion" to waitForCompletion,
                ),
                body = null,
                serde = apiSerde,
                processResponse = { resp ->
                    val ctx = resp.content
                    val taskCtx = ctx.obj("task")
                    TaskResult(
                        completed = ctx.boolean("completed"),
                        task = TaskInfo(
                            node = taskCtx.string("node"),
                            id = taskCtx.long("id"),
                            type = taskCtx.string("type"),
                            action = taskCtx.string("action"),
                            status = task.createStatus(ctx),
                            description = taskCtx.string("description"),
                            startTimeInMillis = taskCtx.long("start_time_in_millis"),
                            runningTimeInNanos = taskCtx.long("running_time_in_nanos"),
                            cancellable = taskCtx.boolean("cancellable"),
                        ),
                        response = task.createResponse(ctx),
                    )
                }
            )
        )
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
            processResponse = { resp ->
                DeleteIndexResult(
                    acknowledged = resp.content.boolean("acknowledged"),
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

    suspend fun multiSearch(searchQueries: List<WithIndex<SearchQuery.Search<*>>>): MultiSearchQueryResult {
        val compiled = getCompilers().multiSearchQuery.compile(
            bulkSerde, searchQueries
        )
        return transport.request(compiled)
    }
}

data class WithIndex<out T>(
    val indexName: String,
    val request: T,
)

fun <T> T.withIndex(indexName: String): WithIndex<T> {
    return WithIndex(indexName, this)
}

class ElasticsearchIndex(
    val name: String,
    val cluster: ElasticsearchCluster,
) {
    val transport: ElasticsearchTransport = cluster.transport

    suspend fun <S : BaseDocSource> search(
        searchQuery: SearchQuery.Search<S>
    ): SearchQueryResult<S> {
        val compiled = cluster.getCompilers().searchQuery.compile<S>(
            cluster.apiSerde, searchQuery.withIndex(name)
        )
        return transport.request(compiled)
    }

    suspend fun multiSearch(
        searchQueries: List<SearchQuery.Search<*>>
    ): MultiSearchQueryResult {
        return cluster.multiSearch(
            searchQueries.map { query -> WithIndex(name, query) }
        )
    }

    suspend fun count(searchQuery: SearchQuery.Count): CountResult {
        val compiled = cluster.getCompilers().countQuery.compile(
            cluster.apiSerde, searchQuery.withIndex(name)
        )
        return transport.request(compiled)
    }

    suspend fun updateByQuery(
        searchQuery: SearchQuery.Update,
    ): UpdateByQueryResult {
        val compiled = cluster.getCompilers().updateByQuery.compile(
            cluster.apiSerde, searchQuery.withIndex(name)
        )
        return transport.request(compiled)
    }

    suspend fun updateByQueryAsync(
        searchQuery: SearchQuery.Update,
    ): AsyncResult<UpdateByQueryPartialResult, UpdateByQueryResult?> {
        val compiled = cluster.getCompilers().updateByQuery.compileAsync(
            cluster.apiSerde, searchQuery.withIndex(name)
        )
        return transport.request(compiled)
    }

    suspend fun deleteByQuery(
        searchQuery: SearchQuery.Delete,
    ): DeleteByQueryResult {
        val compiled = cluster.getCompilers().deleteByQuery.compile(
            cluster.apiSerde, searchQuery.withIndex(name)
        )
        return transport.request(compiled)
    }

    suspend fun deleteByQueryAsync(
        searchQuery: SearchQuery.Delete,
    ): AsyncResult<DeleteByQueryPartialResult, DeleteByQueryResult?> {
        val compiled = cluster.getCompilers().deleteByQuery.compileAsync(
            cluster.apiSerde, searchQuery.withIndex(name)
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

    suspend fun refresh(
        ignoreUnavailable: Boolean? = null,
        params: Params? = null,
    ): PersistResult {
        val params = Params(
            params,
            "ignore_unavailable" to ignoreUnavailable,
        )
        return transport.request(
            ApiRequest(
                Method.POST,
                "/${name}/_refresh",
                params.toRequestParameters(),
                serde = cluster.apiSerde,
                processContent = PersistResult::invoke,
            )
        )
    }

    suspend fun forceMerge(
        ignoreUnavailable: Boolean? = null,
        maxNumSegments: Int? = null,
        onlyExpungeDeletes: Boolean? = null,
        primaryOnly: Boolean? = null,
        params: Params? = null,
    ): PersistResult {
        val params = Params(
            params,
            "ignore_unavailable" to ignoreUnavailable,
            "max_num_segments" to maxNumSegments,
            "only_expunge_deletes" to onlyExpungeDeletes,
            "primary_only" to primaryOnly,
        )
        return transport.request(
            ApiRequest(
                Method.POST,
                "/${name}/_forcemerge",
                params.toRequestParameters(),
                serde = cluster.apiSerde,
                processContent = PersistResult::invoke,
            )
        )
    }
}
