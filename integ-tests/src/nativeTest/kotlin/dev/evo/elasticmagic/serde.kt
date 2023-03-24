package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.serde.kotlinx.JsonSerde as SerializationJsonSerde
import dev.evo.elasticmagic.serde.kotlinx.PrettyJsonSerde as SerializationPrettyJsonSerde

actual val apiSerdes: List<Serde> = listOf(
    SerializationJsonSerde,
    SerializationPrettyJsonSerde,
)
actual val defaultBulkSerde: Serde.OneLineJson = SerializationJsonSerde
