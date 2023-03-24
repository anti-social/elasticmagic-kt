package dev.evo.elasticmagic

import io.kotest.matchers.shouldBe
import kotlin.test.Test


class PingTests : ElasticsearchTestBase() {
    override val indexName = "ping"

    @Test
    fun successfulPing() = runTestWithSerdes {
        val ping = this.cluster.ping()
        ping.statusCode shouldBe 200
    }
}
