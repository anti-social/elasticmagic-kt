package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.BoolNode
import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.Field
import dev.evo.elasticmagic.FunctionScoreNode
import dev.evo.elasticmagic.MultiMatch
import dev.evo.elasticmagic.NodeHandle
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SubDocument
import dev.evo.elasticmagic.Type
import dev.evo.elasticmagic.serde.StdSerializer

import io.kotest.matchers.maps.shouldContainExactly

import kotlin.test.Test

class AnyField(name: String) : Field<Nothing>(
    name,
    object : Type<Nothing> {
        override val name: String
            get() = TODO("not implemented")
        override fun deserialize(v: Any): Nothing {
            TODO("not implemented")
        }
    },
    Params()
) {
    init {
        _setFieldName(name)
        // _bindToParent()
    }
}

class SearchQueryCompilerTests {
    private val serializer = StdSerializer()
    private val compiler = SearchQueryCompiler(
        ElasticsearchVersion(6, 0, 0),
    )

    @Test
    fun testEmpty() {
        val compiled = compiler.compile(serializer, SearchQuery())
        compiled.body!! shouldContainExactly emptyMap()
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

        val compiled = compiler.compile(serializer, query)
        compiled.body!! shouldContainExactly mapOf(
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

        val compiled = compiler.compile(serializer, query)
        compiled.body!! shouldContainExactly mapOf(
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

    @Test
    fun testPostFilters() {
        val query = SearchQuery()
        query.postFilter(AnyField("status").eq(0))

        val compiled = compiler.compile(serializer, query)
        compiled.body!! shouldContainExactly mapOf(
            "post_filter" to listOf(
                mapOf(
                    "term" to mapOf(
                        "status" to 0
                    )
                )
            )
        )
    }

    @Test
    fun testNodes() {
        val BOOL_HANDLE = NodeHandle<BoolNode>("bool")
        val AD_BOOST_HANDLE = NodeHandle<FunctionScoreNode>("ad_boost")

        val query = SearchQuery(
            BoolNode(
                BOOL_HANDLE,
                should = listOf(
                    FunctionScoreNode(
                        AD_BOOST_HANDLE,
                        null
                    )
                )
            )
        )
        var compiled = compiler.compile(serializer, query)
        compiled.body!! shouldContainExactly emptyMap()

        query.queryNode(BOOL_HANDLE) { node ->
            node.should.add(
                AnyField("opinions_count").gt(4)
            )
        }
        compiled = compiler.compile(serializer, query)
        compiled.body!! shouldContainExactly mapOf(
            "query" to mapOf(
                "range" to mapOf(
                    "opinions_count" to mapOf("gt" to 4)
                )
            )
        )

        query.queryNode(BOOL_HANDLE) { node ->
            node.should.add(
                AnyField("opinions_positive_percent").gt(90.0)
            )
        }
        compiled = compiler.compile(serializer, query)
        compiled.body!! shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "should" to listOf(
                        mapOf(
                            "range" to mapOf(
                                "opinions_count" to mapOf("gt" to 4)
                            )
                        ),
                        mapOf(
                            "range" to mapOf(
                                "opinions_positive_percent" to mapOf("gt" to 90.0)
                            )
                        ),
                    )
                )
            )
        )

        query.queryNode(AD_BOOST_HANDLE) { node ->
            node.functions.add(
                weight(
                    1.5,
                    filter = AnyField("name").match("test")
                )
            )
        }
        compiled = compiler.compile(serializer, query)
        compiled.body!! shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "should" to listOf(
                        mapOf(
                            "function_score" to mapOf(
                                "functions" to listOf(
                                    mapOf(
                                        "weight" to 1.5,
                                        "filter" to mapOf(
                                            "match" to mapOf(
                                                "name" to "test"
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                        mapOf(
                            "range" to mapOf(
                                "opinions_count" to mapOf("gt" to 4)
                            )
                        ),
                        mapOf(
                            "range" to mapOf(
                                "opinions_positive_percent" to mapOf("gt" to 90.0)
                            )
                        ),
                    )
                )
            )
        )

    }
}
