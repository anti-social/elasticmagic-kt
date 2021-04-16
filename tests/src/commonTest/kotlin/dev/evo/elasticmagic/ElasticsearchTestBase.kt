package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.serialization.JsonSerde
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

abstract class ElasticsearchTestBase(indexName: String) : TestBase() {
    protected val esTransport = ElasticsearchKtorTransport(
        "http://localhost:9200",
        deserializer = JsonSerde.deserializer,
        engine = httpEngine
    )
    protected val cluster = ElasticsearchCluster(esTransport, JsonSerde)
    protected val index = cluster[indexName]
}