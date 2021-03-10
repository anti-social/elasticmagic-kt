package dev.evo.elasticmagic.json.compile

import dev.evo.elasticmagic.compile.CompilerProvider
import dev.evo.elasticmagic.compile.MappingCompiler
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.json.JsonDeserializer
import dev.evo.elasticmagic.json.JsonSerializer
import dev.evo.elasticmagic.serde.Deserializer

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

object JsonCompilerProvider : CompilerProvider<JsonObject, JsonArray> {
    override val serializer = JsonSerializer()
    override val deserializer = JsonDeserializer()

    override val mapping: MappingCompiler<JsonObject, JsonArray> =
        MappingCompiler(serializer)

    override val searchQuery: SearchQueryCompiler<JsonObject, JsonArray> =
        SearchQueryCompiler(serializer)
}
