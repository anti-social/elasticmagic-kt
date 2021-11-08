package dev.evo.elasticmagic.serde.serialization

import dev.evo.elasticmagic.serde.Serializer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed class JsonSerializer : Serializer {

    companion object : JsonSerializer()

    class ObjectCtx : Serializer.ObjectCtx {
        private val content: MutableMap<String, JsonElement> = HashMap()

        fun build(): JsonObject {
            return JsonObject(content)
        }

        override fun field(name: String, value: Int?) {
            content[name] = JsonPrimitive(value)
        }

        override fun field(name: String, value: Long?) {
            content[name] = JsonPrimitive(value)
        }

        override fun field(name: String, value: Float?) {
            content[name] = JsonPrimitive(value)
        }

        override fun field(name: String, value: Double?) {
            content[name] = JsonPrimitive(value)
        }

        override fun field(name: String, value: Boolean?) {
            content[name] = JsonPrimitive(value)
        }

        override fun field(name: String, value: String?) {
            content[name] = JsonPrimitive(value)
        }

        override fun obj(name: String, block: Serializer.ObjectCtx.() -> Unit) {
            content[name] = JsonObject(ObjectCtx().apply(block).build())
        }

        override fun array(name: String, block: Serializer.ArrayCtx.() -> Unit) {
            content[name] = JsonArray(ArrayCtx().apply(block).build())
        }

        override fun serialize(): String {
            return Json.encodeToString(JsonObject.serializer(), build())
        }
    }

    class ArrayCtx : Serializer.ArrayCtx {
        private val content: MutableList<JsonElement> = ArrayList()

        fun build(): JsonArray {
            return JsonArray(content)
        }

        override fun value(v: Int?) {
            content.add(JsonPrimitive(v))
        }

        override fun value(v: Long?) {
            content.add(JsonPrimitive(v))
        }

        override fun value(v: Float?) {
            content.add(JsonPrimitive(v))
        }

        override fun value(v: Double?) {
            content.add(JsonPrimitive(v))
        }

        override fun value(v: Boolean?) {
            content.add(JsonPrimitive(v))
        }

        override fun value(value: String?) {
            content.add(JsonPrimitive(value))
        }

        override fun obj(block: Serializer.ObjectCtx.() -> Unit) {
            content.add(JsonObject(ObjectCtx().apply(block).build()))
        }

        override fun array(block: Serializer.ArrayCtx.() -> Unit) {
            content.add(ArrayCtx().apply(block).build())
        }

        override fun serialize(): String {
            return Json.encodeToString(JsonArray.serializer(), build())
        }
    }

    override fun obj(block: Serializer.ObjectCtx.() -> Unit): Serializer.ObjectCtx {
        return ObjectCtx().apply(block)
    }
}
