package dev.evo.elasticmagic.serde.jackson

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper

import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.StdDeserializer

sealed class JsonDeserializer : StdDeserializer() {
    private val mapper = ObjectMapper()
    private val typeRef = object : TypeReference<Map<String, Any?>>() {}

    companion object : JsonDeserializer()

    override fun objFromStringOrNull(data: String): Deserializer.ObjectCtx? {
        return ObjectCtx(mapper.readValue(data, typeRef))
    }
}
