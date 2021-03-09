package dev.evo.elasticmagic.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.features.compression.ContentEncoding
import io.ktor.client.request.request
import io.ktor.client.statement.readText
import io.ktor.client.statement.HttpResponse
import io.ktor.content.ByteArrayContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
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
        ContentEncoding()
    }

    @Suppress("NAME_SHADOWING")
    override suspend fun request(
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

        val response = client.request<HttpResponse> {
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
                this.body = ByteArrayContent(
                    requestEncoder.toByteArray(),
                    contentType
                )
            }
        }
        return processResponse(response)
    }

    private suspend fun processResponse(response: HttpResponse): String {
        val statusCode = response.status.value
        val content = response.readText()
        throw when (statusCode) {
            in 200..299 -> return content
            400 -> ElasticsearchException.RequestError(content)
            401 -> ElasticsearchException.AuthenticationError(content)
            403 -> ElasticsearchException.AuthorizationError(content)
            404 -> ElasticsearchException.NotFoundError(content)
            409 -> ElasticsearchException.ConflictError(content)
            504 -> ElasticsearchException.GatewayTimeout(content)
            else -> ElasticsearchException.TransportError(statusCode, content)
        }
    }
}
