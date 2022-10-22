package dev.evo.elasticmagic.serde.jackson

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper

import dev.evo.elasticmagic.serde.StdSerializer

object YamlSerializer : StdSerializer(::ObjectCtx, ::ArrayCtx) {
    private val mapper = YAMLMapper()

    class ObjectCtx : StdSerializer.ObjectCtx(HashMap()) {
        override fun serialize(): String {
            return mapper.writeValueAsString(map)
        }
    }

    class ArrayCtx : StdSerializer.ArrayCtx(ArrayList()) {
        override fun serialize(): String {
            return mapper.writeValueAsString(array)
        }
    }
}
