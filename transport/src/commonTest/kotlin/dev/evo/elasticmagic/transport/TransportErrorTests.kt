package dev.evo.elasticmagic.transport

import dev.evo.elasticmagic.serde.serialization.JsonDeserializer

import io.kotest.matchers.shouldBe

import kotlin.test.Test

import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
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
            JsonDeserializer.wrapObj(rawError)
        )
        ex shouldBe TransportError.Structured("error", "error reason")
    }

    @Test
    fun parseInvalidJson() {
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
            JsonDeserializer.wrapObj(rawError)
        )
        ex shouldBe TransportError.Simple("{root_cause=[{type=error, reason=error reason}]}")
    }

    @Test
    fun parseJsonError() {
        val rawError = buildJsonObject {
            put("error", "something error message")
        }
        val ex = TransportError.parse(
            JsonDeserializer.wrapObj(rawError)
        )
        ex shouldBe TransportError.Simple("something error message")
    }
}
