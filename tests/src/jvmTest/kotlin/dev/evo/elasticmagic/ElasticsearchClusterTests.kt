package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.CompilerProvider
import dev.evo.elasticmagic.serde.serialization.JsonSerde
import dev.evo.elasticmagic.transport.ElasticsearchKtorTransport

import io.kotest.matchers.shouldBe

import io.ktor.client.engine.cio.CIO

import kotlinx.coroutines.runBlocking

import kotlin.test.Test

class ElasticsearchClusterTests {
    object FactorsDoc : Document() {
        val partition by float()
        val companyId by keyword("company_id")
        val clickPrice by float("click_price")
    }

    class FactorsSource : Source() {
        val partition by FactorsDoc.partition
        val companyId by FactorsDoc.companyId.required()
        val clickPrice by FactorsDoc.clickPrice
    }

    object ProductDoc : Document() {
        val rank by float()
    }

    private val esTransport = ElasticsearchKtorTransport(
        // "http://es6-stg-prom-lb.prom.dev-cloud.evo.:9200",
        "http://localhost:9200",
        deserializer = JsonSerde.deserializer,
        engine = CIO.create {}
    )
    private val compilers = CompilerProvider(
        ElasticsearchVersion(6, 0, 0),
    )
    private val cluster = ElasticsearchCluster(esTransport, compilers, JsonSerde)

    @Test
    fun test() = runBlocking {
        // val index = cluster["ua_trunk_catalog"]
        val index = cluster["adv_ua_weight_factors"]

        val query = SearchQuery(::FactorsSource) {
            functionScore(
                query = null,
                functions = listOf(
                    fieldValueFactor(
                        FactorsDoc.clickPrice,
                        missing = 0.0
                    )
                )
            )
        }
            .filter(FactorsDoc.clickPrice.gt(2.2))
            .rescore(
                QueryRescore(
                    FactorsDoc.companyId.eq(222),
                    rescoreQueryWeight = 10.0,
                    windowSize = 1000,
                )
            )
            .aggs(
                "qf" to FilterAgg(
                    filter = MatchAll(),
                    aggs = mapOf(
                        "partitions" to TermsAgg(FactorsDoc.partition, size = 100),
                        "price" to RangeAgg.simpleRanges(
                            FactorsDoc.clickPrice,
                            ranges = listOf(
                                null to 1.0,
                                1.0 to 10.0,
                                10.0 to null,
                            ),
                            aggs = mapOf(
                                "avg" to AvgAgg(FactorsDoc.clickPrice)
                            )
                        )
                    )
                )
            )
        println(compilers.searchQuery.compile(JsonSerde.serializer, query).body)

        val searchResult = index.search(query)
        println(searchResult)

        1 shouldBe 2
    }

    // @Test
    // fun testError() = runBlocking {
    //     val index = cluster["ua_trunk_catalog"]
    //
    //     val query = SearchQuery {
    //         functionScore(
    //             query = null,
    //             functions = listOf(
    //                 fieldValueFactor(
    //                     ProductDoc.rank,
    //                 )
    //             )
    //         )
    //     }
    //     println(compilers.searchQuery.compile(JsonSerde.serializer, query).body)
    //
    //     val searchResult = index.search(query)
    //     println(searchResult)
    //
    //     1 shouldBe 2
    // }
}