package dev.evo.elasticmagic.json

import dev.evo.elasticmagic.serde.Deserializer

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class JsonDeserializer : Deserializer<JsonObject, JsonArray> {
    private class ObjectCtx(private val obj: JsonObject) : Deserializer.ObjectCtx {
        override fun intOrNull(name: String): Int? {
            return obj[name]?.jsonPrimitive?.int
        }

        override fun longOrNull(name: String): Long? {
            return obj[name]?.jsonPrimitive?.long
        }

        override fun floatOrNull(name: String): Float? {
            return obj[name]?.jsonPrimitive?.float
        }

        override fun doubleOrNull(name: String): Double? {
            return obj[name]?.jsonPrimitive?.double
        }

        override fun booleanOrNull(name: String): Boolean? {
            return obj[name]?.jsonPrimitive?.boolean
        }

        override fun stringOrNull(name: String): String? {
            return obj[name]?.jsonPrimitive?.content
        }

        override fun objOrNull(name: String): Deserializer.ObjectCtx? {
            return (obj[name] as? JsonObject)?.let(::ObjectCtx)
        }

        override fun arrayOrNull(name: String): Deserializer.ArrayCtx? {
            return (obj[name] as? JsonArray)?.let(::ArrayCtx)
        }
    }

    private class ArrayCtx(arr: JsonArray) : Deserializer.ArrayCtx {
        private val iter = arr.iterator()

        private fun nextPrimitive(): JsonPrimitive? {
            return when(val v = iter.next()) {
                is JsonPrimitive -> v
                is JsonNull -> null
                else -> error("not a primitive")
            }
        }

        override fun hasNext(): Boolean = iter.hasNext()

        override fun intOrNull(): Int? {
            return nextPrimitive()?.int
        }

        override fun longOrNull(): Long? {
            return nextPrimitive()?.long
        }

        override fun floatOrNull(): Float? {
            return nextPrimitive()?.float
        }

        override fun doubleOrNull(): Double? {
            return nextPrimitive()?.double
        }

        override fun booleanOrNull(): Boolean? {
            return nextPrimitive()?.boolean
        }

        override fun stringOrNull(): String? {
            return nextPrimitive()?.content
        }

        override fun objOrNull(): Deserializer.ObjectCtx? {
            return (iter.next() as? JsonObject)?.let(::ObjectCtx)
        }

        override fun arrayOrNull(): Deserializer.ArrayCtx? {
            return (iter.next() as? JsonArray)?.let(::ArrayCtx)
        }
    }

    override fun obj(obj: JsonObject): Deserializer.ObjectCtx {
        return ObjectCtx(obj)
    }

    override fun array(arr: JsonArray): Deserializer.ArrayCtx {
        return ArrayCtx(arr)
    }
}