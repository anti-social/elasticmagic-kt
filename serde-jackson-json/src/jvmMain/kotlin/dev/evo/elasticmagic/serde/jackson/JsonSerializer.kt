package dev.evo.elasticmagic.serde.jackson

import com.fasterxml.jackson.databind.ObjectMapper

import dev.evo.elasticmagic.serde.StdSerializer

object JsonSerializer : StdSerializer(::ObjectCtx, ::ArrayCtx) {
    private val mapper = ObjectMapper()

    class ObjectCtx : StdSerializer.ObjectCtx(HashMap()) {
        override fun serialize(): String {
            return mapper.writeValueAsString(map)
        }
    }

    class ArrayCtx : StdSerializer.ArrayCtx(ArrayList())
}
