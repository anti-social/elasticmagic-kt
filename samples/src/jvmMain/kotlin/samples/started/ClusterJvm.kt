package samples.started

import dev.evo.elasticmagic.transport.Auth
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport
import dev.evo.elasticmagic.transport.Request
import dev.evo.elasticmagic.transport.Response
import dev.evo.elasticmagic.transport.PlainRequest
import dev.evo.elasticmagic.transport.PlainResponse
import dev.evo.elasticmagic.transport.Tracker

import io.ktor.client.engine.cio.CIO

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

import kotlin.time.Duration
import kotlin.getOrThrow

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

    if (System.getenv("ELASTICMAGIC_DEBUG") != null) {
        trackers = listOf {
            object : Tracker {
                override fun requiresTextContent(request: Request<*, *, *>) = true

                override suspend fun onRequest(request: PlainRequest) {
                    println(">>>")
                    println("${request.method} ${request.path.ifEmpty { "/" }}")
                    println(request.textContent)
                }

                override suspend fun onResponse(responseResult: Result<PlainResponse>, duration: Duration) {
                    responseResult
                        .onSuccess { response ->
                            println("<<< ${response.statusCode}: ${duration}")
                            response.headers.forEach { header ->
                                println("< ${header.key}: ${header.value}")
                            }
                            println(response.contentType)
                            println(response.content)
                        }
                        .onFailure { exception ->
                            println("!!! $exception")
                        }
                }
            }
        }
    }
}
