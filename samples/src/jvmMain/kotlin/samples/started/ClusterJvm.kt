package samples.started

import dev.evo.elasticmagic.serde.serialization.JsonSerde
import dev.evo.elasticmagic.transport.Auth
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

import io.ktor.client.engine.cio.CIO

actual val esTransport = ElasticsearchKtorTransport(
    System.getenv("ELASTIC_URL") ?: "http://localhost:9200",
    serde = JsonSerde,
    engine = CIO.create {}
) {
    val elasticPassword = System.getenv("ELASTIC_PASSWORD")
    if (!elasticPassword.isNullOrEmpty()) {
        auth = Auth.Basic("elastic", elasticPassword)
    }
}
