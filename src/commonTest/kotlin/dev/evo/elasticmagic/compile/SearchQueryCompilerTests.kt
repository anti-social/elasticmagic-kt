package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.MultiMatch
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SubDocument
import dev.evo.elasticmagic.serde.StdSerializer

import io.kotest.matchers.maps.shouldContainExactly

import kotlin.test.Test

class SearchQueryCompilerTests {
    private val compiler = SearchQueryCompiler(
        ElasticsearchVersion(6, 0, 0),
        StdSerializer(),
    )

    @Test
    fun testEmpty() {
        val res = compiler.compile(SearchQuery())
        res.body shouldContainExactly emptyMap()
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
        res.body shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf(
                            "term" to mapOf(
                                "status" to 0
                            )
                        ),
                        mapOf(
                            "range" to mapOf(
                                "rank" to mapOf(
                                    "gte" to 90.0
                                )
                            )
                        ),
                        mapOf(
                            "range" to mapOf(
                                "opinions_count" to mapOf(
                                    "gt" to 5
                                )
                            )
                        ),
                    )
                )
            )
        )
    }

    @Test
    fun testFilteredQuery() {
        class OpinionDoc : SubDocument() {
            val count by int()
        }

        class CompanyDoc : SubDocument() {
            val name by text()
            val opinion by obj(::OpinionDoc)
        }

        val productDoc = object : Document() {
            val name by text()
            val rank by float()
            val company by obj(::CompanyDoc)
        }

        val query = SearchQuery {
            functionScore(
                multiMatch(
                    "Test term",
                    listOf(productDoc.name, productDoc.company.name),
                    type = MultiMatch.Type.CROSS_FIELDS
                ),
                functions = listOf(
                    weight(2.0, productDoc.company.opinion.count.eq(5)),
                    fieldValueFactor(productDoc.rank, 5.0)
                )
            )
        }
        query.filter(productDoc.company.opinion.count.gt(4))

        val res = compiler.compile(query)
        res.body shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "must" to listOf(
                        mapOf(
                            "function_score" to mapOf(
                                "functions" to listOf(
                                    mapOf(
                                        "filter" to mapOf(
                                            "term" to mapOf(
                                                "company.opinion.count" to 5
                                            )
                                        ),
                                        "weight" to 2.0,
                                    ),
                                    mapOf(
                                        "field_value_factor" to mapOf(
                                            "field" to "rank",
                                            "factor" to 5.0,
                                        )
                                    ),
                                ),
                                "query" to mapOf(
                                    "multi_match" to mapOf(
                                        "query" to "Test term",
                                        "fields" to listOf("name", "company.name"),
                                        "type" to "cross_fields",
                                    )
                                ),
                            )
                        )
                    ),
                    "filter" to listOf(
                        mapOf(
                            "range" to mapOf(
                                "company.opinion.count" to mapOf(
                                    "gt" to 4
                                )
                            )
                        )
                    )
                )
            )
        )
    }
}