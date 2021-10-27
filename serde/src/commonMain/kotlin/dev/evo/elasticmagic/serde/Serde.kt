package dev.evo.elasticmagic.serde

interface Serde {
    val contentType: String

    val serializer: Serializer

    val deserializer: Deserializer
}
