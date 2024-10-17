package dev.evo.elasticmagic.transport

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.content.ByteArrayContent
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.path
import io.ktor.http.takeFrom

class ElasticsearchKtorTransport(
    baseUrl: String,
    engine: HttpClientEngine,
    private val customHttpClientConfig: HttpClientConfig<*>.() -> Unit = {},
    configure: Config.() -> Unit = {},
) : ElasticsearchTransport(
    baseUrl,
    Config().apply(configure),
) {
    private val client = HttpClient(engine) {
        expectSuccess = false

        customHttpClientConfig(this)

        // Enable compressed response from Elasticsearch
        if (config.gzipRequests) {
            install(ContentEncoding) {
                gzip()
                deflate()
                identity()
            }
        }

        when (val auth = config.auth) {
            is dev.evo.elasticmagic.transport.Auth.Basic -> {
                install(Auth) {
                    basic {
                        credentials {
                            BasicAuthCredentials(auth.username, auth.password)
                        }
                        sendWithoutRequest { true }
                    }
                }
            }
            null -> {}
        }
    }

    override suspend fun doRequest(
        request: PlainRequest,
    ): PlainResponse {
        val ktorHttpMethod = when (request.method) {
            Method.GET -> HttpMethod.Get
            Method.PUT -> HttpMethod.Put
            Method.POST -> HttpMethod.Post
            Method.DELETE -> HttpMethod.Delete
            Method.HEAD -> HttpMethod.Head
        }
        val ktorParameters = if (request.parameters.isNotEmpty()) {
            Parameters.build {
                request.parameters.forEach { (name, values) ->
                    appendAll(name, values)
                }
            }
        } else {
            Parameters.Empty
        }

        val response = client.request {
            this.method = ktorHttpMethod
            url {
                takeFrom(baseUrl)
                this.path(request.path)
                this.parameters.appendAll(ktorParameters)
            }

            request.contentEncoding?.let { encoding ->
                this.headers[HttpHeaders.ContentEncoding] = encoding
            }

            request.acceptContentType?.let { acceptContentType ->
                this.headers[HttpHeaders.Accept] = acceptContentType
            }

            val content = request.content
            if (content.isNotEmpty()) {
                this.setBody(ByteArrayContent(content, ContentType.parse(request.contentType)))
            }
        }
        val contentType = response.contentType()?.let { "${it.contentType}/${it.contentSubtype}"}
        return PlainResponse(
            response.status.value,
            response.headers.entries().associate { entry -> entry.key.lowercase() to entry.value },
            contentType,
            response.bodyAsText(),
         )
    }
}
