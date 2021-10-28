package dev.evo.elasticmagic.serde.jackson

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper

import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.StdDeserializer

object JsonDeserializer : StdDeserializer() {
    private val mapper = ObjectMapper()
    private val typeRef = object : TypeReference<Map<String, Any?>>() {}

    override fun objFromStringOrNull(data: String): Deserializer.ObjectCtx? {
        return try {
            ObjectCtx(mapper.readValue(data, typeRef))
        } catch (ex: JsonMappingException) {
            null
        }
    }
}
