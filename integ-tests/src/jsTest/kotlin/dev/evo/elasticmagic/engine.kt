package dev.evo.elasticmagic

import dev.evo.elasticmagic.transport.Auth

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

external val process: Process

external interface Process {
    val env: ProcessEnvVariables
}

external object ProcessEnvVariables {
    val ELASTIC_PASSWORD: String?
}

actual val httpEngine: HttpClientEngine = Js.create {}

actual val elasticAuth: Auth? = when (val elasticPassword = process.env.ELASTIC_PASSWORD) {
    null -> null
    else -> Auth.Basic("elastic", elasticPassword)
}
