package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.UpdateMappingResult
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.transport.ApiRequest
import dev.evo.elasticmagic.transport.Method
import dev.evo.elasticmagic.transport.Parameters

class PreparedUpdateMapping(
    val indexName: String,
    val mapping: Document,
    val allowNoIndices: Boolean?,
    val ignoreUnavailable: Boolean?,
    val writeIndexOnly: Boolean?,
    val masterTimeout: String?,
    val timeout: String?,
)

class UpdateMappingCompiler(
    features: ElasticsearchFeatures,
    private val mappingCompiler: MappingCompiler,
) : BaseCompiler(features) {
    fun compile(
        serde: Serde,
        input: PreparedUpdateMapping
    ): ApiRequest<UpdateMappingResult> {
        val path = if (features.requiresMappingTypeName) {
            "${input.indexName}/_mapping/_doc"
        } else {
            "${input.indexName}/_mapping"

        }
        return ApiRequest(
            method = Method.PUT,
            path = path,
            parameters = Parameters(
                "allow_no_indices" to input.allowNoIndices,
                "ignore_unavailable" to input.ignoreUnavailable,
                "write_index_only" to input.writeIndexOnly,
                "master_timeout" to input.masterTimeout,
                "timeout" to input.timeout,
            ),
            body = serde.serializer.obj {
                mappingCompiler.visit(this, input.mapping)
            },
            serde = serde,
            processResponse = ::processResponse,
        )
    }

    fun processResponse(ctx: Deserializer.ObjectCtx): UpdateMappingResult {
        return UpdateMappingResult(
            acknowledged = ctx.boolean("acknowledged"),
        )
    }
}
