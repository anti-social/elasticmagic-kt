package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.Parameters
import dev.evo.elasticmagic.UpdateMappingResult
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.toMap
import dev.evo.elasticmagic.transport.Method

class UpdateMappingRequest(
    val indexName: String,
    val mapping: Document
)

class UpdateMappingCompiler(
    esVersion: ElasticsearchVersion,
    private val mappingCompiler: MappingCompiler,
) : BaseCompiler(esVersion) {
    fun <OBJ> compile(
        serializer: Serializer<OBJ>,
        input: UpdateMappingRequest
    ): Compiled<OBJ, UpdateMappingResult> {
        return Compiled(
            method = Method.PUT,
            path = "${input.indexName}/_mapping",
            parameters = Parameters(),
            body = serializer.buildObj {
                mappingCompiler.visit(this, input.mapping)
            },
            processResult = ::processResult,
        )
    }

    fun processResult(ctx: Deserializer.ObjectCtx): UpdateMappingResult {
        println(ctx.toMap())
        return UpdateMappingResult(
            acknowledged = ctx.boolean("acknowledged"),
        )
    }
}
