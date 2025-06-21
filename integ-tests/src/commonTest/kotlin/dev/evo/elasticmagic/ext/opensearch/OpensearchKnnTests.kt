package dev.evo.elasticmagic.ext.opensearch

import dev.evo.elasticmagic.ElasticsearchTestBase
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.Version
import dev.evo.elasticmagic.bulk.withActionMeta
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.DocSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlin.test.Test

object HotelDoc : Document() {
    val location by Field(
        KnnVector(dimension = 2, spaceType = KnnSpace.L2),
    )
}

class HotelDocSource : DocSource() {
    var location by HotelDoc.location.required()
}

class OpensearchKnnTests : ElasticsearchTestBase() {
    override val indexName = "opensearch-knn"

    private val docSources = listOf(
        HotelDocSource().apply {
            location = listOf(5.2F, 4.4F)
        }.withActionMeta(id = "1"),
        HotelDocSource().apply {
            location = listOf(5.2F, 3.9F)
        }.withActionMeta(id = "2"),
        HotelDocSource().apply {
            location = listOf(4.9F, 3.4F)
        }.withActionMeta(id = "3"),
        HotelDocSource().apply {
            location = listOf(4.2F, 4.6F)
        }.withActionMeta(id = "4"),
        HotelDocSource().apply {
            location = listOf(3.3F, 4.5F)
        }.withActionMeta(id = "5"),
    )

    @Test
    fun knnQuery() = runTestWithSerdes {
        val clusterVersion = cluster.getVersion()
        if (clusterVersion !is Version.Opensearch) {
            return@runTestWithSerdes
        }

        withFixtures(HotelDoc, docSources, forcemerge = true) {
            val searchResult = SearchQuery(
                ::HotelDocSource,
                Knn(HotelDoc.location, vector = listOf(5F, 4F), k = 3)
            ).search(index)
            searchResult.totalHits shouldBe 3
            val hits = searchResult.hits
            hits.map { h -> h.id } shouldBe listOf("2", "1", "3")
            hits[0].source.shouldNotBeNull().location shouldBe listOf(5.2F, 3.9F)
            hits[1].source.shouldNotBeNull().location shouldBe listOf(5.2F, 4.4F)
            hits[2].source.shouldNotBeNull().location shouldBe listOf(4.9F, 3.4F)
        }
    }
}
