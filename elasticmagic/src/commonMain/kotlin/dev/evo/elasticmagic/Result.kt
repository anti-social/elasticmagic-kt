package dev.evo.elasticmagic

import dev.evo.elasticmagic.aggs.AggregationResult
import dev.evo.elasticmagic.bulk.ActionMeta
import dev.evo.elasticmagic.bulk.IdActionMeta
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.doc.BoundField
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.forEach
import dev.evo.elasticmagic.serde.forEachArray
import dev.evo.elasticmagic.serde.forEachObj
import dev.evo.elasticmagic.serde.toList
import dev.evo.elasticmagic.serde.toMap

abstract class AggAwareResult {
    abstract val aggs: Map<String, AggregationResult>

    inline fun <reified A : AggregationResult> aggIfExists(name: String): A? {
        return (aggs[name] ?: return null) as A
    }

    inline fun <reified A : AggregationResult> agg(name: String): A {
        return aggIfExists(name) ?: throw NoSuchElementException(name)
    }
}

data class SearchQueryResult<S : BaseDocSource>(
    val rawResult: Map<String, Any?>?,
    val took: Long,
    val timedOut: Boolean,
    val totalHits: Long?,
    val totalHitsRelation: String?,
    val maxScore: Float?,
    val hits: List<SearchHit<S>>,
    override val aggs: Map<String, AggregationResult>,
) : AggAwareResult()

data class MultiSearchQueryResult(
    val took: Long?,
    val responses: List<SearchQueryResult<BaseDocSource>>
) {
    inline fun <reified S : BaseDocSource> get(ix: Int): SearchQueryResult<S> {
        @Suppress("UNCHECKED_CAST")
        return responses[ix] as SearchQueryResult<S>
    }
}

data class Explanation(
    val value: Float,
    val description: String,
    val details: List<Explanation> = emptyList(),
) {
    companion object {
        operator fun invoke(rawHit: Deserializer.ObjectCtx): Explanation {
            val description = rawHit.string("description")
            val value = rawHit.float("value")
            val explanation = rawHit.arrayOrNull("details")?.let {
                buildList {
                    it.forEachObj { rawExplanation ->
                        add(Explanation(rawExplanation))
                    }
                }
            }

            return Explanation(value, description, explanation ?: emptyList())
        }
    }
}

data class SearchHit<S : BaseDocSource>(
    val index: String,
    val type: String,
    override val id: String? = null,
    override val routing: String? = null,
    override val version: Long? = null,
    override val seqNo: Long? = null,
    override val primaryTerm: Long? = null,
    val score: Float? = null,
    val sort: List<Any?>? = null,
    val source: S? = null,
    val fields: Fields = Fields(emptyMap()),
    val explanation: Explanation? = null,
) : ActionMeta {
    companion object {
        operator fun <S: BaseDocSource> invoke(
            rawHit: Deserializer.ObjectCtx,
            docSourceFactory: (obj: Deserializer.ObjectCtx) -> S,
        ): SearchHit<S> {
            val source = rawHit.objOrNull("_source")?.let { rawSource ->
                docSourceFactory(rawHit).apply {
                    // TODO: Don't convert to a map
                    fromSource(rawSource.toMap())
                }
            }
            val fields = rawHit.objOrNull("fields").let { rawFields ->
                val fields = buildMap {
                    rawFields?.forEachArray { fieldName, fieldValues ->
                        put(fieldName, fieldValues.toList().filterNotNull())
                    }
                }
                SearchHit.Fields(fields)
            }
            val rawSort = rawHit.arrayOrNull("sort")
            val sort = buildList {
                rawSort?.forEach { sortValue ->
                    add(sortValue)
                }
            }
            return SearchHit(
                index = rawHit.string("_index"),
                type = rawHit.stringOrNull("_type") ?: "_doc",
                id = rawHit.stringOrNull("_id"),
                routing = rawHit.stringOrNull("_routing"),
                version = rawHit.longOrNull("_version"),
                seqNo = rawHit.longOrNull("_seq_no"),
                primaryTerm = rawHit.longOrNull("_primary_term"),
                score = rawHit.floatOrNull("_score"),
                sort = sort.ifEmpty { null },
                source = source,
                fields = fields,
                explanation = rawHit.objOrNull("_explanation")?.let(Explanation::invoke),
            )
        }
    }

    class Fields(private val fields: Map<String, List<Any>>) {
        /**
         * Checks if the search hit contains the given field name.
         */
        operator fun contains(field: String): Boolean {
            return field in fields
        }

        /**
         * Returns the value for the corresponding field name.
         *
         * @throws NoSuchElementException if [field] name is missing in the search hit.
         */
        operator fun get(field: String): List<Any> {
            return fields[field]
                ?: throw NoSuchElementException("Field $field is missing")
        }

        /**
         * Checks if the search hit contains the given field.
         */
        operator fun contains(field: FieldOperations<*>): Boolean {
            return field.getQualifiedFieldName() in this
        }

        /**
         * Returns deserialized value for the corresponding field.
         *
         * @throws NoSuchElementException if [field] is missing in the search hit.
         * @throws dev.evo.elasticmagic.types.ValueDeserializationException if the field value
         * cannot be deserialized.
         */
        operator fun <V> get(field: BoundField<V, *>): List<V> {
            return this[field.getQualifiedFieldName()]
                .map(field.getFieldType()::deserialize)
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Fields) return false

            return fields == other.fields
        }

        override fun hashCode(): Int = fields.hashCode()
    }
}

data class CountResult(
    val count: Long,
)

data class UpdateByQueryPartialResult(
    val total: Long,
    val updated: Long,
    val created: Long,
    val deleted: Long,
    val batches: Int,
    val versionConflicts: Long,
    val noops: Long,
    val retries: BulkScrollRetries,
    val throttledMillis: Long,
    val requestsPerSecond: Float,
    val throttledUntilMillis: Long,
)

data class UpdateByQueryResult(
    val took: Long,
    val timedOut: Boolean,
    val total: Long,
    val updated: Long,
    val deleted: Long,
    val batches: Int,
    val versionConflicts: Long,
    val noops: Long,
    val retries: BulkScrollRetries,
    val throttledMillis: Long,
    val requestsPerSecond: Float,
    val throttledUntilMillis: Long,
    val failures: List<BulkScrollFailure>,
)

typealias DeleteByQueryPartialResult = UpdateByQueryPartialResult

data class DeleteByQueryResult(
    val took: Long,
    val timedOut: Boolean,
    val total: Long,
    val deleted: Long,
    val batches: Int,
    val versionConflicts: Long,
    val noops: Long,
    val retries: BulkScrollRetries,
    val throttledMillis: Long,
    val requestsPerSecond: Float,
    val throttledUntilMillis: Long,
    val failures: List<BulkScrollFailure>,
)

data class BulkScrollRetries(
    val bulk: Long,
    val search: Long,
)

data class BulkScrollFailure(
    val index: String,
    val type: String?,
    val id: String,
    val status: Int,
    val cause: BulkError,
)

data class AsyncResult<P, T>(
    val task: String,
    val createStatus: (Deserializer.ObjectCtx) -> P,
    val createResponse: (Deserializer.ObjectCtx) -> T,
) {
    suspend fun check(cluster: ElasticsearchCluster): TaskResult<P, T> {
        return cluster.checkAsyncResult(this)
    }

    suspend fun wait(cluster: ElasticsearchCluster): TaskResult<P, T & Any> {
        return cluster.waitAsyncResult(this)
    }
}

data class TaskResult<P, T>(
    val completed: Boolean,
    val task: TaskInfo<P>,
    val response: T,
)

data class TaskInfo<T>(
    val node: String,
    val id: Long,
    val type: String,
    val action: String,
    val status: T,
    val description: String,
    val startTimeInMillis: Long,
    val runningTimeInNanos: Long,
    val cancellable: Boolean
)

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
    ) : BulkItem(), IdActionMeta

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

data class PingResult(val statusCode: Int, val responseTimeMs: Long)

data class PersistResult(val shards: Shards) {
    companion object {
        operator fun invoke(rawResp: Deserializer.ObjectCtx): PersistResult {
            val rawShards = rawResp.obj("_shards")
            val shards = Shards(
                failed = rawShards.int("failed"),
                successful = rawShards.int("successful"),
                total = rawShards.int("total"),
            )
            return PersistResult(shards)
        }
    }
}

data class Shards(val failed: Int, val successful: Int, val total: Int)
