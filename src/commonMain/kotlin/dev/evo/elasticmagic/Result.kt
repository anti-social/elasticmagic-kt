package dev.evo.elasticmagic

data class SearchQueryResult<S: BaseDocSource>(
    val rawResult: Map<String, Any?>?,
    val took: Long,
    val timedOut: Boolean,
    val totalHits: Long?,
    val totalHitsRelation: String?,
    val maxScore: Double?,
    val hits: List<SearchHit<S>>,
    val aggs: Map<String, AggregationResult>,
) {
    inline fun <reified A: AggregationResult> agg(name: String): A {
        return aggs[name] as A
    }
}

data class SearchHit<S: BaseDocSource>(
    val index: String,
    val type: String,
    override val id: String,
    override val routing: String? = null,
    override val version: Long? = null,
    override val seqNo: Long? = null,
    override val primaryTerm: Long? = null,
    val score: Double? = null,
    val sort: List<Any>? = null,
    val source: S? = null,
) : ActionMeta

data class CreateIndexResult(
    val acknowledged: Boolean,
    val shardsAcknowledged: Boolean,
    val index: String,
)

data class UpdateMappingResult(
    val acknowledged: Boolean,
)

data class DeleteIndexResult(
    val acknowledged: Boolean,
)

data class BulkResult(
    val errors: Boolean,
    val took: Long,
    val items: List<BulkItem>,
)

sealed class BulkItem {
    abstract val opType: BulkOpType
    abstract val index: String
    abstract val type: String
    abstract val id: String
    abstract val routing: String?
    abstract val status: Int

    data class Ok(
        override val opType: BulkOpType,
        override val index: String,
        override val type: String,
        override val id: String,
        override val routing: String?,
        override val status: Int,

        override val version: Long,
        override val seqNo: Long,
        override val primaryTerm: Long,

        val result: String,
        // TODO:
        // val shards: List<ShardsInfo>
    ) : BulkItem(), IdentActionMeta

    data class Error(
        override val opType: BulkOpType,
        override val index: String,
        override val type: String,
        override val id: String,
        override val routing: String?,
        override val status: Int,
        val error: BulkError,
    ) : BulkItem()
}

enum class BulkOpType {
    INDEX, CREATE, DELETE, UPDATE
}

data class BulkError(
    val type: String,
    val reason: String,
    val index: String,
    val indexUuid: String,
    val shard: Int?,
)
