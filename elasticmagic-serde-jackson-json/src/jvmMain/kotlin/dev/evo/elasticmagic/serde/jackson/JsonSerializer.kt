package dev.evo.elasticmagic.serde.jackson

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter

import dev.evo.elasticmagic.serde.StdSerializer

object JsonSerializer : StdSerializer(::ObjectCtx, ::ArrayCtx) {
    private val writer: ObjectWriter = ObjectMapper()
        .setDefaultPrettyPrinter(
            MinimalPrettyPrinter()
        )
        .writerWithDefaultPrettyPrinter()

    class ObjectCtx : StdSerializer.ObjectCtx(HashMap()) {
        override fun serialize(): String {
            return writer.writeValueAsString(map)
        }
    }

    class ArrayCtx : StdSerializer.ArrayCtx(ArrayList()) {
        override fun serialize(): String {
            return writer.writeValueAsString(array)
        }
    }
}

object PrettyJsonSerializer : StdSerializer(::ObjectCtx, ::ArrayCtx) {
    private const val INDENTATION = 4
    private val writer: ObjectWriter = ObjectMapper()
        .setDefaultPrettyPrinter(
            DefaultPrettyPrinter()
                .withObjectIndenter(DefaultIndenter(" ".repeat(INDENTATION), "\n"))
        )
        .writerWithDefaultPrettyPrinter()

    class ObjectCtx : StdSerializer.ObjectCtx(HashMap()) {
        override fun serialize(): String {
            return writer.writeValueAsString(map)
        }
    }

    class ArrayCtx : StdSerializer.ArrayCtx(ArrayList()) {
        override fun serialize(): String {
            return writer.writeValueAsString(array)
        }
    }
}
