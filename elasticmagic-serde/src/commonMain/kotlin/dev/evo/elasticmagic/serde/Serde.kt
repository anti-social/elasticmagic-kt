package dev.evo.elasticmagic.serde

interface Serde {
    val contentType: String

    val serializer: Serializer

    val deserializer: Deserializer

    abstract class Json : Serde {
        override val contentType = "application/json"
    }

    abstract class OneLineJson : Json()
}
