package samples.started

import dev.evo.elasticmagic.transport.Auth
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport
import dev.evo.elasticmagic.transport.Response

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

    if (System.getenv("ELASTICMAGIC_DEBUG") != null) {
        hooks = listOf { request, response, duration ->
            println(">>>")
            println("${request.method} ${request.path.ifEmpty { "/" }}")
            println(request.encodeToString())
            when (response) {
                is Response.Ok -> {
                    println("<<< ${response.statusCode}: ${duration}")
                    response.headers.forEach { header ->
                        println("< ${header.key}: ${header.value}")
                    }
                    println(response.contentType)
                    println(response.content)
                }
                is Response.Error -> {
                    println("<<< ${response.statusCode}: ${duration}")
                    response.headers.forEach { header ->
                        println("< ${header.key}: ${header.value}")
                    }
                    println(response.error)
                }
                is Response.Exception -> {
                    println("!!! ${response.cause}")
                }
            }
            println("===")
            println()
        }
    }
}
