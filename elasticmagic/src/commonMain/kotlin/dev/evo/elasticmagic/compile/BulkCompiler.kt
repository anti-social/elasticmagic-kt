package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.bulk.Action
import dev.evo.elasticmagic.BulkError
import dev.evo.elasticmagic.BulkItem
import dev.evo.elasticmagic.BulkOpType
import dev.evo.elasticmagic.BulkResult
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.bulk.Refresh
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.forEachObj
import dev.evo.elasticmagic.serde.toMap
import dev.evo.elasticmagic.toRequestParameters
import dev.evo.elasticmagic.transport.ApiResponse
import dev.evo.elasticmagic.transport.Parameters

class PreparedBulk(
    val indexName: String,
    val actions: List<Action<*>>,
    val refresh: Refresh? = null,
    val timeout: String? = null,
    val params: Params = Params(),
)

class BulkCompiler(
    features: ElasticsearchFeatures,
    private val actionCompiler: ActionCompiler,
) : BaseCompiler(features) {

    class Compiled(
        val path: String,
        val parameters: Parameters,
        val body: List<ActionCompiler.Compiled>,
        val processResult: (ApiResponse) -> BulkResult
    )

    fun compile(
        serializer: Serializer,
        input: PreparedBulk
    ): Compiled {
        val params = Params(
            input.params,
            "refresh" to input.refresh?.toValue(),
            "timeout" to input.timeout,
        )
        return Compiled(
            path = "${input.indexName}/_bulk",
            parameters = params.toRequestParameters(),
            body = input.actions.map { action ->
                actionCompiler.compile(serializer, action)
            },
            processResult = ::processResult,
        )
    }

    fun processResult(response: ApiResponse): BulkResult {
        val ctx = response.content
        val bulkItems = buildList {
            ctx.array("items").forEachObj { itemObjWrapper ->
                val (itemObj, itemOpType) = itemObjWrapper.objOrNull("index")?.let { it to BulkOpType.INDEX }
                    ?: itemObjWrapper.objOrNull("delete")?.let { it to BulkOpType.DELETE }
                    ?: itemObjWrapper.objOrNull("update")?.let { it to BulkOpType.UPDATE }
                    ?: itemObjWrapper.objOrNull("create")?.let { it to BulkOpType.CREATE }
                    ?: throw IllegalArgumentException("Unknown bulk item: ${itemObjWrapper.toMap()}")
                val index = itemObj.string("_index")
                val type = itemObj.stringOrNull("_type") ?: "_doc"
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
                add(bulkItem)
            }
        }
        return BulkResult(
            errors = ctx.boolean("errors"),
            took = ctx.long("took"),
            items = bulkItems
        )
    }
}
