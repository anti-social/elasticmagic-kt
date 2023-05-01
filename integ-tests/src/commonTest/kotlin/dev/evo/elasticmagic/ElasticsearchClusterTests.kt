package dev.evo.elasticmagic

import io.kotest.matchers.booleans.shouldBeFalse

import kotlin.test.Test

class ElasticsearchClusterTests : ElasticsearchTestBase() {
    override val indexName = "elasticsearch-cluster"

    @Test
    fun checkMissingIndex() = runTestWithSerdes {
        cluster.indexExists("unknown-index").shouldBeFalse()
    }
}
