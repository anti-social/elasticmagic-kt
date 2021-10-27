package dev.evo.elasticmagic.transport

import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.serde.Serializer

enum class Method {
    GET, PUT, POST, DELETE, HEAD
}

typealias Parameters = Map<String, List<String>>

fun Parameters(vararg params: Pair<String, Any?>): Parameters {
    val parameters = mutableMapOf<String, List<String>>()
    for ((k, v) in params) {
        val w = when (v) {
            null -> continue
            is List<*> -> v.mapNotNull(::parameterToString)
            else -> parameterToString(v)?.let { listOf(it) }
        } ?: continue
        parameters[k] = w
    }
    return parameters
}

fun parameterToString(v: Any?): String? {
    return when (v) {
        null -> null
        is Number -> v.toString()
        is Boolean -> v.toString()
        is CharSequence -> v.toString()
        else -> throw IllegalArgumentException(
            "Request parameter must be one of [Number, Boolean, String] but was ${v::class}"
        )
    }
}

class Request<out B, out R>(
    val method: Method,
    val path: String,
    val parameters: Parameters = emptyMap(),
    val body: B? = null,
    val processResult: (Deserializer.ObjectCtx) -> R,
) {
    companion object {
        operator fun <B> invoke(
            method: Method,
            path: String,
            parameters: Parameters = emptyMap(),
            body: B? = null,
        ): Request<B, Deserializer.ObjectCtx> {
            return Request(
                method = method,
                path = path,
                parameters = parameters,
                body = body
            ) { obj -> obj }
        }
    }
}

interface RequestEncoderFactory {
    val encoding: String?
    fun create(): RequestEncoder
}

interface RequestEncoder : Appendable {
    override fun append(value: Char): Appendable {
        return append(value.toString())
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
        return append(value?.subSequence(startIndex, endIndex))
    }

    fun toByteArray(): ByteArray
}

class StringEncoderFactory : RequestEncoderFactory {
    override val encoding: String? = null
    override fun create() = StringEncoder()
}

class StringEncoder : RequestEncoder {
    private val builder = StringBuilder()

    override fun append(value: CharSequence?): Appendable {
        return builder.append(value)
    }

    override fun toByteArray(): ByteArray {
        return builder.toString().encodeToByteArray(throwOnInvalidSequence = true)
    }
}

class GzipEncoderFactory : RequestEncoderFactory {
    override val encoding = "gzip"
    override fun create() = GzipEncoder()
}
internal expect val isGzipSupported: Boolean
expect class GzipEncoder() : RequestEncoder

typealias RequestBodyBuilder = RequestEncoder.() -> Unit

abstract class ElasticsearchTransport(
    val baseUrl: String,
    val serde: Serde,
    config: Config,
) {
    class Config {
        var gzipRequests: Boolean = false
    }

    protected val requestEncoderFactory: RequestEncoderFactory =
        if (config.gzipRequests && isGzipSupported) {
            GzipEncoderFactory()
        } else {
            StringEncoderFactory()
        }

    suspend fun <R> request(request: Request<Serializer.ObjectCtx, R>): R {
        val response = request(
            request.method,
            request.path,
            request.parameters,
            contentType = serde.contentType,
        ) {
            if (request.body != null) {
                append(request.body.serialize())
            }
        }
        // HEAD requests return empty response body
        val result = serde.deserializer.objFromString(
            response.ifBlank { "{}" }
        )
        return request.processResult(result)
    }

    suspend fun <R> bulkRequest(request: Request<List<Serializer.ObjectCtx>, R>): R {
        val response = request(
            request.method,
            request.path,
            request.parameters,
            contentType = "application/x-ndjson",
        ) {
            if (request.body != null) {
                for (row in request.body) {
                    append(row.serialize())
                    append("\n")
                }
            }
        }
        // HEAD requests return empty response body
        val result = serde.deserializer.objFromString(
            response.ifBlank { "{}" }
        )
        return request.processResult(result)
    }

    abstract suspend fun request(
        method: Method,
        path: String,
        parameters: Map<String, List<String>>? = null,
        contentType: String? = null,
        bodyBuilder: RequestBodyBuilder? = null
    ): String
}
