package dev.evo.elasticmagic.transport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

enum class Method {
    GET, PUT, POST, DELETE
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
internal expect val isGzipEncoderImplemented: Boolean
expect class GzipEncoder() : RequestEncoder

typealias RequestBodyBuilder = RequestEncoder.() -> Unit


abstract class ElasticsearchTransport(
    val baseUrl: String,
    config: Config,
) {
    companion object {
        private val json = Json.Default
    }

    class Config {
        var gzipRequests: Boolean = false
    }

    protected val requestEncoderFactory: RequestEncoderFactory =
        if (config.gzipRequests && isGzipEncoderImplemented) {
            GzipEncoderFactory()
        } else {
            StringEncoderFactory()
        }

    suspend fun jsonRequest(
        method: Method,
        path: String,
        parameters: Map<String, List<String>>? = null,
        body: JsonElement? = null
    ): JsonElement {
        val response = if (body != null) {
            request(method, path, parameters) {
                append(json.encodeToString(JsonElement.serializer(), body))
            }
        } else {
            request(method, path, parameters, null)
        }
        return json.decodeFromString(JsonElement.serializer(), response)
    }

    abstract suspend fun request(
        method: Method,
        path: String,
        parameters: Map<String, List<String>>? = null,
        contentType: String? = null,
        bodyBuilder: RequestBodyBuilder? = null
    ): String
}
