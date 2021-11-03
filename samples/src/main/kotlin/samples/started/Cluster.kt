package samples.started

import dev.evo.elasticmagic.ElasticsearchCluster
import dev.evo.elasticmagic.serde.serialization.JsonSerde
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

import io.ktor.client.engine.cio.CIO

val esTransport = ElasticsearchKtorTransport(
    "http://localhost:9200",
    serde = JsonSerde,
    engine = CIO.create {}
)
val cluster = ElasticsearchCluster(esTransport)
val userIndex = cluster["user"]
