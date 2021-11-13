package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.jackson.JsonSerde as JacksonSerde
import dev.evo.elasticmagic.serde.serialization.JsonSerde as SerializationSerde

actual val serdes = listOf(
    SerializationSerde,
    JacksonSerde,
)
