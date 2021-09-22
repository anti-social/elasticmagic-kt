package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.serialization.JsonSerde
import dev.evo.elasticmagic.transport.ElasticsearchException
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

@Suppress("UnnecessaryAbstractClass")
abstract class ElasticsearchTestBase(indexName: String) : TestBase() {
    protected val esTransport = ElasticsearchKtorTransport(
        "http://localhost:9200",
        deserializer = JsonSerde.deserializer,
        engine = httpEngine
    )
    protected val cluster = ElasticsearchCluster(esTransport, JsonSerde)
    protected val index = cluster["elasticmagic-tests_$indexName"]

    protected suspend fun withTestIndex(vararg mappings: Document, block: suspend () -> Unit) {
        cluster.createIndex(
            index.name,
            mapping = mergeDocuments(*mappings),
            settings = Params(
                "index.number_of_replicas" to 0,
            ),
        )
        try {
            block()
        } finally {
            cluster.deleteIndex(index.name)
        }
    }

    protected suspend fun ensureIndex(vararg mappings: Document) {
        if (!cluster.indexExists(index.name)) {
            cluster.createIndex(
                index.name,
                mapping = mergeDocuments(*mappings),
                settings = Params(
                    "index.number_of_replicas" to 0,
                ),
            )
        } else {
            cluster.updateMapping(index.name, mapping = OrderDoc)
        }
    }

    protected suspend fun withFixtures(
        mapping: Document,
        fixtures: List<DocSourceAndMeta>,
        block: suspend () -> Unit
    ) {
        withFixtures(listOf(mapping), fixtures, block)
    }

    protected suspend fun withFixtures(
        mappings: List<Document>,
        fixtures: List<DocSourceAndMeta>,
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
            index.bulk(deleteActions, refresh = Refresh.TRUE)
        }
    }
}
