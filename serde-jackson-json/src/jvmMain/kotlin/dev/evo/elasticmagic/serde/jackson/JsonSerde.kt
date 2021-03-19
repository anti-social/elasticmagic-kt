package dev.evo.elasticmagic.serde.jackson

import dev.evo.elasticmagic.serde.Serde

sealed class JsonSerde(
    override val serializer: JsonSerializer,
    override val deserializer: JsonDeserializer,
) : Serde<Map<String, Any?>> {
    override val contentType = "application/json"

    companion object : JsonSerde(JsonSerializer, JsonDeserializer)
}
