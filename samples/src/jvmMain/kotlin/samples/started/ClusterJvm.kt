package samples.started

import dev.evo.elasticmagic.transport.Auth
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

import io.ktor.client.engine.cio.CIO

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

actual val esTransport = ElasticsearchKtorTransport(
    System.getenv("ELASTIC_URL") ?: DEFAULT_ELASTIC_URL,
    engine = CIO.create {
        https {
            trustManager = object: X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?, authType: String?
                ) {}

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?, authType: String?
                ) {}

                override fun getAcceptedIssuers(): Array<X509Certificate>? = null
            }
        }
    }
) {
    val elasticUser = System.getenv("ELASTIC_USER") ?: DEFAULT_ELASTIC_USER
    val elasticPassword = System.getenv("ELASTIC_PASSWORD")
    if (!elasticPassword.isNullOrEmpty()) {
        auth = Auth.Basic(elasticUser, elasticPassword)
    }
}
