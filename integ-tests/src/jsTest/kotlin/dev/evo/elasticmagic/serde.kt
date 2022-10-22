package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.serde.serialization.JsonSerde as SerializationJsonSerde

actual val apiSerdes: List<Serde> = listOf(
    SerializationJsonSerde,
)
actual val defaultBulkSerde: Serde.Json = SerializationJsonSerde
