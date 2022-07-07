package dev.evo.elasticmagic

import dev.evo.elasticmagic.transport.Auth

import io.ktor.client.engine.HttpClientEngine

const val DEFAULT_ELASTIC_URL = "http://localhost:9200"
const val DEFAULT_ELASTIC_USER = "elastic"

expect val elasticUrl: String

expect val httpEngine: HttpClientEngine

expect val elasticAuth: Auth?
