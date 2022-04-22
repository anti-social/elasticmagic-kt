package samples.started

import dev.evo.elasticmagic.ElasticsearchCluster
import dev.evo.elasticmagic.serde.serialization.JsonSerde
import dev.evo.elasticmagic.transport.Auth
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

import io.ktor.client.engine.curl.Curl

import kotlinx.cinterop.toKString

import platform.posix.getenv

actual val esTransport = ElasticsearchKtorTransport(
    "http://localhost:9200",
    serde = JsonSerde,
    engine = Curl.create {}
) {
    val elasticPassword = getenv("ELASTIC_PASSWORD")?.toKString()
    if (!elasticPassword.isNullOrEmpty()) {
        auth = Auth.Basic("elastic", elasticPassword)
    }
}
actual val cluster = ElasticsearchCluster(esTransport)
actual val userIndex = cluster["user"]
