package dev.evo.elasticmagic.json.compile

import dev.evo.elasticmagic.compile.Serializer

import kotlinx.serialization.json.*

class JsonSerializer : Serializer<JsonObject, JsonArray> {
    private class ObjectCtx(private val objBuilder: JsonObjectBuilder) : Serializer.ObjectCtx {
        override fun field(name: String, value: Any?) {
            objBuilder.put(name, toJsonPrimitive(value))
        }

        override fun obj(name: String, block: Serializer.ObjectCtx.() -> Unit) {
            objBuilder.putJsonObject(name) {
                ObjectCtx(this).block()
            }
        }

        override fun array(name: String, block: Serializer.ArrayCtx.() -> Unit) {
            objBuilder.putJsonArray(name) {
                ArrayCtx(this).block()
            }
        }
    }

    class ArrayCtx(private val arrayBuilder: JsonArrayBuilder) : Serializer.ArrayCtx {
        override fun value(value: Any?) {
            arrayBuilder.add(toJsonPrimitive(value))
        }

        override fun obj(block: Serializer.ObjectCtx.() -> Unit) {
            arrayBuilder.addJsonObject {
                ObjectCtx(this).block()
            }
        }

        override fun array(block: Serializer.ArrayCtx.() -> Unit) {
            arrayBuilder.addJsonArray {
                ArrayCtx(this).block()
            }
        }
    }

    companion object {
        private fun toJsonPrimitive(value: Any?): JsonPrimitive {
            return when (value) {
                null -> JsonNull
                is Number? -> JsonPrimitive(value)
                is Boolean? -> JsonPrimitive(value)
                is String? -> JsonPrimitive(value)
                else ->  throw IllegalArgumentException(
                    "Expected number, boolean or string value but was: ${value::class}"
                )
            }
        }
    }

    override fun obj(block: Serializer.ObjectCtx.() -> Unit): JsonObject {
        return buildJsonObject {
            ObjectCtx(this).block()
        }
    }

    override fun array(block: Serializer.ArrayCtx.() -> Unit): JsonArray {
        return buildJsonArray {
            ArrayCtx(this).block()
        }
    }
}
