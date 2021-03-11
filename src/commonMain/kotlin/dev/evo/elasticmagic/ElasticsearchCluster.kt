package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.CompilerProvider
import dev.evo.elasticmagic.serde.toMap
import dev.evo.elasticmagic.transport.ElasticsearchTransport
import dev.evo.elasticmagic.transport.Method

class ElasticsearchCluster<OBJ>(
    private val esTransport: ElasticsearchTransport<OBJ>,
    private val compilerProvider: CompilerProvider<OBJ>,
) {
    operator fun get(indexName: String): ElasticsearchIndex<OBJ> {
        return ElasticsearchIndex(indexName, esTransport, compilerProvider)
    }
}

class ElasticsearchIndex<OBJ>(
    val indexName: String,
    private val esTransport: ElasticsearchTransport<OBJ>,
    private val compilerProvider: CompilerProvider<OBJ>,
) {
    suspend fun <S : Source> search(
        searchQuery: BaseSearchQuery<S, *>
    ): SearchQueryResult<OBJ, S> {
        val preparedSearchQuery = searchQuery.prepare()
        val compiled = compilerProvider.searchQuery.compile(searchQuery)
        val result = esTransport.objRequest(
            Method.GET, "$indexName/_doc/_search", body = compiled.body
        )
        val rawResult = compilerProvider.deserializer.obj(result)
        val rawHitsData = rawResult.obj("hits")
        val rawTotal = rawHitsData.objOrNull("total")
        val (totalHits, totalHitsRelation) = if (rawTotal != null) {
            rawTotal.long("value") to rawTotal.string("relation")
        } else {
            rawHitsData.long("total") to null
        }
        val hits = mutableListOf<SearchHit<S>>()
        val rawHits = rawHitsData.arrayOrNull("hits")
        if (rawHits != null) {
            while (rawHits.hasNext()) {
                val rawHit = rawHits.obj()
                val source = rawHit.objOrNull("_source")?.let { rawSource ->
                    val source = preparedSearchQuery.sourceFactory()
                    for ((fieldName, fieldValue) in rawSource.toMap()) {
                        source.setField(fieldName, fieldValue)
                    }
                    source
                }
                hits.add(
                    SearchHit(
                        index = rawHit.string("_index"),
                        type = rawHit.stringOrNull("_type") ?: "_doc",
                        id = rawHit.string("_id"),
                        score = rawHit.doubleOrNull("_score"),
                        source = source,
                    )
                )
            }
        }
        return SearchQueryResult(
            // TODO: Flag to add raw result
            null,
            took = rawResult.long("took"),
            timedOut = rawResult.boolean("timed_out"),
            totalHits = totalHits,
            totalHitsRelation = totalHitsRelation,
            maxScore = rawHitsData.doubleOrNull("max_score"),
            hits = hits,
        )
    }
}

interface ElasticsearchSyncCluster<R> {
    operator fun get(indexName: String): ElasticsearchSyncIndex<R>
}

interface ElasticsearchSyncIndex<R> {
    fun <S: Source> search(searchQuery: BaseSearchQuery<S, *>): SearchQueryResult<R, S>
}
