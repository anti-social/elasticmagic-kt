package dev.evo.elasticmagic.serde.jackson

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.dataformat.yaml.JacksonYAMLParseException
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper

import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.StdDeserializer

object YamlDeserializer : StdDeserializer() {
    private val mapper = YAMLMapper()
    private val typeRef = object : TypeReference<Map<String, Any?>>() {}

    override fun objFromStringOrNull(data: String): Deserializer.ObjectCtx? {
        return try {
            ObjectCtx(mapper.readValue(data, typeRef))
        } catch (ex: JacksonYAMLParseException) {
            null
        }
    }
}
