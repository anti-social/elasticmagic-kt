package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.serialization.JsonSerde
import dev.evo.elasticmagic.transport.CatRequest
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

import io.kotest.matchers.shouldBe

import kotlin.test.Test

class ElasticsearchTransportTests : TestBase() {
    val transport = ElasticsearchKtorTransport(
        "http://localhost:9200",
        serde = JsonSerde,
        engine = httpEngine,
    ) {
        if (elasticAuth != null) {
            auth = elasticAuth
        }
    }

    @Test
    fun catRequest() = runTest {
        val nodes = transport.request(CatRequest("nodes", ))
        nodes.size shouldBe 1
        nodes[0].size shouldBe 10
        nodes[0][nodes[0].size - 2] shouldBe "*"
    }
}
