package dev.evo.elasticmagic.transport

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


abstract class ElasticsearchTransport<OBJ>(
    val baseUrl: String,
    config: Config,
) {
    class Config {
        var gzipRequests: Boolean = false
    }

    protected val requestEncoderFactory: RequestEncoderFactory =
        if (config.gzipRequests && isGzipEncoderImplemented) {
            GzipEncoderFactory()
        } else {
            StringEncoderFactory()
        }

    abstract suspend fun objRequest(
        method: Method,
        path: String,
        parameters: Map<String, List<String>>? = null,
        body: OBJ? = null
    ): OBJ

    abstract suspend fun request(
        method: Method,
        path: String,
        parameters: Map<String, List<String>>? = null,
        contentType: String? = null,
        bodyBuilder: RequestBodyBuilder? = null
    ): String
}
