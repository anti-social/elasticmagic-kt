package dev.evo.elasticmagic

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

actual val httpEngine: HttpClientEngine = Js.create {}
