package dev.evo.elasticmagic.transport

import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.serialization.JsonDeserializer
import dev.evo.elasticmagic.serde.serialization.JsonSerde
import dev.evo.elasticmagic.serde.serialization.JsonSerializer
import dev.evo.elasticmagic.serde.toMap

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

import kotlin.test.Test

@ExperimentalStdlibApi
class ElasticsearchKtorTransportTests {
    @Test
    fun headRequest() = runTest {
        val client = ElasticsearchKtorTransport(
            "http://example.com:9200",
            MockEngine { request ->
                request.method shouldBe HttpMethod.Head
                request.url.encodedPath shouldBe "/products"
                request.body.contentType.shouldBeNull()
                request.body.toByteArray().decodeToString().shouldBeEmpty()

                respond(
                    "",
                    headers = headersOf(
                        HttpHeaders.ContentType, ContentType.Application.Json.toString()
                    )
                )
            }
        )
        val result = client.request(
            ApiRequest(
                Method.HEAD,
                "products",
                serde = JsonSerde,
                processResponse = Deserializer.ObjectCtx::toMap
            )
        )
        result shouldContainExactly emptyMap()
    }

    @Test
    fun putRequest() = runTest {
        val client = ElasticsearchKtorTransport(
            "http://example.com:9200",
            MockEngine { request ->
                request.method shouldBe HttpMethod.Put
                request.url.encodedPath shouldBe "/products/_settings"
                request.body.contentType shouldBe ContentType.Application.Json
                request.body.toByteArray().decodeToString() shouldBe """{"index":{"number_of_replicas":2}}"""

                respond(
                    """{"acknowledge": true}""",
                    headers = headersOf(
                        HttpHeaders.ContentType, ContentType.Application.Json.toString()
                    )
                )
            }
        )
        val body = JsonSerializer.obj {
            obj("index") {
                field("number_of_replicas", 2.toInt())
            }
        }
        val result = client.request(
            ApiRequest(
                Method.PUT,
                "products/_settings",
                parameters = emptyMap(),
                body = body,
                serde = JsonSerde,
                Deserializer.ObjectCtx::toMap
            )
        )
        result shouldContainExactly mapOf(
            "acknowledge" to true
        )
    }

     @Test
     fun deleteRequest() = runTest {
         val client = ElasticsearchKtorTransport(
             "http://example.com:9200",
             MockEngine { request ->
                 request.method shouldBe HttpMethod.Delete
                 request.url.encodedPath shouldBe "/products_v2"
                 request.body.toByteArray().decodeToString().shouldBeEmpty()

                 respond(
                     """{"acknowledge": true}""",
                     headers = headersOf(
                         HttpHeaders.ContentType, ContentType.Application.Json.toString()
                     )
                 )
             }
         )
         val result = client.request(
             ApiRequest(
                 Method.DELETE,
                 "products_v2",
                 serde = JsonSerde,
                 processResponse = Deserializer.ObjectCtx::toMap
             )
         )
         result shouldContainExactly mapOf(
             "acknowledge" to true
         )
     }

     @Test
     fun bulkRequest() = runTest {
         val client = ElasticsearchKtorTransport(
             "http://example.com:9200",
             MockEngine { request ->
                 request.method shouldBe HttpMethod.Post
                 request.url.encodedPath shouldBe "/_bulk"
                 request.body.contentType shouldBe ContentType("application", "x-ndjson")
                 val rawBody = request.body.toByteArray().decodeToString()
                 JsonDeserializer.objFromStringOrNull(rawBody)
                     .shouldNotBeNull().toMap() shouldContainExactly mapOf(
                         "delete" to mapOf(
                             "_id" to "123",
                             "_index" to "test",
                         )
                     )
                 val respBody = buildJsonObject {
                     put("took", 7)
                     put("errors", false)
                     putJsonArray("items") {
                         addJsonObject {
                             putJsonObject("delete") {
                                 put("_index", "test")
                                 put("_type", "_doc")
                                 put("_id", "123")
                             }
                         }
                     }
                 }

                 respond(
                     Json.encodeToString(respBody)
                 )
             }
         )
         val body = JsonSerializer.obj {
            obj("delete") {
                field("_id", "123")
                field("_index", "test")
            }
        }
         val result = client.request(
             BulkRequest(
                 Method.POST, "_bulk",
                 body = listOf(body),
                 serde = JsonSerde,
                 processResponse = Deserializer.ObjectCtx::toMap
             )
         )
         result shouldContainExactly mapOf(
             "took" to 7L,
             "errors" to false,
             "items" to listOf(
                 mapOf(
                     "delete" to mapOf(
                         "_index" to "test",
                         "_type" to "_doc",
                         "_id" to "123",
                     )
                 )
             )
         )
     }

    @Test
    fun catRequest() = runTest {
        val client = ElasticsearchKtorTransport(
            "http://example.com:9200",
            MockEngine { request ->
                request.method shouldBe HttpMethod.Get
                request.url.encodedPath shouldBe "/_cat/nodes"
                request.body.contentType.shouldBeNull()
                respond(
                    """
                        192.168.163.48  58 99 51 6.59 5.83 5.62 cdfhimrstw - es-01
                        192.168.163.47  45 99 44 4.64 5.02 5.24 cdfhimrstw - es-02
                        192.168.163.156 48 99 57 5.25 5.97 6.47 cdfhimrstw * es-03
                    """.trimIndent()
                )
            }
        )
        val result = client.request(
            CatRequest("nodes", errorSerde = JsonSerde)
        )
        result shouldBe listOf(
            listOf("192.168.163.48", "58", "99", "51", "6.59", "5.83", "5.62", "cdfhimrstw", "-", "es-01"),
            listOf("192.168.163.47", "45", "99", "44", "4.64", "5.02", "5.24", "cdfhimrstw", "-", "es-02"),
            listOf("192.168.163.156", "48", "99", "57", "5.25", "5.97", "6.47", "cdfhimrstw", "*", "es-03"),
        )
    }

    @Test
    fun requestWithTimeout() = runTest {
        val client = ElasticsearchKtorTransport(
            "http://example.com:9200",
            MockEngine { request ->
                request.method shouldBe HttpMethod.Post
                request.url.encodedPath shouldBe "/products_v2/_forcemerge"
                request.body.toByteArray().decodeToString().shouldBeEmpty()
                respondError(
                    HttpStatusCode.GatewayTimeout
                )
            }
        )
        val ex = shouldThrow<ElasticsearchException.GatewayTimeout> {
            client.request(
                ApiRequest(
                    Method.POST,
                    "products_v2/_forcemerge",
                    parameters = mapOf("max_num_segments" to listOf("1")),
                    serde = JsonSerde,
                )
            )
        }
        ex.statusCode shouldBe 504
        ex.toString() shouldBe "GatewayTimeout(504, \"Gateway Timeout\")"
    }
}
