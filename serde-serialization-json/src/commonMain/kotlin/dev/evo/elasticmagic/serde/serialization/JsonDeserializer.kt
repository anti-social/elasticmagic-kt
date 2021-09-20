package dev.evo.elasticmagic.serde.serialization

import dev.evo.elasticmagic.serde.DeserializationException
import dev.evo.elasticmagic.serde.Deserializer

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed class JsonDeserializer : Deserializer<JsonObject> {
    companion object : JsonDeserializer() {
        private fun coerceToAny(v: JsonElement): Any? {
            return when (v) {
                is JsonNull -> null
                is JsonPrimitive -> {
                    if (v.isString) {
                        v.content
                    } else {
                        v.longOrNull
                            ?: v.doubleOrNull
                            ?: v.booleanOrNull
                    }
                }
                is JsonObject -> ObjectCtx(v)
                is JsonArray -> ArrayCtx(v)
            }
        }
    }

    private class ObjectCtx(private val obj: JsonObject) : Deserializer.ObjectCtx {
        override fun anyOrNull(name: String): Any? {
            return obj[name]?.let(::coerceToAny)
        }

        override fun intOrNull(name: String): Int? {
            return obj[name]?.jsonPrimitive?.intOrNull
        }

        override fun longOrNull(name: String): Long? {
            return obj[name]?.jsonPrimitive?.longOrNull
        }

        override fun floatOrNull(name: String): Float? {
            return obj[name]?.jsonPrimitive?.floatOrNull
        }

        override fun doubleOrNull(name: String): Double? {
            return obj[name]?.jsonPrimitive?.doubleOrNull
        }

        override fun booleanOrNull(name: String): Boolean? {
            return obj[name]?.jsonPrimitive?.booleanOrNull
        }

        override fun stringOrNull(name: String): String? {
            return obj[name]?.jsonPrimitive?.contentOrNull
        }

        override fun objOrNull(name: String): Deserializer.ObjectCtx? {
            return (obj[name] as? JsonObject)?.let(JsonDeserializer::ObjectCtx)
        }

        override fun arrayOrNull(name: String): Deserializer.ArrayCtx? {
            return (obj[name] as? JsonArray)?.let(JsonDeserializer::ArrayCtx)
        }

        override fun iterator(): ObjectIterator {
            return ObjectIterator(obj.iterator())
        }
    }

    private class ObjectIterator(
        private val iter: Iterator<Map.Entry<String, JsonElement>>
    ) : Deserializer.ObjectIterator {
        private var currentEntry: Map.Entry<String, JsonElement>? = null

        override fun hasNext(): Boolean {
            return iter.hasNext().also {
                currentEntry = if (it) iter.next() else null
            }
        }

        override fun anyOrNull(): Pair<String, Any?> {
            val (key, value) = currentEntry!!
            return key to coerceToAny(value)
        }

        override fun intOrNull(): Pair<String, Int?> {
            val (key, jsonValue) = currentEntry!!
            return jsonValue.jsonPrimitiveOrNull.let { value ->
                key to value?.intOrNull
            }
        }

        override fun longOrNull(): Pair<String, Long?> {
            val (key, jsonValue) = currentEntry!!
            return jsonValue.jsonPrimitiveOrNull.let { value ->
                key to value?.longOrNull
            }
        }

        override fun floatOrNull(): Pair<String, Float?> {
            val (key, jsonValue) = currentEntry!!
            return jsonValue.jsonPrimitiveOrNull.let { value ->
                key to value?.floatOrNull
            }
        }

        override fun doubleOrNull(): Pair<String, Double?> {
            val (key, jsonValue) = currentEntry!!
            return jsonValue.jsonPrimitiveOrNull.let { value ->
                key to value?.doubleOrNull
            }
        }

        override fun booleanOrNull(): Pair<String, Boolean?> {
            val (key, jsonValue) = currentEntry!!
            return jsonValue.jsonPrimitiveOrNull.let { value ->
                key to value?.booleanOrNull
            }
        }

        override fun stringOrNull(): Pair<String, String?> {
            val (key, jsonValue) = currentEntry!!
            return jsonValue.jsonPrimitiveOrNull.let { value ->
                key to value?.contentOrNull
            }
        }

        override fun objOrNull(): Pair<String, Deserializer.ObjectCtx?> {
            val (key, jsonValue) = currentEntry!!
            return key to jsonValue.jsonObjectOrNull?.let(JsonDeserializer::ObjectCtx)
        }

        override fun arrayOrNull(): Pair<String, Deserializer.ArrayCtx?> {
            val (key, jsonValue) = currentEntry!!
            return key to jsonValue.jsonArrayOrNull?.let(JsonDeserializer::ArrayCtx)
        }
    }

    private class ArrayCtx(arr: JsonArray) : Deserializer.ArrayCtx {
        private val iter = arr.iterator()
        private var currentValue: JsonElement? = null

        private fun nextPrimitive(): JsonPrimitive? {
            return when(val v = currentValue) {
                is JsonPrimitive -> v
                else -> null
            }
        }

        override fun hasNext(): Boolean {
            return iter.hasNext().also {
                currentValue = if (it) iter.next() else null
            }
        }

        override fun anyOrNull(): Any? {
            return coerceToAny(currentValue!!)
        }

        override fun intOrNull(): Int? {
            return nextPrimitive()?.intOrNull
        }

        override fun longOrNull(): Long? {
            return nextPrimitive()?.longOrNull
        }

        override fun floatOrNull(): Float? {
            return nextPrimitive()?.floatOrNull
        }

        override fun doubleOrNull(): Double? {
            return nextPrimitive()?.doubleOrNull
        }

        override fun booleanOrNull(): Boolean? {
            return nextPrimitive()?.booleanOrNull
        }

        override fun stringOrNull(): String? {
            return nextPrimitive()?.contentOrNull
        }

        override fun objOrNull(): Deserializer.ObjectCtx? {
            return (currentValue!! as? JsonObject)?.let(JsonDeserializer::ObjectCtx)
        }

        override fun arrayOrNull(): Deserializer.ArrayCtx? {
            return (currentValue!! as? JsonArray)?.let(JsonDeserializer::ArrayCtx)
        }
    }

    override fun wrapObj(obj: JsonObject): Deserializer.ObjectCtx {
        return ObjectCtx(obj)
    }

    override fun objFromStringOrNull(data: String): Deserializer.ObjectCtx? {
        val jsonObj =  try {
            Json.decodeFromString(JsonElement.serializer(), data) as? JsonObject
        } catch (e: SerializationException) {
            throw DeserializationException("Cannot deserialize data", e)
        }
        return jsonObj?.let(::wrapObj)
    }
}

private val JsonElement.jsonPrimitiveOrNull: JsonPrimitive?
    get() = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> this
        else -> null
    }

private val JsonElement.jsonObjectOrNull: JsonObject?
    get() = this as? JsonObject

private val JsonElement.jsonArrayOrNull: JsonArray?
    get() = this as? JsonArray
