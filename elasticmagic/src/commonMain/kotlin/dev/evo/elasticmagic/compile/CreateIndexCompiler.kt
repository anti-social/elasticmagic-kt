package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.CreateIndexResult
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.transport.ApiRequest
import dev.evo.elasticmagic.transport.Method
import dev.evo.elasticmagic.transport.Parameters

class PreparedCreateIndex(
    val indexName: String,
    val settings: Params,
    val mapping: Document? = null,
    val aliases: Params = Params(),
    val waitForActiveShards: Boolean? = null,
    val masterTimeout: String? = null,
    val timeout: String? = null,
)

class CreateIndexCompiler(
    features: ElasticsearchFeatures,
    val mappingCompiler: MappingCompiler,
) : BaseCompiler(features) {

    fun compile(
        serde: Serde,
        input: PreparedCreateIndex,
    ): ApiRequest<CreateIndexResult> {
        return ApiRequest(
            method = Method.PUT,
            path = input.indexName,
            parameters = Parameters(
                "wait_for_active_shards" to input.waitForActiveShards,
                "master_timeout" to input.masterTimeout,
                "timeout" to input.timeout,
            ),
            body = serde.serializer.obj {
                if (input.settings.isNotEmpty()) {
                    obj("settings") {
                        visit(this, input.settings)
                    }
                }
                if (input.mapping != null) {
                    obj("mappings") {
                        if (features.requiresMappingTypeName) {
                            obj("_doc") {
                                mappingCompiler.visit(this, input.mapping)
                            }
                        } else {
                            mappingCompiler.visit(this, input.mapping)
                        }
                    }
                }
                if (input.aliases.isNotEmpty()) {
                    obj("aliases") {
                        visit(this, input.aliases)
                    }
                }
            },
            serde = serde,
            processResponse = ::processResponse
        )
    }

    fun processResponse(ctx: Deserializer.ObjectCtx): CreateIndexResult {
        return CreateIndexResult(
            acknowledged = ctx.boolean("acknowledged"),
            shardsAcknowledged = ctx.boolean("shards_acknowledged"),
            index = ctx.string("index"),
        )
    }
}
