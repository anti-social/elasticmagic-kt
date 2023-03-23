package dev.evo.elasticmagic.transport

import dev.evo.elasticmagic.serde.serialization.JsonDeserializer

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

import kotlin.test.Test

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class TransportErrorTests {
    @Test
    fun parseBasicJsonErrorWithReason() {
        val rawError = buildJsonObject {
            putJsonObject("error") {
                put("type", "error")
                put("reason", "error reason")
            }
        }

        val ex = TransportError.parse(
            Json.Default.encodeToString(rawError), JsonDeserializer
        )
        ex shouldBe TransportError.Structured("error", "error reason")
    }

    @Test
    fun parseNonStructuredJson() {
        val rawError = buildJsonObject {
            putJsonObject("error") {
                putJsonArray("root_cause") {
                    addJsonObject {
                        put("type", "error")
                        put("reason", "error reason")
                    }
                }
            }
        }

        val ex = TransportError.parse(
            Json.Default.encodeToString(rawError), JsonDeserializer
        )
        ex.shouldBeInstanceOf<TransportError.Simple>()
        ex.error shouldBe "{root_cause=[{type=error, reason=error reason}]}"
    }

    @Test
    fun parseJsonError() {
        val rawError = buildJsonObject {
            put("error", "Just error message")
        }
        val ex = TransportError.parse(
            Json.Default.encodeToString(rawError), JsonDeserializer
        )
        ex.shouldBeInstanceOf<TransportError.Simple>()
        ex.error shouldBe "Just error message"
    }
}
