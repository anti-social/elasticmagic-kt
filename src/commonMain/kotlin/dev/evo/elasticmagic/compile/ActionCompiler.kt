package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.Action
import dev.evo.elasticmagic.ActionMeta
import dev.evo.elasticmagic.BulkError
import dev.evo.elasticmagic.BulkItem
import dev.evo.elasticmagic.BulkOpType
import dev.evo.elasticmagic.BulkResult
import dev.evo.elasticmagic.ConcurrencyControl
import dev.evo.elasticmagic.DeleteAction
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.IndexAction
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.Refresh
import dev.evo.elasticmagic.UpdateAction
import dev.evo.elasticmagic.UpdateSource
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.toMap
import dev.evo.elasticmagic.toRequestParameters
import dev.evo.elasticmagic.transport.Method

class Bulk(
    val indexName: String,
    val actions: List<Action<*>>,
    val refresh: Refresh? = null,
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
        val bulkItems = mutableListOf<BulkItem>()
        val items = ctx.array("items")
        while (items.hasNext()) {
            val itemObjWrapper = items.obj()
            val (itemObj, itemOpType) = itemObjWrapper.objOrNull("index")?.let { it to BulkOpType.INDEX }
                ?: itemObjWrapper.objOrNull("delete")?.let { it to BulkOpType.DELETE }
                ?: itemObjWrapper.objOrNull("update")?.let { it to BulkOpType.UPDATE }
                ?: itemObjWrapper.objOrNull("create")?.let { it to BulkOpType.CREATE }
                ?: throw IllegalArgumentException("Unknown bulk item: ${itemObjWrapper.toMap()}")
            val index = itemObj.string("_index")
            val type = itemObj.string("_type")
            val id = itemObj.string("_id")
            val routing = itemObj.stringOrNull("_routing")
            val status = itemObj.int("status")
            val bulkItem = when (val errorObj = itemObj.objOrNull("error")) {
                null -> {
                    BulkItem.Ok(
                        opType = itemOpType,
                        index = index,
                        type = type,
                        id = id,
                        routing = routing,
                        status = status,
                        version = itemObj.long("_version"),
                        seqNo = itemObj.long("_seq_no"),
                        primaryTerm = itemObj.long("_primary_term"),
                        result = itemObj.string("result"),
                    )
                }
                else -> {
                    BulkItem.Error(
                        opType = itemOpType,
                        index = index,
                        type = type,
                        id = id,
                        routing = routing,
                        status = status,
                        error = BulkError(
                            type = errorObj.string("type"),
                            reason = errorObj.string("reason"),
                            index = errorObj.string("index"),
                            indexUuid = errorObj.string("index_uuid"),
                            shard = errorObj.int("shard"),
                        )
                    )
                }
            }
            bulkItems.add(bulkItem)
        }
        return BulkResult(
            errors = ctx.boolean("errors"),
            took = ctx.long("took"),
            items = bulkItems
        )
    }
}

class ActionCompiler(
    esVersion: ElasticsearchVersion,
    private val actionMetaCompiler: ActionMetaCompiler,
    private val actionSourceCompiler: ActionSourceCompiler,
) : BaseCompiler(esVersion) {

    data class Compiled<OBJ>(val header: OBJ, val source: OBJ?)

    fun <OBJ> compile(serializer: Serializer<OBJ>, input: Action<*>): Compiled<OBJ> {
        return Compiled(
            actionMetaCompiler.compile(serializer, input),
            actionSourceCompiler.compile(serializer, input)
        )
    }
}

class ActionMetaCompiler(
    esVersion: ElasticsearchVersion
) : BaseCompiler(esVersion) {
    fun <OBJ> compile(serializer: Serializer<OBJ>, input: Action<*>): OBJ {
        return serializer.buildObj {
            visit(this, input)
        }
    }

    private fun visit(ctx: Serializer.ObjectCtx, action: Action<*>) {
        ctx.obj(action.name) {
            val meta = action.meta
            visit(this, meta)

            when (action.concurrencyControl) {
                ConcurrencyControl.SEQ_NO -> {
                    field("if_seq_no", meta.seqNo)
                    field("if_primary_term", meta.primaryTerm)
                }
                ConcurrencyControl.VERSION -> {
                    field("version_type", "external")
                    fieldIfNotNull("version", meta.version)
                }
                ConcurrencyControl.VERSION_GTE -> {
                    field("version_type", "external_gte")
                    fieldIfNotNull("version", meta.version)
                }
            }
        }
    }

    private fun visit(ctx: Serializer.ObjectCtx, meta: ActionMeta) {
        ctx.fieldIfNotNull("_id", meta.id)
        ctx.fieldIfNotNull("routing", meta.routing)
    }
}

class ActionSourceCompiler(
    esVersion: ElasticsearchVersion,
    private val searchQueryCompiler: SearchQueryCompiler,
) : BaseCompiler(esVersion) {
    fun <OBJ> compile(serializer: Serializer<OBJ>, input: Action<*>): OBJ? {
        if (input is DeleteAction) {
            return null
        }
        return serializer.buildObj {
            visit(this, input)
        }
    }

    private fun visit(ctx: Serializer.ObjectCtx, action: Action<*>) {
        when (action) {
            is IndexAction<*> -> {
                visit(ctx, action.source.getSource())
            }
            is UpdateAction<*> -> {
                val source = action.source
                if (source.upsert != null) {
                    ctx.obj("upsert") {
                        visit(this, source.upsert.getSource())
                    }
                }
                ctx.fieldIfNotNull("detect_noop", source.detectNoop)

                when (source) {
                    is UpdateSource.WithDoc<*> -> {
                        ctx.obj("doc") {
                            visit(this, source.doc.getSource())
                        }
                        ctx.fieldIfNotNull("doc_as_upsert", source.docAsUpsert)
                    }
                    is UpdateSource.WithScript<*> -> {
                        searchQueryCompiler.visit(ctx, source.script)
                    }
                }
            }
            is DeleteAction -> {}
        }
    }
}
