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
    abstract val contentType: String
    open val acceptContentType: String? = null
    abstract val errorSerde: Serde

    abstract fun serializeRequest(encoder: RequestEncoder)
    abstract fun deserializeResponse(response: String): ResponseT
}

class ApiRequest<ResultT>(
    method: Method,
    path: String,
    parameters: Parameters = emptyMap(),
    body: Serializer.ObjectCtx? = null,
    private val serde: Serde,
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
            serde: Serde,
        ): ApiRequest<Deserializer.ObjectCtx> {
            return ApiRequest(method, path, parameters = parameters, body = body, serde = serde) { res -> res}
        }
    }

    override val contentType: String = serde.contentType
    override val errorSerde: Serde = serde

    override fun serializeRequest(encoder: RequestEncoder) {
        if (body != null) {
            encoder.append(body.serialize())
        }
    }

    override fun deserializeResponse(response: String): Deserializer.ObjectCtx {
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
    private val serde: Serde.OneLineJson,
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
            serde: Serde.OneLineJson,
        ): BulkRequest<Deserializer.ObjectCtx> {
            return BulkRequest(method, path, parameters = parameters, body = body, serde = serde) { res -> res}
        }
    }

    override val contentType = "application/x-ndjson"
    override val acceptContentType: String = serde.contentType
    override val errorSerde = serde

    override fun serializeRequest(encoder: RequestEncoder) {
        if (body != null) {
            for (obj in body) {
                encoder.append(obj.serialize())
                encoder.append("\n")
            }
        }
    }

    override fun deserializeResponse(response: String): Deserializer.ObjectCtx {
        // HEAD requests return empty response body
        return serde.deserializer.objFromString(
            response.ifBlank { "{}" }
        )
    }
}

class CatRequest<ResultT>(
    path: String,
    parameters: Parameters = emptyMap(),
    override val errorSerde: Serde,
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
            errorSerde: Serde,
        ): CatRequest<List<List<String>>> {
            return CatRequest(path, parameters = parameters, errorSerde = errorSerde) { res -> res}
        }
    }

    override val contentType = "text/plain"

    override fun serializeRequest(encoder: RequestEncoder) {}

    override fun deserializeResponse(response: String): List<List<String>> {
        return response.split("\n").mapNotNull { row ->
            if (row.isBlank()) null else row.split("\\s+".toRegex())
        }
    }
}


abstract class ElasticsearchTransport(
    val baseUrl: String,
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
        val response = doRequest(request)
        return request.processResponse(request.deserializeResponse(response))
    }

    protected abstract suspend fun doRequest(request: Request<*, *, *>): String
}
