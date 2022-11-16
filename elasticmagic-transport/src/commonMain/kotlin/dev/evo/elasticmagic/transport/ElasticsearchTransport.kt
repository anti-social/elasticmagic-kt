package dev.evo.elasticmagic.transport

import dev.evo.elasticmagic.serde.DeserializationException
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.serde.Serializer

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

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

object StringEncoderFactory : RequestEncoderFactory {
    override val encoding: String? = null
    override fun create() = StringEncoder()
}

class StringEncoder : RequestEncoder {
    private val builder = StringBuilder()

    override fun append(value: CharSequence?): Appendable {
        return builder.append(value)
    }

    override fun toByteArray(): ByteArray {
        return toString().encodeToByteArray(throwOnInvalidSequence = true)
    }

    override fun toString(): String {
        return builder.toString()
    }
}

object GzipEncoderFactory : RequestEncoderFactory {
    override val encoding = "gzip"
    override fun create() = GzipEncoder()
}

internal expect val isGzipSupported: Boolean
expect class GzipEncoder() : RequestEncoder

sealed class Auth {
    class Basic(val username: String, val password: String) : Auth()
}

abstract class Request<out BodyT, ResponseT, out ResultT> {
    abstract val method: Method
    abstract val path: String
    abstract val parameters: Parameters
    abstract val body: BodyT?
    abstract val contentType: String
    abstract val errorSerde: Serde
    abstract val processResponse: (ResponseT) -> ResultT

    open val acceptContentType: String? = null

    abstract fun serializeRequest(encoder: RequestEncoder)
    abstract fun deserializeResponse(response: String): ResponseT

    /**
     * Encodes this request body to string. Useful with transport hooks
     */
    fun encodeToString(): String {
        return StringEncoderFactory.create()
            .apply(::serializeRequest)
            .toString()
    }
}

class ApiRequest<ResultT>(
    override val method: Method,
    override val path: String,
    override val parameters: Parameters = emptyMap(),
    override val body: Serializer.ObjectCtx? = null,
    val serde: Serde,
    override val processResponse: (Deserializer.ObjectCtx) -> ResultT
) : Request<Serializer.ObjectCtx, Deserializer.ObjectCtx, ResultT>() {
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
    override val method: Method,
    override val path: String,
    override val parameters: Parameters = emptyMap(),
    override val body: List<Serializer.ObjectCtx>,
    val serde: Serde.OneLineJson,
    override val processResponse: (Deserializer.ObjectCtx) -> ResultT
) : Request<List<Serializer.ObjectCtx>, Deserializer.ObjectCtx, ResultT>() {
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
        for (obj in body) {
            encoder.append(obj.serialize())
            encoder.append("\n")
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
    catPath: String,
    override val parameters: Parameters = emptyMap(),
    override val errorSerde: Serde,
    override val processResponse: (List<List<String>>) -> ResultT
) : Request<Nothing, List<List<String>>, ResultT>() {
    companion object {
        operator fun invoke(
            path: String,
            parameters: Parameters = emptyMap(),
            errorSerde: Serde,
        ): CatRequest<List<List<String>>> {
            return CatRequest(path, parameters = parameters, errorSerde = errorSerde) { res -> res}
        }
    }

    override val method = Method.GET
    override val path = "_cat/$catPath"
    override val body = null
    override val contentType = "text/plain"

    override fun serializeRequest(encoder: RequestEncoder) {}

    override fun deserializeResponse(response: String): List<List<String>> {
        return response.split("\n").mapNotNull { row ->
            if (row.isBlank()) null else row.split("\\s+".toRegex())
        }
    }
}

class PlainResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val contentType: String?,
    val content: String,
)

sealed class Response<T> {
    data class Ok<T>(
        val statusCode: Int,
        val headers: Map<String, List<String>>,
        val contentType: String?,
        val content: String,
        val result: T,
    ) : Response<T>()
    data class Error(
        val statusCode: Int,
        val headers: Map<String, List<String>>,
        val contentType: String?,
        val error: TransportError,
    ) : Response<Nothing>()
    data class Exception(val cause: Throwable) : Response<Nothing>()
}

abstract class ElasticsearchTransport(
    val baseUrl: String,
    protected val config: Config,
) {
    /**
     * Configuration of transport
     */
    class Config {
        /**
         * Whether to compress requests or not
         */
        var gzipRequests: Boolean = false

        /**
         * Authentication data
         */
        var auth: Auth? = null

        /**
         * Using hooks it is possible to log requests
         */
        var hooks: List<(Request<*, *, *>, Response<*>, Duration) -> Unit> = emptyList()
    }

    protected val requestEncoderFactory: RequestEncoderFactory =
        if (config.gzipRequests && isGzipSupported) {
            GzipEncoderFactory
        } else {
            StringEncoderFactory
        }

    companion object {
        private val HTTP_OK_CODES = 200..299
    }

    @OptIn(ExperimentalTime::class)
    suspend fun <BodyT, ResponseT, ResultT> request(
        request: Request<BodyT, ResponseT, ResultT>
    ): ResultT {
        val (requestResult, duration) = measureTimedValue {
            runCatching {
                doRequest(request)
            }
        }

        val response = requestResult.fold(
            { response ->
                processResponse(request, response)
            },
            { exception ->
                Response.Exception(exception)
            }
        )

        config.hooks.forEach { hook ->
            hook(request, response, duration)
        }

        return when (response) {
            is Response.Ok<out ResultT> -> response.result
            is Response.Error -> {
                throw ElasticsearchException.Transport.fromStatusCode(
                    response.statusCode, response.error
                )
            }
            is Response.Exception -> {
                throw response.cause
            }
        }
    }

    private fun <BodyT, ResponseT, ResultT> processResponse(
        request: Request<BodyT, ResponseT, ResultT>, response: PlainResponse
    ): Response<out ResultT> {
        val content = response.content
        return when (val statusCode = response.statusCode) {
            in HTTP_OK_CODES -> {
                val result = request.processResponse(request.deserializeResponse(content))
                Response.Ok(
                    statusCode,
                    response.headers,
                    response.contentType,
                    content,
                    result,
                )
            }
            else -> {
                val jsonError = try {
                    request.errorSerde.deserializer.objFromStringOrNull(content)
                } catch (e: DeserializationException) {
                    null
                }
                val transportError = if (jsonError != null) {
                    TransportError.parse(jsonError)
                } else {
                    TransportError.Simple(content)
                }
                Response.Error(
                    statusCode, response.headers, response.contentType, transportError
                )
            }
        }
    }

    protected abstract suspend fun doRequest(request: Request<*, *, *>): PlainResponse
}
