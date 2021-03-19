package dev.evo.elasticmagic.serde.serialization

import dev.evo.elasticmagic.serde.Serde

import kotlinx.serialization.json.JsonObject

sealed class JsonSerde(
    override val serializer: JsonSerializer,
    override val deserializer: JsonDeserializer,
) : Serde<JsonObject> {
    override val contentType = "application/json"

    companion object : JsonSerde(JsonSerializer, JsonDeserializer)
}
