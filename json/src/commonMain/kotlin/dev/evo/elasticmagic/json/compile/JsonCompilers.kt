package dev.evo.elasticmagic.json.compile

import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.compile.CompilerProvider
import dev.evo.elasticmagic.compile.MappingCompiler
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.json.JsonDeserializer
import dev.evo.elasticmagic.json.JsonSerializer

import kotlinx.serialization.json.JsonObject

class JsonCompilers(esVersion: ElasticsearchVersion) : CompilerProvider<JsonObject> {
    override val serializer = JsonSerializer()
    override val deserializer = JsonDeserializer()

    override val mapping: MappingCompiler<JsonObject> =
        MappingCompiler(esVersion, serializer)

    override val searchQuery: SearchQueryCompiler<JsonObject> =
        SearchQueryCompiler(esVersion, serializer)
}
