package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.kotlinx.JsonSerde
import dev.evo.elasticmagic.transport.ApiRequest
import dev.evo.elasticmagic.transport.CatRequest
import dev.evo.elasticmagic.transport.ElasticsearchException
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport
import dev.evo.elasticmagic.transport.Method
import dev.evo.elasticmagic.transport.TransportError

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

import kotlin.test.Test

class ElasticsearchTransportTests : TestBase() {
    val transport = ElasticsearchKtorTransport(
        elasticUrl,
        engine = httpEngine,
    ) {
        if (elasticAuth != null) {
            auth = elasticAuth
        }
    }

    @Test
    fun catRequest() = runTest {
        val nodes = transport.request(
            CatRequest(
                "nodes",
                parameters = mapOf(
                    "h" to listOf("name,node.role,ip,heap.percent,cpu,master"),
                ),
                errorSerde = JsonSerde
            )
        )
        nodes.size shouldBe 1
        nodes[0].size shouldBe 6
        nodes[0][nodes[0].size - 1] shouldBe "*"
    }

    @Test
    fun catRequestWithError() = runTest {
        val exception = shouldThrow<ElasticsearchException.BadRequest> {
            transport.request(
                CatRequest(
                    "nodes",
                    parameters = mapOf("unknown_parameter" to listOf("111")),
                    errorSerde = JsonSerde
                )
            )
        }
        val error = exception.error
        error.shouldBeInstanceOf<TransportError.Structured>()
        error.type shouldBe "illegal_argument_exception"
        error.reason shouldContain "unknown_parameter"
    }

    @Test
    fun compressedRequest() = runTest {
        val transport = ElasticsearchKtorTransport(
            elasticUrl,
            engine = httpEngine,
        ) {
            gzipRequests = true
            if (elasticAuth != null) {
                auth = elasticAuth
            }
        }
        val result = transport.request(
            ApiRequest(
                Method.POST,
                "_search",
                parameters = mapOf("terminate_after" to listOf("1")),
                body = JsonSerde.serializer.obj {
                    obj("query") {
                        obj("match_all") {}
                    }
                },
                serde = JsonSerde
            )
        )
        result.long("took").shouldNotBeNull()
    }
}
