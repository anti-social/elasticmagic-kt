package dev.evo.elasticmagic.serde.jackson

import dev.evo.elasticmagic.serde.Serde

sealed class YamlSerde(
    override val serializer: YamlSerializer,
    override val deserializer: YamlDeserializer,
) : Serde {
    override val contentType = "application/yaml"

    companion object : YamlSerde(YamlSerializer, YamlDeserializer)
}
