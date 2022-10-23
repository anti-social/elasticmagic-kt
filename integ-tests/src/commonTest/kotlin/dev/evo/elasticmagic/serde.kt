package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.Serde

expect val apiSerdes: List<Serde>
expect val defaultBulkSerde: Serde.OneLineJson
