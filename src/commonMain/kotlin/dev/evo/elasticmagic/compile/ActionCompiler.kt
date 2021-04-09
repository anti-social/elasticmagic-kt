package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.Action
import dev.evo.elasticmagic.ActionMeta
import dev.evo.elasticmagic.BaseSource
import dev.evo.elasticmagic.BulkResult
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.IndexAction
import dev.evo.elasticmagic.IndexSource
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.Source
import dev.evo.elasticmagic.UpdateSource
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.toMap
import dev.evo.elasticmagic.toRequestParameters
import dev.evo.elasticmagic.transport.Method

class Bulk(
    val indexName: String,
    val actions: List<Action>,
    val refresh: Action.Refresh? = null,
    val timeout: String? = null,
    val params: Params = Params(),
)

class BulkCompiler(
    esVersion: ElasticsearchVersion,
    private val actionCompiler: ActionCompiler,
) : BaseCompiler(esVersion) {
    fun <OBJ> compile(
        serializer: Serializer<OBJ>,
        input: Bulk
    ): Compiled<List<ActionCompiler.Compiled<OBJ>>, BulkResult> {
        val params = Params(
            input.params,
            "refresh" to input.refresh?.toValue(),
            "timeout" to input.timeout,
        )
        return Compiled(
            method = Method.POST,
            path = "${input.indexName}/_bulk",
            parameters = params.toRequestParameters(),
            body = input.actions.map { action ->
                actionCompiler.compile(serializer, action)
            },
            processResult = ::processResult,
        )
    }

    fun processResult(ctx: Deserializer.ObjectCtx): BulkResult {
        println(ctx.toMap())
        return BulkResult(
            errors = ctx.boolean("errors"),
        )
    }
}

class ActionCompiler(
    esVersion: ElasticsearchVersion,
    private val actionMetaCompiler: ActionMetaCompiler,
    private val actionSourceCompiler: ActionSourceCompiler,
) : BaseCompiler(esVersion) {

    data class Compiled<OBJ>(val header: OBJ, val source: OBJ?)

    fun <OBJ> compile(serializer: Serializer<OBJ>, input: Action): Compiled<OBJ> {
        return Compiled(
            actionMetaCompiler.compile(serializer, input),
            actionSourceCompiler.compile(serializer, input)
        )
    }
}

class ActionMetaCompiler(
    esVersion: ElasticsearchVersion
) : BaseCompiler(esVersion) {
    fun <OBJ> compile(serializer: Serializer<OBJ>, input: Action): OBJ {
        return serializer.buildObj {
            visit(this, input)
        }
    }

    private fun visit(ctx: Serializer.ObjectCtx, action: Action) {
        val meta = action.getActionMeta()
        ctx.obj(action.name) {
            visit(this, meta)
            if (action is UpdateSource<*>) {
                if (action.upsert != null) {
                    obj("upsert") {
                        visit(this, action.upsert.getSource())
                    }
                }
                fieldIfNotNull("detect_noop", action.detectNoop)
            }
        }
    }

    private fun visit(ctx: Serializer.ObjectCtx, meta: ActionMeta) {
        ctx.fieldIfNotNull("_id", meta.id)
        ctx.fieldIfNotNull("_routing", meta.routing)
        ctx.fieldIfNotNull("_version", meta.version)
    }
}

class ActionSourceCompiler(
    esVersion: ElasticsearchVersion,
    // private val docSourceCompiler: DocSourceCompiler,
) : BaseCompiler(esVersion) {
    fun <OBJ> compile(serializer: Serializer<OBJ>, input: Action): OBJ {
        return serializer.buildObj {
            visit(this, input)
        }
    }

    private fun visit(ctx: Serializer.ObjectCtx, action: Action) {
        val source = action.getActionSource()
        when (source) {
            is IndexSource<*> -> {
                // docSourceCompiler.visit(ctx, source.source)
                visit(ctx, source.source.getSource())
            }
            is UpdateSource.WithDoc<*> -> {

            }
            is UpdateSource.WithScript<*> -> {}
            null -> {}
        }
    }
}

// class DocSourceCompiler(
//     esVersion: ElasticsearchVersion,
// ) : BaseCompiler(esVersion) {
//     fun <OBJ> compile(serializer: Serializer<OBJ>, input: Source): OBJ {
//         return serializer.buildObj {
//             visit(this, input.getSource())
//         }
//     }
//
//     fun visit(ctx: Serializer.ObjectCtx, source: BaseSource) {
//         visit(ctx, source.getSource())
//     }
// }
