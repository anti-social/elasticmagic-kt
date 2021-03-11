package dev.evo.elasticmagic.json.compile

import dev.evo.elasticmagic.compile.CompilerProvider
import dev.evo.elasticmagic.compile.MappingCompiler
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.json.JsonDeserializer
import dev.evo.elasticmagic.json.JsonSerializer

import kotlinx.serialization.json.JsonObject

object JsonCompilerProvider : CompilerProvider<JsonObject> {
    override val serializer = JsonSerializer()
    override val deserializer = JsonDeserializer()

    override val mapping: MappingCompiler<JsonObject> =
        MappingCompiler(serializer)

    override val searchQuery: SearchQueryCompiler<JsonObject> =
        SearchQueryCompiler(serializer)
}
