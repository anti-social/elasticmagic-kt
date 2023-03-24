package dev.evo.elasticmagic

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

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
