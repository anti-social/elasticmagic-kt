package dev.evo.elasticmagic.serde.jackson

import dev.evo.elasticmagic.serde.Serde

sealed class JsonSerde(
    override val serializer: JsonSerializer,
    override val deserializer: JsonDeserializer,
) : Serde.Json() {
    companion object : JsonSerde(JsonSerializer, JsonDeserializer)
}
