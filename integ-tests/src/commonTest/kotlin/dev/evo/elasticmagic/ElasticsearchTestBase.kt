package dev.evo.elasticmagic

import dev.evo.elasticmagic.bulk.Action
import dev.evo.elasticmagic.bulk.DeleteAction
import dev.evo.elasticmagic.bulk.DocSourceAndMeta
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.bulk.IndexAction
import dev.evo.elasticmagic.doc.mergeDocuments
import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.transport.ElasticsearchException
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport
import dev.evo.elasticmagic.transport.PlainRequest
import dev.evo.elasticmagic.transport.PlainResponse
import dev.evo.elasticmagic.transport.Request
import dev.evo.elasticmagic.transport.Tracker
import kotlin.time.Duration
import kotlinx.atomicfu.atomic

@Suppress("UnnecessaryAbstractClass")
abstract class ElasticsearchTestBase : TestBase() {
    abstract val indexName: String

    protected val debug = atomic(false)
    protected val transport = ElasticsearchKtorTransport(
        elasticUrl,
        engine = httpEngine,
    ) {
        if (elasticAuth != null) {
            auth = elasticAuth
        }

        trackers = listOf({
            @Suppress("ForbiddenMethodCall")
            object : Tracker {
                override fun requiresTextContent(request: Request<*, *, *>) = true

                override fun onRequest(request: PlainRequest) {
                    if (!debug.value) {
                        return
                    }
                    println(">>>")
                    val queryParams = request.parameters
                        .flatMap { (k, v) -> v.map { w -> "$k=$w" } }
                        .joinToString("&")
                    println("${request.method} ${request.path.ifEmpty { "/" }}?${queryParams}")
                    println(request.textContent)
                    println()
                }

                override fun onResponse(responseResult: Result<PlainResponse>, duration: Duration) {
                    if (!debug.value) {
                        return
                    }
                    responseResult
                        .onSuccess { response ->
                            println("<<< ${response.statusCode}: ${duration}")
                            response.headers.forEach { header ->
                                println("< ${header.key}: ${header.value}")
                            }
                            println(response.contentType)
                            println(response.content)
                            println()
                        }
                        .onFailure { exception ->
                            println("!!! $exception")
                            println()
                        }
                }
            }
        })
    }

    protected fun runTestWithSerdes(
        serdes: List<Serde>,
        debug: Boolean = false,
        block: suspend TestScope.() -> Unit
    ) {
        this.debug.value = debug

        for (apiSerde in serdes) {
            val bulkSerde = if (apiSerde is Serde.OneLineJson) {
                apiSerde
            } else {
                defaultBulkSerde
            }
            val cluster = ElasticsearchCluster(transport, apiSerde = apiSerde, bulkSerde = bulkSerde)
            val index = cluster["elasticmagic-tests_$indexName"]
            val testScope = TestScope(cluster, index)

            runTest { testScope.block() }
        }
    }

    protected fun runTestWithSerdes(debug: Boolean = false, block: suspend TestScope.() -> Unit) {
        runTestWithSerdes(apiSerdes, debug = debug, block)
    }

    protected data class TestScope(
        val cluster: ElasticsearchCluster,
        val index: ElasticsearchIndex,
    ) {
        suspend fun withTestIndex(
            vararg mappings: Document,
            block: suspend () -> Unit
        ) {
            if (cluster.indexExists(index.name)) {
                cluster.deleteIndex(index.name)
            }
            cluster.createIndex(
                index.name,
                mapping = mergeDocuments(*mappings),
                settings = Params(
                    "index.number_of_shards" to 1,
                    "index.number_of_replicas" to 0,
                ),
            )
            block()
        }

        suspend fun ensureIndex(vararg mappings: Document) {
            if (!cluster.indexExists(index.name)) {
                cluster.createIndex(
                    index.name,
                    mapping = mergeDocuments(*mappings),
                    settings = Params(
                        "index.number_of_shards" to 1,
                        "index.number_of_replicas" to 0,
                    ),
                )
            } else {
                cluster.updateMapping(index.name, mapping = OrderDoc)
            }
        }

        suspend fun TestScope.withFixtures(
            mapping: Document,
            fixtures: List<DocSourceAndMeta<*>>,
            cleanup: Boolean = true,
            block: suspend () -> Unit
        ) {
            withFixtures(listOf(mapping), fixtures, cleanup, block)
        }

        suspend fun TestScope.withFixtures(
            mappings: List<Document>,
            fixtures: List<DocSourceAndMeta<*>>,
            cleanup: Boolean = true,
            block: suspend () -> Unit
        ) {
            ensureIndex(*mappings.toTypedArray())

            val indexActions = fixtures.map { docAndMeta ->
                IndexAction(
                    docAndMeta.meta,
                    docAndMeta.doc,
                )
            }
            val bulkResult = index.bulk(indexActions, refresh = Refresh.TRUE)
            val deleteActions = mutableListOf<Action<*>>()
            val failedItems = mutableListOf<BulkError>()
            for (bulkItem in bulkResult.items) {
                when (bulkItem) {
                    is BulkItem.Ok -> {
                        deleteActions.add(DeleteAction(bulkItem))
                    }
                    is BulkItem.Error -> {
                        failedItems.add(bulkItem.error)
                    }
                }
            }
            if (failedItems.isNotEmpty()) {
                throw ElasticsearchException(
                    failedItems.joinToString("\n") { bulkErr ->
                        "${bulkErr.type} - ${bulkErr.reason}"
                    }
                )
            }
            try {
                block()
            } finally {
                if (cleanup) {
                    index.bulk(deleteActions, refresh = Refresh.TRUE)
                }
            }
        }
    }
}
