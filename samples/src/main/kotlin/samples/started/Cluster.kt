package samples.started

import dev.evo.elasticmagic.ElasticsearchCluster
import dev.evo.elasticmagic.serde.serialization.JsonSerde
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

import io.ktor.client.engine.cio.CIO

val esTransport = ElasticsearchKtorTransport(
    "http://localhost:9200",
    deserializer = JsonSerde.deserializer,
    engine = CIO.create {}
)
val cluster = ElasticsearchCluster(esTransport, JsonSerde)
val userIndex = cluster["user"]
