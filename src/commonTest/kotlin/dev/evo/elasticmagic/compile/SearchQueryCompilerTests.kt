package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.FieldFormat
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchType
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.doc.BoundField
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.FieldType
import dev.evo.elasticmagic.doc.RootFieldSet
import dev.evo.elasticmagic.doc.SubDocument
import dev.evo.elasticmagic.doc.datetime
import dev.evo.elasticmagic.doc.serErr
import dev.evo.elasticmagic.query.BoolNode
import dev.evo.elasticmagic.query.DisMax
import dev.evo.elasticmagic.query.DisMaxNode
import dev.evo.elasticmagic.query.FunctionScore
import dev.evo.elasticmagic.query.FunctionScoreNode
import dev.evo.elasticmagic.query.Ids
import dev.evo.elasticmagic.query.MultiMatch
import dev.evo.elasticmagic.query.NodeHandle
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.query.Sort
import dev.evo.elasticmagic.query.QueryRescore
import dev.evo.elasticmagic.serde.StdSerializer

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.datetime.LocalDateTime

import kotlin.test.Test

class AnyField(name: String) : BoundField<Any, Any>(
    name,
    object : FieldType<Any, Any> {
        override val name: String
            get() = throw IllegalStateException("Fake field type cannot be used in mapping")

        override fun deserialize(v: Any, valueFactory: (() -> Any)?): Any {
            return v
        }

        override fun serializeTerm(v: Any): Any = v

        override fun deserializeTerm(v: Any): Any = deserialize(v)
    },
    Params(),
    RootFieldSet
)

class SearchQueryCompilerTests {
    private val serializer = object : StdSerializer() {
        override fun objToString(obj: Map<String, Any?>): String {
            TODO("not implemented")
        }
    }
    private val compiler = SearchQueryCompiler(
        ElasticsearchVersion(6, 0, 0),
    )

    private fun compile(query: SearchQuery<*>): CompiledSearchQuery {
        val compiled = compiler.compile(serializer, query.usingIndex("test"))
        return CompiledSearchQuery(
            params = compiled.parameters,
            body = compiled.body.shouldNotBeNull(),
        )
    }

    class CompiledSearchQuery(
        val params: Params,
        val body: Map<String, Any?>,
    )

    @Test
    fun fieldTypeSerialization() {
        val userDoc = object : Document() {
            val lastLoggedAt by datetime()
        }

        val compiled = compile(
            SearchQuery(userDoc.lastLoggedAt.gt(LocalDateTime(2020, 12, 31, 23, 0)))
        )
        compiled.body shouldContainExactly mapOf(
            "query" to mapOf(
                "range" to mapOf(
                    "lastLoggedAt" to mapOf(
                        "gt" to "2020-12-31T23:00:00Z"
                    )
                )
            )
        )
    }

    @Test
    fun testEmpty() {
        val compiled = compile(SearchQuery())
        compiled.body shouldContainExactly emptyMap()
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
            .filter(userDoc.rank.gte(90.0F))
            .filter(userDoc.opinionsCount.gt(5))

        val compiled = compile(query)
        compiled.body shouldContainExactly mapOf(
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
                                    "gte" to 90.0F
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
        class OpinionDoc(field: BoundField<BaseDocSource, Nothing>) : SubDocument(field) {
            val count by int()
        }

        class CompanyDoc(field: BoundField<BaseDocSource, Nothing>) : SubDocument(field) {
            val name by text()
            val opinion by obj(::OpinionDoc)
        }

        val productDoc = object : Document() {
            val name by text()
            val rank by float()
            val company by obj(::CompanyDoc)
        }

        val query = SearchQuery(
            FunctionScore(
                MultiMatch(
                    "Test term",
                    listOf(productDoc.name, productDoc.company.name),
                    type = MultiMatch.Type.CROSS_FIELDS
                ),
                functions = listOf(
                    FunctionScore.RandomScore(10, productDoc.runtime.seqNo),
                    FunctionScore.Weight(2.0, productDoc.company.opinion.count.eq(5)),
                    FunctionScore.FieldValueFactor(productDoc.rank, 5.0),
                )
            )
        )
        query.filter(productDoc.company.opinion.count.gt(4))

        val compiled = compile(query)
        compiled.body shouldContainExactly mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "must" to listOf(
                        mapOf(
                            "function_score" to mapOf(
                                "functions" to listOf(
                                    mapOf(
                                        "random_score" to mapOf(
                                            "seed" to 10,
                                            "field" to "_seq_no",
                                        ),
                                    ),
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
        compiled.body shouldContainExactly mapOf(
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
    fun testRescore() {
        val query = SearchQuery()

        query.rescore(
            QueryRescore(
                AnyField("rank").gte(4),
                windowSize = 100,
            )
        )
        compile(query).body shouldContainExactly mapOf(
            "rescore" to listOf(
                mapOf(
                    "window_size" to 100,
                    "query" to mapOf(
                        "rescore_query" to mapOf(
                            "range" to mapOf(
                                "rank" to mapOf(
                                    "gte" to 4
                                )
                            )
                        ),
                    ),
                )
            )
        )

        query.rescore(
            QueryRescore(
                FunctionScore(
                    functions = listOf(
                        FunctionScore.ScriptScore(
                            script = Script(
                                source = "Math.log10(doc[params.field].value + 2)",
                                params = mapOf(
                                    "field" to AnyField("likes")
                                )
                            )
                        )
                    )
                ),
                scoreMode = QueryRescore.ScoreMode.MULTIPLY,
            )
        )
        compile(query).body shouldContainExactly mapOf(
            "rescore" to listOf(
                mapOf(
                    "window_size" to 100,
                    "query" to mapOf(
                        "rescore_query" to mapOf(
                            "range" to mapOf(
                                "rank" to mapOf(
                                    "gte" to 4
                                )
                            )
                        ),
                    ),
                ),
                mapOf(
                    "query" to mapOf(
                        "score_mode" to "multiply",
                        "rescore_query" to mapOf(
                            "function_score" to mapOf(
                                "functions" to listOf(
                                    mapOf(
                                        "script_score" to mapOf(
                                            "script" to mapOf(
                                                "source" to "Math.log10(doc[params.field].value + 2)",
                                                "params" to mapOf(
                                                    "field" to "likes"
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        query.clearRescore()
        compile(query).body shouldContainExactly emptyMap()
    }

    @Test
    fun testSort_fieldSimplified() {
        compile(
            SearchQuery()
                .sort(Sort(field = AnyField("popularity")))
        ).body shouldContainExactly mapOf(
            "sort" to listOf("popularity")
        )
    }

    @Test
    fun testSort_fieldOrder() {
        compile(
            SearchQuery()
                .sort(Sort(field = AnyField("popularity"), order = Sort.Order.DESC))
        ).body shouldContainExactly mapOf(
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
        ).body shouldContainExactly mapOf(
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
        ).body shouldContainExactly mapOf(
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
    fun testTrackScores() {
        compile(SearchQuery().trackScores(true)).body shouldContainExactly mapOf(
            "track_scores" to true
        )
    }

    @Test
    fun testTrackTotalHits() {
        compile(SearchQuery().trackScores(true)).body shouldContainExactly mapOf(
            "track_scores" to true
        )
    }

    @Test
    fun testSizeAndFrom() {
        val query = SearchQuery()
            .size(100)
            .from(200)

        val compiled = compile(query)
        compiled.body shouldContainExactly mapOf(
            "size" to 100L,
            "from" to 200L,
        )
    }

    @Test
    fun testTerminateAfter() {
        val query = SearchQuery().terminateAfter(10_000)

        val compiled = compile(query)
        compiled.body shouldContainExactly mapOf(
            "terminate_after" to 10_000L,
        )
    }

    @Test
    fun testSearchParams() {
        val query = SearchQuery(params = Params("routing" to "111"))

        compile(query).params shouldContainExactly mapOf(
            "routing" to listOf("111"),
        )

        query
            .searchType(SearchType.DFS_QUERY_THEN_FETCH)
            .routing(1234)
            .requestCache(true)

        compile(query).params shouldContainExactly mapOf(
            "search_type" to listOf("dfs_query_then_fetch"),
            "routing" to listOf("1234"),
            "request_cache" to listOf("true"),
        )

        query.searchParams(
            "search_type" to SearchType.QUERY_THEN_FETCH,
            "routing" to null,
        )

        compile(query).params shouldContainExactly mapOf(
            "search_type" to listOf("query_then_fetch"),
            "request_cache" to listOf("true"),
        )
    }

    @Test
    fun testTerms() {
        val query = SearchQuery(
            AnyField("tags").contains(listOf(1, 9))
        )
        compile(query).body shouldContainExactly mapOf(
            "query" to mapOf(
                "terms" to mapOf(
                    "tags" to listOf(1, 9)
                )
            )
        )
    }

    @Test
    fun testIds() {
        val query = SearchQuery(
            Ids(listOf(
                "order~3",
                "order~2",
                "order~1",
            ))
        )
        compile(query).body shouldContainExactly mapOf(
            "query" to mapOf(
                "ids" to mapOf(
                    "values" to listOf("order~3", "order~2", "order~1")
                )
            )
        )
    }

    @Test
    fun testDisMax() {
        val query = SearchQuery(
            DisMax(
                listOf(
                    AnyField("name.en").match("Good morning"),
                    AnyField("name.es").match("Buenos días"),
                    AnyField("name.de").match("Guten Morgen"),
                ),
                tieBreaker = 0.5
            )
        )
        compile(query).body shouldContainExactly mapOf(
            "query" to mapOf(
                "dis_max" to mapOf(
                    "queries" to listOf(
                        mapOf(
                            "match" to mapOf(
                                "name.en" to "Good morning"
                            )
                        ),
                        mapOf(
                            "match" to mapOf(
                                "name.es" to "Buenos días"
                            )
                        ),
                        mapOf(
                            "match" to mapOf(
                                "name.de" to "Guten Morgen"
                            )
                        ),
                    ),
                    "tie_breaker" to 0.5
                )
            )
        )
    }

    @Test
    fun testDisMaxNode() {
        val LANG_HANDLE = NodeHandle<DisMaxNode>()
        val query = SearchQuery(
            DisMaxNode(LANG_HANDLE)
        )
        compile(query).body shouldContainExactly emptyMap()

        query.queryNode(LANG_HANDLE) { node ->
            node.queries.add(
                AnyField("name.en").match("Good morning"),
            )
        }
        compile(query).body shouldContainExactly mapOf(
            "query" to mapOf(
                "match" to mapOf(
                    "name.en" to "Good morning"
                )
            )
        )

        query.queryNode(LANG_HANDLE) { node ->
            node.tieBreaker = 0.7
            node.queries.add(
                AnyField("name.de").match("Guten Morgen"),
            )
        }
        compile(query).body shouldContainExactly mapOf(
            "query" to mapOf(
                "dis_max" to mapOf(
                    "queries" to listOf(
                        mapOf(
                            "match" to mapOf(
                                "name.en" to "Good morning"
                            )
                        ),
                        mapOf(
                            "match" to mapOf(
                                "name.de" to "Guten Morgen"
                            )
                        ),
                    ),
                    "tie_breaker" to 0.7
                )
            )
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
        compiled.body shouldContainExactly mapOf(
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
        compiled.body shouldContainExactly mapOf(
            "docvalue_fields" to listOf("name", "rank")
        )
    }

    @Test
    fun testDocvalueFields_formatted() {
        val query = SearchQuery()
            .docvalueFields(FieldFormat(AnyField("date_created"), format = "epoch_millis"))

        val compiled = compile(query)
        compiled.body shouldContainExactly mapOf(
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
        compiled.body shouldContainExactly mapOf(
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
        compiled.body shouldContainExactly mapOf(
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
        compiled.body shouldContainExactly emptyMap()

        query.queryNode(BOOL_HANDLE) { node ->
            node.should.add(
                AnyField("opinions_count").gt(4)
            )
        }
        compiled = compile(query)
        compiled.body shouldContainExactly mapOf(
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
        compiled.body shouldContainExactly mapOf(
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
                FunctionScore.Weight(
                    1.5,
                    filter = AnyField("name").match("test")
                )
            )
        }
        compiled = compile(query)
        compiled.body shouldContainExactly mapOf(
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
