package dev.evo.elasticmagic

import dev.evo.elasticmagic.transport.Auth

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.curl.Curl

import kotlinx.cinterop.toKString

import platform.posix.getenv

actual val httpEngine: HttpClientEngine = Curl.create {}

actual val elasticAuth: Auth? = when (val elasticPassword = getenv("ELASTIC_PASSWORD")?.toKString()) {
    null -> null
    else -> Auth.Basic("elastic", elasticPassword)
}
