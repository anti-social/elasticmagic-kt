package dev.evo.elasticmagic

import dev.evo.elasticmagic.transport.Auth

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

actual val elasticUrl: String = System.getenv("ELASTIC_URL") ?: DEFAULT_ELASTIC_URL

actual val httpEngine: HttpClientEngine = CIO.create {
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

actual val elasticAuth: Auth? = when (val elasticPassword = System.getenv("ELASTIC_PASSWORD")) {
    null -> null
    else -> Auth.Basic(
        System.getenv("ELASTIC_USER") ?: DEFAULT_ELASTIC_USER,
        elasticPassword
    )
}
