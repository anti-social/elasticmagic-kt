package dev.evo.elasticmagic.transport

import dev.evo.elasticmagic.serde.DeserializationException
import dev.evo.elasticmagic.serde.Serde

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.content.ByteArrayContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.path
import io.ktor.http.takeFrom

class ElasticsearchKtorTransport(
    baseUrl: String,
    serde: Serde,
    engine: HttpClientEngine,
    configure: Config.() -> Unit = {},
) : ElasticsearchTransport(
    baseUrl,
    serde,
    Config().apply(configure),
) {
    private val client = HttpClient(engine) {
        expectSuccess = false

        // Enable compressed response from Elasticsearch
        install(ContentEncoding)

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

    companion object {
        private val HTTP_OK_CODES = 200..299
    }

    @Suppress("NAME_SHADOWING")
    override suspend fun doRequest(
        method: Method,
        path: String,
        parameters: Map<String, List<String>>?,
        contentType: String?,
        bodyBuilder: RequestBodyBuilder?
    ): String {
        val ktorHttpMethod = when (method) {
            Method.GET -> HttpMethod.Get
            Method.PUT -> HttpMethod.Put
            Method.POST -> HttpMethod.Post
            Method.DELETE -> HttpMethod.Delete
            Method.HEAD -> HttpMethod.Head
        }
        val ktorParameters = if (parameters != null) {
            Parameters.build {
                parameters.forEach { (name, values) ->
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
                this.path(path)
                if (parameters != null) {
                    this.parameters.appendAll(ktorParameters)
                }
            }
            if (bodyBuilder != null) {
                val requestEncoder = requestEncoderFactory.create().apply(bodyBuilder)
                requestEncoderFactory.encoding?.let { encoding ->
                    this.headers[HttpHeaders.ContentEncoding] = encoding
                }

                val contentType = if (contentType != null) {
                    val (contentType, contentSubType) = contentType.split('/', limit = 2)
                    ContentType(contentType, contentSubType)
                } else {
                    ContentType.Application.Json
                }
                val content = requestEncoder.toByteArray()
                if (content.isNotEmpty()) {
                    this.setBody(ByteArrayContent(content, contentType))
                }
            }
        }
        return processResponse(response)
    }

    private suspend fun processResponse(response: HttpResponse): String {
        val statusCode = response.status.value
        val content = response.bodyAsText()
        return when (statusCode) {
            in HTTP_OK_CODES -> content
            else -> {
                val jsonError = try {
                    serde.deserializer.objFromStringOrNull(content)
                } catch (e: DeserializationException) {
                    null
                }
                val transportError = if (jsonError != null) {
                    TransportError.parse(jsonError)
                } else {
                    TransportError.Simple(content)
                }
                throw ElasticsearchException.Transport.fromStatusCode(
                    statusCode, transportError
                )
            }
        }
    }
}