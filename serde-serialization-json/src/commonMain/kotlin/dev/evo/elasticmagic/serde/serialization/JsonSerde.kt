package dev.evo.elasticmagic.serde.serialization

import dev.evo.elasticmagic.serde.Serde

sealed class JsonSerde(
    override val serializer: JsonSerializer,
    override val deserializer: JsonDeserializer,
) : Serde {
    override val contentType = "application/json"

    companion object : JsonSerde(JsonSerializer, JsonDeserializer)
}
