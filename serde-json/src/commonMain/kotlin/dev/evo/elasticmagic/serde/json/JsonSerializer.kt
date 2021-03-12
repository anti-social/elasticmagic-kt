package dev.evo.elasticmagic.serde.json

import dev.evo.elasticmagic.serde.Serializer

import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object JsonSerializer : Serializer<JsonObject> {
    private class ObjectCtx(private val objBuilder: JsonObjectBuilder) : Serializer.ObjectCtx {
        override fun field(name: String, value: Int?) {
            objBuilder.put(name, value)
        }

        override fun field(name: String, value: Long?) {
            objBuilder.put(name, value)
        }

        override fun field(name: String, value: Float?) {
            objBuilder.put(name, value)
        }

        override fun field(name: String, value: Double?) {
            objBuilder.put(name, value)
        }

        override fun field(name: String, value: Boolean?) {
            objBuilder.put(name, value)
        }

        override fun field(name: String, value: String?) {
            objBuilder.put(name, value)
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
        override fun value(value: Int?) {
            arrayBuilder.add(value)
        }

        override fun value(value: Long?) {
            arrayBuilder.add(value)
        }

        override fun value(value: Float?) {
            arrayBuilder.add(value)
        }

        override fun value(value: Double?) {
            arrayBuilder.add(value)
        }

        override fun value(value: Boolean?) {
            arrayBuilder.add(value)
        }

        override fun value(value: String?) {
            arrayBuilder.add(value)
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

    override fun obj(block: Serializer.ObjectCtx.() -> Unit): JsonObject {
        return buildJsonObject {
            ObjectCtx(this).block()
        }
    }
}
