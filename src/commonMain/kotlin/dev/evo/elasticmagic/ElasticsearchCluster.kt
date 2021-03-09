package dev.evo.elasticmagic

interface ElasticsearchCluster {
    operator fun get(indexName: String): ElasticsearchIndex
}

interface ElasticsearchIndex {
    suspend fun <S: Source> search(searchQuery: BaseSearchQuery<S, *>): SearchQueryResult<S>
}

interface ElasticsearchSyncCluster {
    operator fun get(indexName: String): ElasticsearchSyncIndex
}

interface ElasticsearchSyncIndex {
    fun <S: Source> search(searchQuery: BaseSearchQuery<S, *>): SearchQueryResult<S>
}
