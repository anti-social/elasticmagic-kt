package samples.started

import dev.evo.elasticmagic.ElasticsearchCluster
import dev.evo.elasticmagic.serde.serialization.JsonSerde
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

import io.ktor.client.engine.curl.Curl

actual val esTransport = ElasticsearchKtorTransport(
    "http://localhost:9200",
    serde = JsonSerde,
    engine = Curl.create {}
)
actual val cluster = ElasticsearchCluster(esTransport)
actual val userIndex = cluster["user"]
