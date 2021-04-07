package dev.evo.elasticmagic

data class SearchQueryResult<S: BaseSource>(
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

data class SearchHit<S: BaseSource>(
    val index: String,
    val type: String,
    val id: String,
    val score: Double?,
    val source: S?,
)

data class CreateIndexResult(
    val acknowledged: Boolean,
    val shardsAcknowledged: Boolean,
    val index: String,
)

data class DeleteIndexResult(
    val acknowledged: Boolean,
)
