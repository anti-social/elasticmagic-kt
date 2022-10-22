package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.serde.jackson.JsonSerde as JacksonJsonSerde
import dev.evo.elasticmagic.serde.jackson.YamlSerde as JacksonYamlSerde
import dev.evo.elasticmagic.serde.serialization.JsonSerde as SerializationJsonSerde

actual val apiSerdes = listOf(
    SerializationJsonSerde,
    JacksonJsonSerde,
    JacksonYamlSerde,
)
actual val defaultBulkSerde: Serde.Json = SerializationJsonSerde
