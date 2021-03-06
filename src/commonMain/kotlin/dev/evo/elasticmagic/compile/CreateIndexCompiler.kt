package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.CreateIndexResult
import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.Parameters
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.transport.Method

class CreateIndexRequest(
    val indexName: String,
    val settings: Params,
    val mapping: Document? = null,
    val aliases: Params = Params(),
    val waitForActiveShards: Boolean? = null,
    val masterTimeout: String? = null,
    val timeout: String? = null,
)

class CreateIndexCompiler(
    esVersion: ElasticsearchVersion,
    val features: ElasticsearchFeatures,
    val mappingCompiler: MappingCompiler,
) : BaseCompiler(esVersion) {

    fun <OBJ> compile(
        serializer: Serializer<OBJ>,
        input: CreateIndexRequest
    ): Compiled<OBJ, CreateIndexResult> {
        return Compiled(
            method = Method.PUT,
            path = input.indexName,
            parameters = Parameters(
                "wait_for_active_shards" to input.waitForActiveShards,
                "master_timeout" to input.masterTimeout,
                "timeout" to input.timeout,
            ),
            body = serializer.buildObj {
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
            processResult = ::processResult
        )
    }

    fun processResult(ctx: Deserializer.ObjectCtx): CreateIndexResult {
        return CreateIndexResult(
            acknowledged = ctx.boolean("acknowledged"),
            shardsAcknowledged = ctx.boolean("shards_acknowledged"),
            index = ctx.string("index"),
        )
    }
}
