package dev.evo.elasticmagic

import io.kotest.matchers.shouldBe
import kotlin.test.Test


class PingTests : ElasticsearchTestBase() {
    override val indexName = "ping"

    @Test
    fun successfulPing() = runTestWithTransports {
        val ping = this.cluster.ping()
        ping.statusCode shouldBe HTTP_OK_REQUEST
    }
}
