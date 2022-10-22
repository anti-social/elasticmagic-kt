package samples.started

import dev.evo.elasticmagic.serde.serialization.JsonSerde
import dev.evo.elasticmagic.transport.Auth
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

import io.ktor.client.engine.curl.Curl

import kotlinx.cinterop.toKString

import platform.posix.getenv

actual val esTransport = ElasticsearchKtorTransport(
    getenv("ELASTIC_URL")?.toKString() ?: DEFAULT_ELASTIC_URL,
    engine = Curl.create {
        sslVerify = false
    }
) {
    val elasticUser = getenv("ELASTIC_USER")?.toKString() ?: DEFAULT_ELASTIC_USER
    val elasticPassword = getenv("ELASTIC_PASSWORD")?.toKString()
    if (!elasticPassword.isNullOrEmpty()) {
        auth = Auth.Basic(elasticUser, elasticPassword)
    }
}
