package dev.evo.elasticmagic.json

import dev.evo.elasticmagic.Source

import kotlinx.serialization.json.JsonElement

class JsonSource : Source() {
    val source: MutableMap<String, JsonElement> = mutableMapOf()

    override fun setField(name: String, value: Any?) {
        source[name] = value as JsonElement
    }

    override fun getField(name: String): Any? {
        return source[name]
    }
}