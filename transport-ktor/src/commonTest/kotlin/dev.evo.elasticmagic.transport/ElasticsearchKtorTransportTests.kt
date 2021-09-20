package dev.evo.elasticmagic.transport

// import io.ktor.client.engine.mock.MockEngine
// import io.ktor.client.engine.mock.respond
// import io.ktor.client.engine.mock.respondError
// import io.ktor.client.engine.mock.toByteArray
// import io.ktor.http.ContentType
// import io.ktor.http.headersOf
// import io.ktor.http.HttpHeaders
// import io.ktor.http.HttpMethod
// import io.ktor.http.HttpStatusCode
//
// import kotlinx.serialization.encodeToString
// import kotlinx.serialization.json.put
// import kotlinx.serialization.json.Json
// import kotlinx.serialization.json.JsonObject
// import kotlinx.serialization.json.addJsonObject
// import kotlinx.serialization.json.buildJsonObject
// import kotlinx.serialization.json.jsonObject
// import kotlinx.serialization.json.putJsonArray
// import kotlinx.serialization.json.putJsonObject
//
// import kotlin.test.Test
// import kotlin.test.assertEquals
// import kotlin.test.assertFailsWith
//
// @ExperimentalStdlibApi
// @io.ktor.util.KtorExperimentalAPI
// class ElasticsearchKtorTransportTests {
//     @Test
//     fun testGetJsonRequest() = runTest {
//         val client = ElasticsearchKtorTransport("http://example.com:9200", MockEngine { request ->
//             assertEquals(
//                 request.method,
//                 HttpMethod.Get
//             )
//             assertEquals(
//                 "/_alias",
//                 request.url.encodedPath
//             )
//             respond(
//                 """
//                 {
//                   "products_v1": {"aliases": {"products": {}}},
//                   "orders_v2": {"aliases": {"orders": {}, "shopping_carts": {}}}
//                 }
//                 """.trimIndent(),
//                 headers = headersOf(
//                     HttpHeaders.ContentType, ContentType.Application.Json.toString()
//                 )
//             )
//         })
//         val aliases = client.jsonRequest(Method.GET, "_alias")
//         assertEquals(setOf("products_v1", "orders_v2"), aliases.jsonObject.keys)
//         assertEquals(
//             JsonObject(emptyMap()),
//             aliases.jsonObject["products_v1"]!!
//                 .jsonObject["aliases"]!!
//                 .jsonObject["products"]!!
//                 .jsonObject
//         )
//     }
//
//     @Test
//     fun testPutJsonRequest() = runTest {
//         val client = ElasticsearchKtorTransport("http://example.com:9200", MockEngine { request ->
//             assertEquals(
//                 request.method,
//                 HttpMethod.Put
//             )
//             assertEquals(
//                 "/products/_settings",
//                 request.url.encodedPath
//             )
//             assertEquals(
//                 ContentType.Application.Json,
//                 request.body.contentType
//             )
//             assertEquals(
//                 """{"index":{"number_of_replicas":2}}""",
//                 request.body.toByteArray().decodeToString()
//             )
//             respond(
//                 """{"acknowledge": true}""",
//                 headers = headersOf(
//                     HttpHeaders.ContentType, ContentType.Application.Json.toString()
//                 )
//             )
//         })
//         val body = buildJsonObject {
//             putJsonObject("index") {
//                 put("number_of_replicas", 2)
//             }
//         }
//         val result = client.jsonRequest(Method.PUT, "products/_settings", body = body)
//         assertEquals(
//             buildJsonObject {
//                 put("acknowledge", true)
//             },
//             result,
//         )
//     }
//
//     @Test
//     fun testDeleteJsonRequest() = runTest {
//         val client = ElasticsearchKtorTransport("http://example.com:9200", MockEngine { request ->
//             assertEquals(
//                 request.method,
//                 HttpMethod.Delete
//             )
//             assertEquals(
//                 "/products_v2",
//                 request.url.encodedPath
//             )
//             assertEquals(
//                 "",
//                 request.body.toByteArray().decodeToString()
//             )
//             respond(
//                 """{"acknowledge": true}""",
//                 headers = headersOf(
//                     HttpHeaders.ContentType, ContentType.Application.Json.toString()
//                 )
//             )
//         })
//         val result = client.jsonRequest(Method.DELETE, "products_v2")
//         assertEquals(
//             buildJsonObject {
//                 put("acknowledge", true)
//             },
//             result
//         )
//     }
//
//     @Test
//     fun testRequestWithCustomContentType() = runTest {
//         val client = ElasticsearchKtorTransport("http://example.com:9200", MockEngine { request ->
//             assertEquals(
//                 request.method,
//                 HttpMethod.Post
//             )
//             assertEquals(
//                 "/_bulk",
//                 request.url.encodedPath
//             )
//             assertEquals(
//                 ContentType("application", "x-ndjson"),
//                 request.body.contentType
//             )
//             assertEquals(
//                 "{\"delete\":{\"_id\":\"123\",\"_index\":\"test\"}}\n",
//                 request.body.toByteArray().decodeToString()
//             )
//             val body = buildJsonObject {
//                 put("took", 7)
//                 put("errors", false)
//                 putJsonArray("items") {
//                     addJsonObject {
//                         putJsonObject("delete") {
//                             put("_index", "test")
//                             put("_type", "_doc")
//                             put("_id", "123")
//                         }
//                     }
//                 }
//             }
//
//             respond(
//                 Json.encodeToString(body)
//             )
//         })
//         val result = client.request(
//             Method.POST, "_bulk",
//             contentType = "application/x-ndjson",
//         ) {
//             append(
//                 """
//                     {"delete":{"_id":"123","_index":"test"}}
//
//                 """.trimIndent()
//             )
//         }
//         assertEquals(
//             """
//                 {"took":7,"errors":false,"items":[{"delete":{"_index":"test","_type":"_doc","_id":"123"}}]}
//             """.trimIndent(),
//             result,
//         )
//     }
//
//     @Test
//     fun testPostJsonRequestWithTimeout() = runTest {
//         val client = ElasticsearchKtorTransport("http://example.com:9200", MockEngine { request ->
//             assertEquals(
//                 request.method,
//                 HttpMethod.Post
//             )
//             assertEquals(
//                 "/products_v2/_forcemerge",
//                 request.url.encodedPath
//             )
//             assertEquals(
//                 "",
//                 request.body.toByteArray().decodeToString()
//             )
//             respondError(
//                 HttpStatusCode.GatewayTimeout
//             )
//         })
//         val ex = assertFailsWith(ElasticsearchException.GatewayTimeout::class) {
//             client.jsonRequest(
//                 Method.POST, "products_v2/_forcemerge", mapOf("max_num_segments" to listOf("1"))
//             )
//         }
//         assertEquals(504, ex.statusCode)
//         assertEquals(
//             "GatewayTimeout(504, \"Gateway Timeout\")",
//             ex.toString()
//         )
//     }
// }
