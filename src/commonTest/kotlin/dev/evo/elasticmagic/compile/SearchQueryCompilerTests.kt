package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.BoolNode
import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.Field
import dev.evo.elasticmagic.FieldFormat
import dev.evo.elasticmagic.FunctionScore
import dev.evo.elasticmagic.FunctionScoreNode
import dev.evo.elasticmagic.MultiMatch
import dev.evo.elasticmagic.NodeHandle
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.Script
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.Sort
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
    }
}

class SearchQueryCompilerTests {
    private val serializer = StdSerializer()
    private val compiler = SearchQueryCompiler(
        ElasticsearchVersion(6, 0, 0),
    )

    private fun compile(query: SearchQuery<*>): SearchQueryCompiler.Compiled<Map<String, Any?>> {
        return compiler.compile(serializer, query)
    }

    @Test
    fun testEmpty() {
        val compiled = compile(SearchQuery())
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

        val compiled = compile(query)
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

        val compiled = compile(query)
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

        val compiled = compile(query)
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
    fun testSort_fieldSimplified() {
        compile(
            SearchQuery()
                .sort(Sort(field = AnyField("popularity")))
        ).body!! shouldContainExactly mapOf(
            "sort" to listOf("popularity")
        )
    }

    @Test
    fun testSort_fieldOrder() {
        compile(
            SearchQuery()
                .sort(Sort(field = AnyField("popularity"), order = Sort.Order.DESC))
        ).body!! shouldContainExactly mapOf(
            "sort" to listOf(
                mapOf(
                    "popularity" to mapOf(
                        "order" to "desc",
                    )
                )
            )
        )
    }

    @Test
    fun testSort_fieldAllParams() {
        compile(
            SearchQuery()
                .sort(
                    Sort(
                        field = AnyField("popularity"),
                        order = Sort.Order.DESC,
                        mode = Sort.Mode.MEDIAN,
                        numericType = Sort.NumericType.LONG,
                        missing = Sort.Missing.Value(50),
                        unmappedType = "long",
                    )
                )
        ).body!! shouldContainExactly mapOf(
            "sort" to listOf(
                mapOf(
                    "popularity" to mapOf(
                        "order" to "desc",
                        "mode" to "median",
                        "numeric_type" to "long",
                        "missing" to 50,
                        "unmapped_type" to "long",
                    )
                )
            )
        )
    }

    @Test
    fun testSort_scriptWithOrder() {
        compile(
            SearchQuery()
                .sort(
                    Sort(
                        script = Script(
                            source = "doc[params.field].value",
                            params = mapOf(
                                "field" to AnyField("popularity"),
                            )
                        ),
                        scriptType = "number",
                        order = Sort.Order.DESC
                    )
                )
        ).body!! shouldContainExactly mapOf(
            "sort" to listOf(
                mapOf(
                    "_script" to mapOf(
                        "order" to "desc",
                        "type" to "number",
                        "script" to mapOf(
                            "source" to "doc[params.field].value",
                            "params" to mapOf(
                                "field" to "popularity",
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun testSizeAndFrom() {
        val query = SearchQuery()
        query
            .size(100)
            .from(200)

        val compiled = compile(query)
        compiled.body!! shouldContainExactly mapOf(
            "size" to 100L,
            "from" to 200L,
        )
    }

    @Test
    fun testFunctionScore_scriptScore() {
        val query = SearchQuery(
            FunctionScore(
                query = null,
                functions = listOf(
                    FunctionScore.ScriptScore(
                        Script(
                            source = "params.a / Math.pow(params.b, doc[params.field].value)",
                            params = Params(
                                "a" to 5,
                                "b" to 1.2,
                                "field" to AnyField("rank")
                            ),
                        )
                    )
                )
            )
        )

        val compiled = compile(query)
        compiled.body!! shouldContainExactly mapOf(
            "query" to mapOf(
                "function_score" to mapOf(
                    "functions" to listOf(
                        mapOf(
                            "script_score" to mapOf(
                                "script" to mapOf(
                                    "source" to "params.a / Math.pow(params.b, doc[params.field].value)",
                                    "params" to mapOf(
                                        "a" to 5,
                                        "b" to 1.2,
                                        "field" to "rank",
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun testDocvalueFields_simple() {
        val query = SearchQuery()
            .docvalueFields(AnyField("name"), AnyField("rank"))

        val compiled = compile(query)
        compiled.body!! shouldContainExactly mapOf(
            "docvalue_fields" to listOf("name", "rank")
        )
    }

    @Test
    fun testDocvalueFields_formatted() {
        val query = SearchQuery()
            .docvalueFields(FieldFormat(AnyField("date_created"), format = "epoch_millis"))

        val compiled = compile(query)
        compiled.body!! shouldContainExactly mapOf(
            "docvalue_fields" to listOf(
                mapOf(
                    "field" to "date_created",
                    "format" to "epoch_millis",
                )
            )
        )
    }

    @Test
    fun testStoredFields() {
        val query = SearchQuery()
            .storedFields(AnyField("name"), AnyField("date_modified"))

        val compiled = compile(query)
        compiled.body!! shouldContainExactly mapOf(
            "stored_fields" to listOf("name", "date_modified")
        )
    }

    @Test
    fun testScriptFields() {
        val query = SearchQuery()
            .scriptFields(
                "sort_price" to Script(
                    id = "price_sort",
                    lang = "painless",
                    params = Params(
                        "field" to AnyField("price"),
                        "factor" to 2.0
                    )
                )
            )

        val compiled = compile(query)
        compiled.body!! shouldContainExactly mapOf(
            "script_fields" to mapOf(
                "sort_price" to mapOf(
                    "script" to mapOf(
                        "lang" to "painless",
                        "id" to "price_sort",
                        "params" to mapOf(
                            "field" to "price",
                            "factor" to 2.0
                        )
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
        var compiled = compile(query)
        compiled.body!! shouldContainExactly emptyMap()

        query.queryNode(BOOL_HANDLE) { node ->
            node.should.add(
                AnyField("opinions_count").gt(4)
            )
        }
        compiled = compile(query)
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
        compiled = compile(query)
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
        compiled = compile(query)
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
