package dev.evo.elasticmagic.serde

interface Serde<OBJ> {
    val contentType: String

    val serializer: Serializer<OBJ>

    val deserializer: Deserializer<OBJ>
}
