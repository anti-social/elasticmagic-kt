package dev.evo.elasticmagic.serde.kotlinx

import dev.evo.elasticmagic.serde.Serde

object JsonSerde : Serde.OneLineJson() {
    override val serializer = JsonSerializer
    override val deserializer = JsonDeserializer
}

object PrettyJsonSerde : Serde.Json() {
    override val serializer = PrettyJsonSerializer
    override val deserializer = JsonDeserializer
}
