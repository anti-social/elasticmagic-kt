package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.serde.serialization.JsonSerde as SerializationSerde

actual val serdes: List<Serde> = listOf(
    SerializationSerde,
)
