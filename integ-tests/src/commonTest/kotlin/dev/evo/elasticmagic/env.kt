package dev.evo.elasticmagic

import dev.evo.elasticmagic.transport.Auth

expect fun getenv(name: String): String?

const val DEFAULT_ELASTIC_URL = "http://localhost:9200"
const val DEFAULT_ELASTIC_USER = "elastic"

val elasticUrl = getenv("ELASTIC_URL") ?: DEFAULT_ELASTIC_URL

val elasticAuth = when (val elasticPassword = getenv("ELASTIC_PASSWORD")) {
    null -> null
    else -> Auth.Basic(
        getenv("ELASTIC_USER") ?: DEFAULT_ELASTIC_USER,
        elasticPassword
    )
}
