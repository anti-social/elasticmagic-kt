package dev.evo.elasticmagic.json.compile

import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.json.JsonSerializer

import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.serializer
import kotlin.reflect.typeOf

import kotlin.test.Test

class SearchQueryCompilerJsonTests {
    private val serializer = JsonSerializer()
    private val compiler = SearchQueryCompiler(serializer)

    // @Test
    // fun test() {
    //     val data = mapOf("1" to 2, "2" to 2)
    //     val js = Json.encodeToString(data)
    //     // val js = Json.encodeToString(serializer<Map<String, Any>>(), mapOf("1" to 2))
    //     println(js)
    //     // Json.decodeFromString(js)
    //     //     .also(::println)
    //     0 shouldBe 1
    // }

    @Test
    fun testEmpty() {
        val compiled = compiler.compile(SearchQuery())
        compiled.body shouldBe buildJsonObject {}
    }

    @Test
    fun testComposeFilters() {
        val userDoc = object : Document() {
            val status by int()
            val rank by float()
            val opinionsCount by int("opinions_count")
        }

        val query = SearchQuery()
            .filter(userDoc.status.eq(0))
            .filter(userDoc.rank.gte(90.0))
            .filter(userDoc.opinionsCount.gt(5))

        val res = compiler.compile(query)
        res.body shouldBe buildJsonObject {
            putJsonObject("query") {
                putJsonObject("bool") {
                    putJsonArray("filter") {
                        addJsonObject {
                            putJsonObject("term") {
                                put("status", 0)
                            }
                        }
                        addJsonObject {
                            putJsonObject("range") {
                                putJsonObject("rank") {
                                    put("gte", 90.0)
                                }
                            }
                        }
                        addJsonObject {
                            putJsonObject("range") {
                                putJsonObject("opinions_count") {
                                    put("gt", 5)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}