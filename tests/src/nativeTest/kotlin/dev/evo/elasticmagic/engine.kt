package dev.evo.elasticmagic

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.curl.Curl

actual val httpEngine: HttpClientEngine = Curl.create {}
