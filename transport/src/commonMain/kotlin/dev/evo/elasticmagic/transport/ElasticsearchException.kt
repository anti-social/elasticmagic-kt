package dev.evo.elasticmagic.transport

open class ElasticsearchException(msg: String) : Exception(msg) {
    open class Transport(
        val statusCode: Int,
        val error: TransportError,
    ) : ElasticsearchException("Elasticsearch responded with an error") {
        // TODO:
        open val isRetriable = false

        companion object {
            private const val MAX_TEXT_ERROR_LENGTH = 256

            private fun trimText(text: String) =
                text.slice(0 until text.length.coerceAtMost(MAX_TEXT_ERROR_LENGTH))

            fun fromStatusCode(statusCode: Int, error: TransportError): Transport {
                return when (statusCode) {
                    400 -> Request(error)
                    401 -> Authentication(error)
                    403 -> Authorization(error)
                    404 -> NotFound(error)
                    409 -> Conflict(error)
                    504 -> GatewayTimeout(error)
                    else -> Transport(statusCode, error)
                }
            }
        }

        override fun toString(): String {
            val reason = when (error) {
                is TransportError.Structured -> {
                    val rootCause = error.rootCauses.getOrNull(0)
                    buildString {
                        if (rootCause != null) {
                            append(rootCause.reason)
                        } else {
                            append(error.reason)
                        }
                    }
                }
                is TransportError.Simple -> trimText(error.error)
            }
            return "${this::class.simpleName}(statusCode=${statusCode}, ${reason})"
        }
    }
    class Request(error: TransportError) : Transport(400, error)
    class Authentication(error: TransportError) : Transport(401, error)
    class Authorization(error: TransportError) : Transport(403, error)
    class NotFound(error: TransportError) : Transport(404, error)
    class Conflict(error: TransportError) : Transport(409, error)
    class GatewayTimeout(error: TransportError) : Transport(504, error)
}
