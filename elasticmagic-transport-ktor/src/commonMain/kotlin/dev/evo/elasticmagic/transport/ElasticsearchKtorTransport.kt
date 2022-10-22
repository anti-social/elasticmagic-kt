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
    engine: HttpClientEngine,
    configure: Config.() -> Unit = {},
) : ElasticsearchTransport(
    baseUrl,
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
    override suspend fun doRequest(request: Request<*, *, *>): String {
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

            val requestEncoder = requestEncoderFactory.create().apply(request::serializeRequest)
            requestEncoderFactory.encoding?.let { encoding ->
                this.headers[HttpHeaders.ContentEncoding] = encoding
            }

            request.acceptContentType?.let { acceptContentType ->
                this.headers[HttpHeaders.Accept] = acceptContentType
            }

            val content = requestEncoder.toByteArray()
            if (content.isNotEmpty()) {
                this.setBody(ByteArrayContent(content, ContentType.parse(request.contentType)))
            }
        }
        return processResponse(response, request.errorSerde)
    }

    private suspend fun processResponse(response: HttpResponse, errorSerde: Serde): String {
        val statusCode = response.status.value
        val content = response.bodyAsText()
        return when (statusCode) {
            in HTTP_OK_CODES -> content
            else -> {
                val jsonError = try {
                    errorSerde.deserializer.objFromStringOrNull(content)
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
