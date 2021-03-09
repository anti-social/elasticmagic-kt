package dev.evo.elasticmagic.transport

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed class ElasticsearchException(msg: String) : Exception(msg) {
    open class TransportError(
        val statusCode: Int,
        val error: String
    ) : ElasticsearchException("Elasticsearch server respond with an error") {
        private val json = Json.Default
        open val isRetriable = false

        companion object {
            private const val MAX_TEXT_ERROR_LENGTH = 80

            private fun jsonElementToString(element: JsonElement) = when (element) {
                is JsonObject -> element.toString()
                is JsonArray -> element.toString()
                is JsonPrimitive -> element.content
            }

            private fun trimRawError(error: String) =
                error.slice(0 until error.length.coerceAtMost(MAX_TEXT_ERROR_LENGTH))
        }

        fun reason(): String? {
            return try {
                val info = json.decodeFromString(JsonElement.serializer(), error).jsonObject
                when (val error = info["error"]) {
                    null -> return null
                    is JsonObject -> {
                        val rootCause = error["root_cause"]?.jsonArray?.get(0)?.jsonObject
                            ?: return null
                        StringBuilder().apply {
                            append(
                                jsonElementToString(rootCause["reason"] ?: return null)
                            )
                            rootCause["resource.id"]?.let(::append)
                            rootCause["resource.type"]?.let(::append)
                        }.toString()
                    }
                    is JsonPrimitive -> error.content
                    is JsonArray -> error.toString()
                }

            } catch (ex: SerializationException) {
                return trimRawError(error)
            } catch (ex: IllegalStateException) {
                return trimRawError(error)
            }
        }

        override fun toString(): String {
            val reasonArg = when (val reason = reason()) {
                null -> ""
                else -> ", \"$reason\""
            }
            return "${this::class.simpleName}(${statusCode}${reasonArg})"
        }
    }
    class RequestError(error: String)
        : TransportError(400, error)
    class AuthenticationError(error: String)
        : TransportError(401, error)
    class AuthorizationError(error: String)
        : TransportError(403, error)
    class NotFoundError(error: String)
        : TransportError(404, error)
    class ConflictError(error: String)
        : TransportError(409, error)
    class GatewayTimeout(error: String)
        : TransportError(504, error)
}
