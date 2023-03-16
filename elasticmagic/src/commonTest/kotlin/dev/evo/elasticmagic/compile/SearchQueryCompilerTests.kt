package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchType
import dev.evo.elasticmagic.aggs.DateHistogramAgg
import dev.evo.elasticmagic.aggs.FixedInterval
import dev.evo.elasticmagic.aggs.MinAgg
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.doc.BoundField
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.RootFieldSet
import dev.evo.elasticmagic.types.SimpleFieldType
import dev.evo.elasticmagic.doc.SubDocument
import dev.evo.elasticmagic.doc.datetime
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.DisMax
import dev.evo.elasticmagic.query.FieldFormat
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.FunctionScore
import dev.evo.elasticmagic.query.Ids
import dev.evo.elasticmagic.query.Match
import dev.evo.elasticmagic.query.MatchAll
import dev.evo.elasticmagic.query.MinimumShouldMatch
import dev.evo.elasticmagic.query.MultiMatch
import dev.evo.elasticmagic.query.NodeHandle
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.query.Sort
import dev.evo.elasticmagic.query.QueryExpressionNode
import dev.evo.elasticmagic.query.QueryRescore
import dev.evo.elasticmagic.query.SearchExt
import dev.evo.elasticmagic.query.match
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.types.LongType

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainExactly

import kotlinx.datetime.LocalDateTime

import kotlin.test.Test

class AnyField(name: String) : BoundField<Any, Any>(
    name,
    object : SimpleFieldType<Any>() {
        override val name: String
            get() = throw IllegalStateException("Fake field type cannot be used in mapping")
        override val termType = Any::class

        override fun deserialize(v: Any, valueFactory: (() -> Any)?) = v
    },
    Params(),
    RootFieldSet
)

class StringField(name: String) : BoundField<String, String>(
    name,
    object : SimpleFieldType<String>() {
        override val name: String
            get() = throw IllegalStateException("Fake field type cannot be used in mapping")
        override val termType = String::class

        override fun deserialize(v: Any, valueFactory: (() -> String)?) = v.toString()
    },
    Params(),
    RootFieldSet
)

class SearchQueryCompilerTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    @Test
    fun fieldTypeSerialization() = testWithCompiler {
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
    fun empty() = testWithCompiler {
        val compiled = compile(SearchQuery())
        compiled.body shouldContainExactly emptyMap()
    }

    @Test
    fun query() = testWithCompiler {
        val query = SearchQuery(StringField("name").match("Tesla"))
        compile(query).let { compiled ->
            compiled.body shouldContainExactly mapOf(
                "query" to mapOf(
                    "match" to mapOf(
                        "name" to "Tesla"
                    )
                )
            )
        }

        query.query(
            Match(
                StringField("name"), "Tesla model S",
                minimumShouldMatch = MinimumShouldMatch.Count(-1),
                params = Params("boost" to 1.5)
            )
        )
        compile(query).let { compiled ->
            compiled.body shouldContainExactly mapOf(
                "query" to mapOf(
                    "match" to mapOf(
                        "name" to mapOf(
                            "query" to "Tesla model S",
                            "minimum_should_match" to -1,
                            "boost" to 1.5
                        )
                    )
                )
            )
        }

        compile(query.query(MatchAll)).let { compiled ->
            compiled.body shouldContainExactly mapOf(
                "query" to mapOf("match_all" to emptyMap<String, Any>())
            )
        }

        compile(query.query(null)).body shouldContainExactly emptyMap()
    }

    @Test
    fun aggs() = testWithCompiler {
        val query = SearchQuery()
        query.aggs(
            "statuses" to TermsAgg(
                AnyField("status"),
                size = 100,
                shardSize = 1000,
            )
        )
        compile(query).let { compiled ->
            compiled.body shouldContainExactly mapOf(
                "aggs" to mapOf(
                    "statuses" to mapOf(
                        "terms" to mapOf(
                            "field" to "status",
                            "size" to 100,
                            "shard_size" to 1000,
                        )
                    )
                )
            )
        }

        query.aggs(
            "statuses" to TermsAgg(
                AnyField("status"),
                size = 10,
                shardSize = 100,
            )
        )
        compile(query).let { compiled ->
            compiled.body shouldContainExactly mapOf(
                "aggs" to mapOf(
                    "statuses" to mapOf(
                        "terms" to mapOf(
                            "field" to "status",
                            "size" to 10,
                            "shard_size" to 100,
                        )
                    )
                )
            )
        }

        query.aggs(
            "date_created_hist" to DateHistogramAgg(
                AnyField("date_created"),
                interval = DateHistogramAgg.Interval.Fixed(FixedInterval.Hours(2)),
                aggs = mapOf(
                    "min_price" to MinAgg(
                        AnyField("price")
                    )
                )
            )
        )
        compile(query).let { compiled ->
            compiled.body shouldContainExactly mapOf(
                "aggs" to mapOf(
                    "statuses" to mapOf(
                        "terms" to mapOf(
                            "field" to "status",
                            "size" to 10,
                            "shard_size" to 100,
                        )
                    ),
                    "date_created_hist" to mapOf(
                        "date_histogram" to mapOf(
                            "field" to "date_created",
                            "fixed_interval" to "2h",
                        ),
                        "aggs" to mapOf(
                            "min_price" to mapOf(
                                "min" to mapOf(
                                    "field" to "price"
                                )
                            )
                        )
                    )
                )
            )
        }

        query.aggs(SearchQuery.CLEAR)
        compile(query).body shouldContainExactly emptyMap()
    }

    @Test
    fun composeFilters() = testWithCompiler {
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
    fun filter() = testWithCompiler {
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
                    FunctionScore.Weight(2.0F, productDoc.company.opinion.count.eq(5)),
                    FunctionScore.FieldValueFactor(productDoc.rank, 5.0F),
                )
            )
        )
        query.filter(productDoc.company.opinion.count.gt(4))
        compile(query).body shouldContainExactly mapOf(
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
                                        "weight" to 2.0F,
                                    ),
                                    mapOf(
                                        "field_value_factor" to mapOf(
                                            "field" to "rank",
                                            "factor" to 5.0F,
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

        query.filter(SearchQuery.CLEAR)
        compile(query).body shouldContainExactly mapOf(
            "query" to mapOf(
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
                            "weight" to 2.0F,
                        ),
                        mapOf(
                            "field_value_factor" to mapOf(
                                "field" to "rank",
                                "factor" to 5.0F,
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
        )
    }

    @Test
    fun postFilter() = testWithCompiler {
        val query = SearchQuery()
            .postFilter(AnyField("status").eq(0))
        compile(query).body shouldContainExactly mapOf(
            "post_filter" to mapOf(
                "term" to mapOf(
                    "status" to 0
                )
            )
        )

        query.postFilter(AnyField("rank").lt(8.5))
        compile(query).body shouldContainExactly mapOf(
            "post_filter" to mapOf(
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
                                    "lt" to 8.5
                                )
                            )
                        )
                    )
                )
            )
        )

        query.postFilter(SearchQuery.CLEAR)
        compile(query).body shouldContainExactly emptyMap()
    }

    @Test
    fun rescore() = testWithCompiler {
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
                            script = Script.Source(
                                "Math.log10(doc[params.field].value + 2)",
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

        query.rescore(SearchQuery.CLEAR)
        compile(query).body shouldContainExactly emptyMap()
    }

    @Test
    fun sort_fieldSimplified() = testWithCompiler {
        val query = SearchQuery()
            .sort(AnyField("popularity"), Sort(AnyField("id")))
        compile(query).body shouldContainExactly mapOf(
            "sort" to listOf("popularity", "id")
        )

        query.sort(SearchQuery.CLEAR)
        compile(query).body shouldContainExactly emptyMap()
    }

    @Test
    fun sort_fieldOrder() = testWithCompiler {
        compile(
            SearchQuery()
                .sort(Sort(AnyField("popularity"), order = Sort.Order.DESC))
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
    fun sort_fieldAllParams() = testWithCompiler {
        compile(
            SearchQuery()
                .sort(
                    Sort(
                        AnyField("popularity"),
                        order = Sort.Order.DESC,
                        mode = Sort.Mode.MEDIAN,
                        numericType = Sort.NumericType.LONG,
                        missing = Sort.Missing.Value(50),
                        unmappedType = LongType,
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
    fun sort_scriptWithOrder() = testWithCompiler {
        compile(
            SearchQuery()
                .sort(
                    Sort(
                        Script.Source(
                            "doc[params.field].value",
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
    fun trackScores() = testWithCompiler {
        compile(SearchQuery().trackScores(true)).body shouldContainExactly mapOf(
            "track_scores" to true
        )
    }

    @Test
    fun trackTotalHits() = testWithCompiler {
        val searchQueryBody = compile(SearchQuery().trackTotalHits(true)).body
        if (features.supportsTrackingOfTotalHits) {
            searchQueryBody shouldContainExactly mapOf(
                "track_total_hits" to true
            )
        } else {
            searchQueryBody shouldContainExactly emptyMap()
        }
    }

    @Test
    fun source() = testWithCompiler {
        compile(
            SearchQuery().source()
        ).body shouldContainExactly mapOf(
            "_source" to emptyList<Nothing>()
        )

        compile(
            SearchQuery().source(false)
        ).body shouldContainExactly mapOf(
            "_source" to false
        )

        compile(
            SearchQuery().source(true)
        ).body shouldContainExactly mapOf(
            "_source" to true
        )

        val searchQuery = SearchQuery()
            .source(AnyField("name"), AnyField("rank"))
        compile(
            searchQuery
        ).body shouldContainExactly mapOf(
            "_source" to listOf(
                "name", "rank"
            )
        )
        compile(
            searchQuery
                .source(AnyField("desc*"))
                .source(excludes = listOf(AnyField("description")))
        ).body shouldContainExactly mapOf(
            "_source" to mapOf(
                "includes" to listOf("name", "rank", "desc*"),
                "excludes" to listOf("description")
            )
        )
        compile(
            searchQuery.source(SearchQuery.CLEAR)
        ).body shouldContainExactly emptyMap()
    }

    @Test
    fun fields() = testWithCompiler {
        compile(
            SearchQuery().fields(*arrayOf())
        ).body shouldContainExactly emptyMap()

        compile(
            SearchQuery()
                .fields(AnyField("rank"), AnyField("opinion.rating"))
        ).body shouldContainExactly mapOf(
            "fields" to listOf("rank", "opinion.rating")
        )

        compile(
            SearchQuery()
                .fields(FieldFormat(AnyField("date_created"), "epoch_millis"))
        ).body shouldContainExactly mapOf(
            "fields" to listOf(mapOf("field" to "date_created", "format" to "epoch_millis"))
        )
    }

    @Test
    fun docvalueFields() = testWithCompiler {
        val query = SearchQuery().docvalueFields(*arrayOf())
        compile(query).body shouldContainExactly emptyMap()

        query.docvalueFields(AnyField("rank"), AnyField("opinion.rating"))
        compile(query).body shouldContainExactly mapOf(
            "docvalue_fields" to listOf("rank", "opinion.rating")
        )

        query.docvalueFields(AnyField("date_created").format("YYYY"))
        compile(query).body shouldContainExactly mapOf(
            "docvalue_fields" to listOf(
                "rank",
                "opinion.rating",
                mapOf(
                    "field" to "date_created",
                    "format" to "YYYY"
                ),
            )
        )

        compile(query.docvalueFields(SearchQuery.CLEAR)).body shouldContainExactly emptyMap()
    }

    @Test
    fun storedFields() = testWithCompiler {
        val query = SearchQuery().storedFields(*arrayOf())
        compile(query).body shouldContainExactly emptyMap()

        query.storedFields(AnyField("rank"), AnyField("opinion.rating"))
        compile(query).body shouldContainExactly mapOf(
            "stored_fields" to listOf("rank", "opinion.rating")
        )

        compile(query.storedFields(SearchQuery.CLEAR)).body shouldContainExactly emptyMap()
    }

    @Test
    fun scriptFields() = testWithCompiler {
        val query = SearchQuery().scriptFields(*arrayOf())
        compile(query).body shouldContainExactly emptyMap()

        query.scriptFields(
            "weighted_rank" to Script.Source("doc['rank'].value * doc['weight'].value"),
        )
        compile(query).body shouldContainExactly mapOf(
            "script_fields" to mapOf(
                "weighted_rank" to mapOf(
                    "script" to mapOf(
                        "source" to "doc['rank'].value * doc['weight'].value"
                    )
                )
            )
        )

        compile(query.scriptFields(SearchQuery.CLEAR)).body shouldContainExactly emptyMap()
    }

    @Test
    fun sizeAndFrom() = testWithCompiler {
        val query = SearchQuery()
            .size(100)
            .from(200)

        val compiled = compile(query)
        compiled.body shouldContainExactly mapOf(
            "size" to 100,
            "from" to 200,
        )
    }

    @Test
    fun terminateAfter() = testWithCompiler {
        val query = SearchQuery().terminateAfter(10_000)

        val compiled = compile(query)
        compiled.body shouldContainExactly mapOf(
            "terminate_after" to 10_000,
        )
    }

    @Test
    fun searchParams() = testWithCompiler {
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

        query.routing("4321")
        compile(query).params shouldContainExactly mapOf(
            "search_type" to listOf("dfs_query_then_fetch"),
            "routing" to listOf("4321"),
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

        query
            .stats("test")
            .version(true)
            .seqNoPrimaryTerm(true)
        compile(query).params shouldContainExactly mapOf(
            "search_type" to listOf("query_then_fetch"),
            "request_cache" to listOf("true"),
            "stats" to listOf("test"),
            "version" to listOf("true"),
            "seq_no_primary_term" to listOf("true")
        )

        query
            .searchType(null)
            .requestCache(null)
            .stats(null)
            .version(null)
            .seqNoPrimaryTerm(null)
        compile(query).params shouldContainExactly emptyMap()
    }

    @Test
    fun termsQuery() = testWithCompiler {
        val query = SearchQuery(
            AnyField("tags").oneOf(listOf(1, 9))
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
    fun idsQuery() = testWithCompiler {
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
    fun disMaxQuery() = testWithCompiler {
        val query = SearchQuery(
            DisMax(
                listOf(
                    StringField("name.en").match("Good morning"),
                    StringField("name.es").match("Buenos días"),
                    StringField("name.de").match("Guten Morgen"),
                ),
                tieBreaker = 0.5F
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
                    "tie_breaker" to 0.5F
                )
            )
        )
    }

    @Test
    fun disMaxNodeQuery() = testWithCompiler {
        @Suppress("VariableNaming")
        val LANG_HANDLE = NodeHandle<DisMax>()
        val query = SearchQuery(
            QueryExpressionNode(
                LANG_HANDLE,
                DisMax(emptyList())
            )
        )
        compile(query).body shouldContainExactly emptyMap()

        query.queryNode(LANG_HANDLE) { node ->
            node.copy(
                queries = node.queries + listOf(
                    StringField("name.en").match("Good morning"),
                )
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
            node.copy(
                queries = node.queries + listOf(
                    StringField("name.de").match("Guten Morgen"),
                ),
                tieBreaker = 0.7F,
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
                    "tie_breaker" to 0.7F
                )
            )
        )

        shouldThrow<IllegalArgumentException> {
            query.queryNode(NodeHandle<DisMax>()) { it }
        }
    }

    @Test
    fun functionScoreQuery_scriptScore() = testWithCompiler {
        val query = SearchQuery(
            FunctionScore(
                query = null,
                functions = listOf(
                    FunctionScore.ScriptScore(
                        Script.Source(
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
    fun docvalueFields_simple() = testWithCompiler {
        val query = SearchQuery()
            .docvalueFields(AnyField("name"), AnyField("rank"))

        val compiled = compile(query)
        compiled.body shouldContainExactly mapOf(
            "docvalue_fields" to listOf("name", "rank")
        )
    }

    @Test
    fun docvalueFields_formatted() = testWithCompiler {
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

    fun queryNodes() = testWithCompiler {
        @Suppress("VariableNaming")
        val BOOL_HANDLE = NodeHandle<Bool>("bool")
        @Suppress("VariableNaming")
        val AD_BOOST_HANDLE = NodeHandle<FunctionScore>("ad_boost")

        val query = SearchQuery(
            QueryExpressionNode(
                BOOL_HANDLE,
                Bool.should(
                    QueryExpressionNode(
                        AD_BOOST_HANDLE,
                        FunctionScore(
                            functions = emptyList()
                        )
                    )
                )
            )
        )
        var compiled = compile(query)
        compiled.body shouldContainExactly emptyMap()

        query.queryNode(BOOL_HANDLE) { node ->
            node.copy(
                should = node.should + listOf(
                    AnyField("opinions_count").gt(4)
                )
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
            node.copy(
                should = node.should + listOf(
                    AnyField("opinions_positive_percent").gt(90.0)
                )
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
            node.copy(
                functions = node.functions + listOf(
                    FunctionScore.Weight(
                        1.5F,
                        filter = StringField("name").match("test")
                    )
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
                                        "weight" to 1.5F,
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

    @Test
    fun ext() = testWithCompiler {
        // Search extension example for: https://github.com/anti-social/elasticsearch-collapse-extension
        data class CollapseExtension(
            val field: FieldOperations<*>,
            val windowSize: Int,
            val shardSize: Int,
            val sort: Sort,
        ) : SearchExt {
            override val name = "collapse"

            override fun clone() = copy()

            override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
                ctx.field("field", field.getQualifiedFieldName())
                ctx.field("window_size", windowSize)
                ctx.field("shard_size", shardSize)
                ctx.array("sort") {
                    compiler.visit(this, listOf(sort))
                }
            }
        }

        val carDoc = object : Document() {
            val model by keyword()
            val price by float()
        }

        val query = SearchQuery().ext(
            CollapseExtension(carDoc.model, windowSize = 10_000, shardSize = 1000, sort = carDoc.price.asc())
        )
        compile(query).body shouldContainExactly mapOf(
            "ext" to mapOf(
                "collapse" to mapOf(
                    "field" to "model",
                    "window_size" to 10000,
                    "shard_size" to 1000,
                    "sort" to listOf(
                        mapOf(
                            "price" to mapOf(
                                "order" to "asc"
                            )
                        )
                    )
                )
            )
        )

        query.ext(SearchQuery.CLEAR)
        compile(query).body shouldContainExactly emptyMap()
    }
}
