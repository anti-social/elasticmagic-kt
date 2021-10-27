package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.doc.Action
import dev.evo.elasticmagic.BulkError
import dev.evo.elasticmagic.BulkItem
import dev.evo.elasticmagic.BulkOpType
import dev.evo.elasticmagic.BulkResult
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.doc.Refresh
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.toMap
import dev.evo.elasticmagic.toRequestParameters
import dev.evo.elasticmagic.transport.Request
import dev.evo.elasticmagic.transport.Method

class PreparedBulk(
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
    fun compile(
        serializer: Serializer,
        input: PreparedBulk
    ): Request<List<ActionCompiler.Compiled>, BulkResult> {
        val params = Params(
            input.params,
            "refresh" to input.refresh?.toValue(),
            "timeout" to input.timeout,
        )
        return Request(
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
                            shard = errorObj.intOrNull("shard"),
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
