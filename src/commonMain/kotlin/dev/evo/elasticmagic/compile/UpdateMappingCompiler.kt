package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.Parameters
import dev.evo.elasticmagic.UpdateMappingResult
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.transport.Method

class UpdateMappingRequest(
    val indexName: String,
    val mapping: Document,
    val allowNoIndices: Boolean?,
    val ignoreUnavailable: Boolean?,
    val writeIndexOnly: Boolean?,
    val masterTimeout: String?,
    val timeout: String?,
)

class UpdateMappingCompiler(
    esVersion: ElasticsearchVersion,
    val features: ElasticsearchFeatures,
    private val mappingCompiler: MappingCompiler,
) : BaseCompiler(esVersion) {
    fun <OBJ> compile(
        serializer: Serializer<OBJ>,
        input: UpdateMappingRequest
    ): Compiled<OBJ, UpdateMappingResult> {
        val path = if (features.requiresMappingTypeName) {
            "${input.indexName}/_mapping/_doc"
        } else {
            "${input.indexName}/_mapping"

        }
        return Compiled(
            method = Method.PUT,
            path = path,
            parameters = Parameters(
                "allow_no_indices" to input.allowNoIndices,
                "ignore_unavailable" to input.ignoreUnavailable,
                "write_index_only" to input.writeIndexOnly,
                "master_timeout" to input.masterTimeout,
                "timeout" to input.timeout,
            ),
            body = serializer.buildObj {
                mappingCompiler.visit(this, input.mapping)
            },
            processResult = ::processResult,
        )
    }

    fun processResult(ctx: Deserializer.ObjectCtx): UpdateMappingResult {
        return UpdateMappingResult(
            acknowledged = ctx.boolean("acknowledged"),
        )
    }
}
