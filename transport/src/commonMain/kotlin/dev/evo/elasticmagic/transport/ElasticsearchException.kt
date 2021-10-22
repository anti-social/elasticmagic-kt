package dev.evo.elasticmagic.transport

open class ElasticsearchException(msg: String) : Exception(msg) {
    companion object {
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_CONFLICT = 409
        private const val HTTP_GATEWAY_TIMEOUT = 504
    }

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
                    HTTP_BAD_REQUEST -> Request(error)
                    HTTP_UNAUTHORIZED -> Authentication(error)
                    HTTP_FORBIDDEN -> Authorization(error)
                    HTTP_NOT_FOUND -> NotFound(error)
                    HTTP_CONFLICT -> Conflict(error)
                    HTTP_GATEWAY_TIMEOUT -> GatewayTimeout(error)
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
            return "${this::class.simpleName}(${statusCode}, \"${reason}\")"
        }
    }
    class Request(error: TransportError) : Transport(HTTP_BAD_REQUEST, error)
    class Authentication(error: TransportError) : Transport(HTTP_UNAUTHORIZED, error)
    class Authorization(error: TransportError) : Transport(HTTP_FORBIDDEN, error)
    class NotFound(error: TransportError) : Transport(HTTP_NOT_FOUND, error)
    class Conflict(error: TransportError) : Transport(HTTP_CONFLICT, error)
    class GatewayTimeout(error: TransportError) : Transport(HTTP_GATEWAY_TIMEOUT, error)
}
