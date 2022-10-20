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

sealed class Auth {
    class Basic(val username: String, val password: String) : Auth()
}

abstract class Request<out BodyT, ResponseT, out ResultT>(
    val method: Method,
    val path: String,
    val parameters: Parameters = emptyMap(),
    val body: BodyT? = null,
    val processResponse: (ResponseT) -> ResultT,
) {
    open val contentType: String? = null

    abstract fun serializeRequest(encoder: RequestEncoder)
    abstract fun deserializeResponse(response: String, serde: Serde): ResponseT
}

class JsonRequest<ResultT>(
    method: Method,
    path: String,
    parameters: Parameters = emptyMap(),
    body: Serializer.ObjectCtx? = null,
    processResponse: (Deserializer.ObjectCtx) -> ResultT
) : Request<Serializer.ObjectCtx, Deserializer.ObjectCtx, ResultT>(
    method,
    path,
    parameters = parameters,
    body = body,
    processResponse = processResponse
) {
    companion object {
        operator fun invoke(
            method: Method,
            path: String,
            parameters: Parameters = emptyMap(),
            body: Serializer.ObjectCtx? = null,
        ): JsonRequest<Deserializer.ObjectCtx> {
            return JsonRequest(method, path, parameters = parameters, body = body) { res -> res}
        }
    }
    override val contentType = "application/json"

    override fun serializeRequest(encoder: RequestEncoder) {
        if (body != null) {
            encoder.append(body.serialize())
        }
    }

    override fun deserializeResponse(response: String, serde: Serde): Deserializer.ObjectCtx {
        // HEAD requests return empty response body
        return serde.deserializer.objFromString(
            response.ifBlank { "{}" }
        )
    }
}

class BulkRequest<ResultT>(
    method: Method,
    path: String,
    parameters: Parameters = emptyMap(),
    body: List<Serializer.ObjectCtx>,
    processResponse: (Deserializer.ObjectCtx) -> ResultT
) : Request<List<Serializer.ObjectCtx>, Deserializer.ObjectCtx, ResultT>(
    method,
    path,
    parameters = parameters,
    body = body,
    processResponse = processResponse
) {
    companion object {
        operator fun invoke(
            method: Method,
            path: String,
            parameters: Parameters = emptyMap(),
            body: List<Serializer.ObjectCtx>,
        ): BulkRequest<Deserializer.ObjectCtx> {
            return BulkRequest(method, path, parameters = parameters, body = body) { res -> res}
        }
    }

    override val contentType = "application/x-ndjson"

    override fun serializeRequest(encoder: RequestEncoder) {
        if (body != null) {
            for (row in body) {
                encoder.append(row.serialize())
                encoder.append("\n")
            }
        }
    }

    override fun deserializeResponse(response: String, serde: Serde): Deserializer.ObjectCtx {
        // HEAD requests return empty response body
        return serde.deserializer.objFromString(
            response.ifBlank { "{}" }
        )
    }
}

class CatRequest<ResultT>(
    path: String,
    parameters: Parameters = emptyMap(),
    processResponse: (List<List<String>>) -> ResultT
) : Request<Nothing, List<List<String>>, ResultT>(
    Method.GET,
    "_cat/$path",
    parameters = parameters,
    body = null,
    processResponse = processResponse
) {
    companion object {
        operator fun invoke(
            path: String,
            parameters: Parameters = emptyMap(),
        ): CatRequest<List<List<String>>> {
            return CatRequest(path, parameters = parameters) { res -> res}
        }
    }

    override fun serializeRequest(encoder: RequestEncoder) {}

    override fun deserializeResponse(response: String, serde: Serde): List<List<String>> {
        return response.split("\n").mapNotNull { row ->
            if (row.isBlank()) null else row.split("\\s+".toRegex())
        }
    }
}


abstract class ElasticsearchTransport(
    val baseUrl: String,
    val serde: Serde,
    protected val config: Config,
) {
    class Config {
        var gzipRequests: Boolean = false
        var auth: Auth? = null
    }

    protected val requestEncoderFactory: RequestEncoderFactory =
        if (config.gzipRequests && isGzipSupported) {
            GzipEncoderFactory()
        } else {
            StringEncoderFactory()
        }

    suspend fun <B, T, R> request(
        request: Request<B, T, R>
    ): R {
        val response = doRequest(
            request.method,
            request.path,
            request.parameters,
            contentType = request.contentType,
            bodyBuilder = request::serializeRequest
        )
        return request.processResponse(request.deserializeResponse(response, serde))
    }

    protected abstract suspend fun doRequest(
        method: Method,
        path: String,
        parameters: Map<String, List<String>>? = null,
        contentType: String? = null,
        bodyBuilder: RequestBodyBuilder? = null
    ): String
}
