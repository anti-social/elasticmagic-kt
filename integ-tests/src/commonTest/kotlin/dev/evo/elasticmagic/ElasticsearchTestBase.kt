package dev.evo.elasticmagic

import dev.evo.elasticmagic.bulk.Action
import dev.evo.elasticmagic.bulk.DeleteAction
import dev.evo.elasticmagic.bulk.DocSourceAndMeta
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.bulk.IndexAction
import dev.evo.elasticmagic.bulk.Refresh
import dev.evo.elasticmagic.doc.mergeDocuments
import dev.evo.elasticmagic.transport.ElasticsearchException
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

@Suppress("UnnecessaryAbstractClass")
abstract class ElasticsearchTestBase : TestBase() {
    abstract val indexName: String

    protected fun runTestWithTransports(block: suspend TestScope.() -> Unit) {
        for (serde in serdes) {
            val transport = ElasticsearchKtorTransport(
                "http://localhost:9200",
                serde = serde,
                engine = httpEngine,
            ) {
                if (elasticAuth != null) {
                    auth = elasticAuth
                }
            }
            val cluster = ElasticsearchCluster(transport)
            val index = cluster["elasticmagic-tests_$indexName"]
            val testScope = TestScope(cluster, index)

            runTest { testScope.block() }
        }
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
