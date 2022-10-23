package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.serde.jackson.JsonSerde as JacksonJsonSerde
import dev.evo.elasticmagic.serde.jackson.JsonSerde as JacksonPrettyJsonSerde
import dev.evo.elasticmagic.serde.jackson.YamlSerde as JacksonYamlSerde
import dev.evo.elasticmagic.serde.serialization.JsonSerde as SerializationJsonSerde
import dev.evo.elasticmagic.serde.serialization.PrettyJsonSerde as SerializationPrettyJsonSerde

actual val apiSerdes = listOf(
    SerializationJsonSerde,
    SerializationPrettyJsonSerde,
    JacksonJsonSerde,
    JacksonPrettyJsonSerde,
    JacksonYamlSerde,
)
actual val defaultBulkSerde: Serde.OneLineJson = SerializationJsonSerde
