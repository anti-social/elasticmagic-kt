package dev.evo.elasticmagic.transport

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

interface ContentEncoder : Appendable {
    val encoding: String?

    override fun append(value: Char): Appendable {
        return append(value.toString())
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
        return append(value?.subSequence(startIndex, endIndex))
    }

    fun toByteArray(): ByteArray
}

class IdentityEncoder : ContentEncoder {
    override val encoding: String? = null

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

abstract class BaseGzipEncoder : ContentEncoder {
    override val encoding: String = "gzip"
}

internal expect val isGzipSupported: Boolean

class PreservingOriginGzipEncoder : BaseGzipEncoder() {
    private val gzipEncoder = GzipEncoder()
    private val identEncoder = IdentityEncoder()

    override fun append(value: CharSequence?): Appendable {
        gzipEncoder.append(value)
        identEncoder.append(value)
        return this
    }

    override fun toByteArray(): ByteArray {
        return gzipEncoder.toByteArray()
    }

    override fun toString(): String {
        return identEncoder.toString()
    }
}

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
    // TODO: try to rid of from processResponse here
    abstract val processResponse: (ResponseT) -> ResultT

    open val acceptContentType: String? = null

    abstract fun serializeRequest(encoder: ContentEncoder)
    abstract fun deserializeResponse(response: PlainResponse): ResponseT
}

interface Response<T> {
    val statusCode: Int
    val headers: Map<String, List<String>>
    val content: T
}

class ApiRequest<ResultT>(
    override val method: Method,
    override val path: String,
    override val parameters: Parameters = emptyMap(),
    override val body: Serializer.ObjectCtx? = null,
    val serde: Serde,
    override val processResponse: (ApiResponse) -> ResultT
) : Request<Serializer.ObjectCtx, ApiResponse, ResultT>() {
    companion object {
        operator fun invoke(
            method: Method,
            path: String,
            parameters: Parameters = emptyMap(),
            body: Serializer.ObjectCtx? = null,
            serde: Serde,
        ): ApiRequest<Deserializer.ObjectCtx> {
            return ApiRequest(
                method,
                path,
                parameters = parameters,
                body = body,
                serde = serde,
                processResponse = { resp -> resp.content },
            )
        }
    }

    override val contentType: String = serde.contentType
    override val errorSerde: Serde = serde

    override fun serializeRequest(encoder: ContentEncoder) {
        if (body != null) {
            encoder.append(body.serialize())
        }
    }

    override fun deserializeResponse(response: PlainResponse): ApiResponse {
        return ApiResponse.fromPlainResponse(response, serde.deserializer)
    }
}

class ApiResponse(
    override val statusCode: Int,
    override val headers: Map<String, List<String>>,
    override val content: Deserializer.ObjectCtx,
) : Response<Deserializer.ObjectCtx> {
    companion object {
        internal fun fromPlainResponse(
            response: PlainResponse,
            deserializer: Deserializer
        ): ApiResponse {
            // HEAD requests return empty response body
            val content = deserializer.objFromString(
                response.content.ifBlank { "{}" }
            )
            return ApiResponse(
                response.statusCode,
                response.headers,
                content
            )
        }
    }
}

class BulkRequest<ResultT>(
    override val method: Method,
    override val path: String,
    override val parameters: Parameters = emptyMap(),
    override val body: List<Serializer.ObjectCtx>,
    val serde: Serde.OneLineJson,
    override val processResponse: (ApiResponse) -> ResultT
) : Request<List<Serializer.ObjectCtx>, ApiResponse, ResultT>() {
    companion object {
        operator fun invoke(
            method: Method,
            path: String,
            parameters: Parameters = emptyMap(),
            body: List<Serializer.ObjectCtx>,
            serde: Serde.OneLineJson,
        ): BulkRequest<Deserializer.ObjectCtx> {
            return BulkRequest(
                method,
                path,
                parameters = parameters,
                body = body,
                serde = serde,
                processResponse = { resp -> resp.content },
            )
        }
    }

    override val contentType = "application/x-ndjson"
    override val acceptContentType: String = serde.contentType
    override val errorSerde = serde

    override fun serializeRequest(encoder: ContentEncoder) {
        for (obj in body) {
            encoder.append(obj.serialize())
            encoder.append("\n")
        }
    }

    override fun deserializeResponse(response: PlainResponse): ApiResponse {
        return ApiResponse.fromPlainResponse(response, serde.deserializer)
    }
}

class CatRequest<ResultT>(
    catPath: String,
    override val parameters: Parameters = emptyMap(),
    override val errorSerde: Serde,
    override val processResponse: (CatResponse) -> ResultT
) : Request<Nothing, CatResponse, ResultT>() {
    companion object {
        operator fun invoke(
            path: String,
            parameters: Parameters = emptyMap(),
            errorSerde: Serde,
        ): CatRequest<List<List<String>>> {
            return CatRequest(
                path,
                parameters = parameters,
                errorSerde = errorSerde,
                processResponse = { resp -> resp.content },
            )
        }
    }

    override val method = Method.GET
    override val path = "_cat/$catPath"
    override val body = null
    override val contentType = "text/plain"

    override fun serializeRequest(encoder: ContentEncoder) {}

    override fun deserializeResponse(response: PlainResponse): CatResponse {
        val content = response.content.split("\n").mapNotNull { row ->
            if (row.isBlank()) null else row.split("\\s+".toRegex())
        }
        return CatResponse(
            response.statusCode,
            response.headers,
            content,
        )
    }
}

class CatResponse(
    override val statusCode: Int,
    override val headers: Map<String, List<String>>,
    override val content: List<List<String>>,
) : Response<List<List<String>>>


class PlainRequest(
    val method: Method,
    val path: String,
    val parameters: Parameters,
    val content: ByteArray,
    val textContent: String?,
    val contentType: String,
    val contentEncoding: String?,
    val acceptContentType: String?,
)

class PlainResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val contentType: String?,
    val content: String,
)

sealed class ResponseResult<T> {
    data class Ok<T>(
        val statusCode: Int,
        val headers: Map<String, List<String>>,
        val contentType: String?,
        val result: T,
    ) : ResponseResult<T>()
    data class Error(
        val statusCode: Int,
        val headers: Map<String, List<String>>,
        val contentType: String?,
        val error: TransportError,
    ) : ResponseResult<Nothing>()
    data class Exception(val cause: Throwable) : ResponseResult<Nothing>()
}

interface Tracker {
    fun requiresTextContent(request: Request<*, *, *>): Boolean = false

    suspend fun onRequest(request: PlainRequest)

    suspend fun onResponse(responseResult: Result<PlainResponse>, duration: Duration)
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
         * Allow to track all requests
         */
        var trackers: List<() -> Tracker> = emptyList()
    }

    companion object {
        private val HTTP_OK_CODES = 200..299
    }

    private fun createContentEncoder(preserveOrigin: Boolean): ContentEncoder {
        if (config.gzipRequests && isGzipSupported) {
            if (!preserveOrigin) {
                return GzipEncoder()
            }
            return PreservingOriginGzipEncoder()
        }
        return IdentityEncoder()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun <BodyT, ResponseT, ResultT> request(
        request: Request<BodyT, ResponseT, ResultT>
    ): ResultT {
        val trackers = config.trackers.map { it() }
        val isTextContentRequired = trackers.any { it.requiresTextContent(request) }

        val contentEncoder = createContentEncoder(isTextContentRequired)
        request.serializeRequest(contentEncoder)

        val plainRequest = PlainRequest(
            request.method,
            request.path,
            parameters = request.parameters,
            content = contentEncoder.toByteArray(),
            textContent = if (isTextContentRequired) contentEncoder.toString() else null,
            contentType = request.contentType,
            contentEncoding = contentEncoder.encoding,
            acceptContentType = request.acceptContentType,
        )

        trackers.forEach { tracker ->
            tracker.onRequest(plainRequest)
        }
        val (responseResult, duration) = measureTimedValue {
            runCatching {
                doRequest(plainRequest)
            }
        }
        trackers.forEach { tracker ->
            tracker.onResponse(responseResult, duration)
        }

        val response = responseResult.fold(
            { response ->
                processResponse(request, response)
            },
            { exception ->
                ResponseResult.Exception(exception)
            }
        )

        return when (response) {
            is ResponseResult.Ok<out ResultT> -> response.result
            is ResponseResult.Error -> {
                throw ElasticsearchException.Transport.fromStatusCode(
                    response.statusCode, response.error
                )
            }
            is ResponseResult.Exception -> {
                throw response.cause
            }
        }
    }

    private fun <BodyT, ResponseT, ResultT> processResponse(
        request: Request<BodyT, ResponseT, ResultT>, response: PlainResponse
    ): ResponseResult<out ResultT> {
        val content = response.content
        return when (val statusCode = response.statusCode) {
            in HTTP_OK_CODES -> {
                val result = request.processResponse(request.deserializeResponse(response))
                ResponseResult.Ok(
                    statusCode,
                    response.headers,
                    response.contentType,
                    result,
                )
            }
            else -> {
                val transportError = TransportError.parse(
                    content, request.errorSerde.deserializer
                )
                ResponseResult.Error(
                    statusCode, response.headers, response.contentType, transportError
                )
            }
        }
    }

    protected abstract suspend fun doRequest(
        request: PlainRequest
    ): PlainResponse
}
