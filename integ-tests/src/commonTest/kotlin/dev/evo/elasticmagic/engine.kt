package dev.evo.elasticmagic

import dev.evo.elasticmagic.transport.Auth

import io.ktor.client.engine.HttpClientEngine

expect val httpEngine: HttpClientEngine

expect val elasticAuth: Auth?
