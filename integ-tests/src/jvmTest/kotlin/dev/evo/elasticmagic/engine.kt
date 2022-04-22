package dev.evo.elasticmagic

import dev.evo.elasticmagic.transport.Auth

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

actual val httpEngine: HttpClientEngine = CIO.create {}

actual val elasticAuth: Auth? = when (val elasticPassword = System.getenv("ELASTIC_PASSWORD")) {
    null -> null
    else -> Auth.Basic("elastic", elasticPassword)
}
