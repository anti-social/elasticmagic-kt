package dev.evo.elasticmagic.serde.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import dev.evo.elasticmagic.serde.StdSerializer

sealed class JsonSerializer : StdSerializer() {
    private val mapper = ObjectMapper()

    companion object : JsonSerializer()

    override fun objToString(obj: Map<String, Any?>): String {
        return mapper.writeValueAsString(obj)
    }
}
