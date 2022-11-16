package dev.evo.elasticmagic.query

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.test.Test

class FunctionScoreTests : BaseExpressionTest() {
    @Test
    fun functionScore() {
        FunctionScore(functions = emptyList()).let { fs ->
            fs.compile() shouldContainExactly mapOf(
                "function_score" to mapOf(
                    "functions" to emptyList<Nothing>()
                )
            )
            fs.reduce().shouldBeNull()
            checkClone(fs)
        }

        FunctionScore(functions = listOf(FunctionScore.RandomScore())).let { fs ->
            fs.compile() shouldContainExactly mapOf(
                "function_score" to mapOf(
                    "functions" to listOf(
                        mapOf(
                            "random_score" to emptyMap<Nothing, Nothing>()
                        )
                    )
                )
            )
            fs.reduce().shouldBeSameInstanceAs(fs)
            checkClone(fs)
        }

        FunctionScore(
            MatchAll,
            functions = listOf(
                FunctionScore.RandomScore(
                    filter = Bool.must(MovieDoc.rating.gt(7.0F))
                )
            ),
            boost = 2.2F,
            scoreMode = FunctionScore.ScoreMode.SUM,
            boostMode = FunctionScore.BoostMode.REPLACE,
            minScore = 0.001F,
        ).let { fs ->
            fs.compile() shouldContainExactly mapOf(
                "function_score" to mapOf(
                    "query" to mapOf("match_all" to emptyMap<Nothing, Nothing>()),
                    "functions" to listOf(
                        mapOf(
                            "random_score" to emptyMap<Nothing, Nothing>(),
                            "filter" to mapOf(
                                "bool" to mapOf(
                                    "must" to listOf(
                                        mapOf(
                                            "range" to mapOf(
                                                "rating" to mapOf("gt" to 7.0F)
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    "boost" to 2.2F,
                    "score_mode" to "sum",
                    "boost_mode" to "replace",
                    "min_score" to 0.001F,
                )
            )
            fs.reduce().shouldNotBeNull().compile() shouldContainExactly mapOf(
                "function_score" to mapOf(
                    "query" to mapOf("match_all" to emptyMap<Nothing, Nothing>()),
                    "functions" to listOf(
                        mapOf(
                            "random_score" to emptyMap<Nothing, Nothing>(),
                            "filter" to mapOf(
                                "range" to mapOf(
                                    "rating" to mapOf("gt" to 7.0F)
                                )
                            )
                        )
                    ),
                    "boost" to 2.2F,
                    "score_mode" to "sum",
                    "boost_mode" to "replace",
                    "min_score" to 0.001F,
                )
            )
            checkClone(fs)
        }
    }

    @Test
    fun functionScore_weight() {
        FunctionScore.Weight(3.3F).let { fn ->
            fn.compile() shouldContainExactly mapOf(
                "weight" to 3.3F
            )
            fn.reduce().shouldBeSameInstanceAs(fn)
        }

        FunctionScore.Weight(
            3.3F,
            filter = Bool.should(MovieDoc.isColored.eq(true))
        ).let { fn ->
            fn.compile() shouldContainExactly mapOf(
                "weight" to 3.3F,
                "filter" to mapOf(
                    "bool" to mapOf(
                        "should" to listOf(
                            mapOf(
                                "term" to mapOf(
                                    "is_colored" to true
                                )
                            )
                        )
                    )
                )
            )
            fn.reduce().compile() shouldContainExactly mapOf(
                "weight" to 3.3F,
                "filter" to mapOf(
                    "term" to mapOf(
                        "is_colored" to true
                    )
                )
            )
            checkClone(fn)
        }
    }

    @Test
    fun functionScore_fieldValueFactor() {
        FunctionScore.FieldValueFactor(MovieDoc.rating).let { fn ->
            fn.compile() shouldContainExactly mapOf(
                "field_value_factor" to mapOf(
                    "field" to "rating"
                )
            )
            fn.reduce().shouldBeSameInstanceAs(fn)
            checkClone(fn)
        }

        FunctionScore.FieldValueFactor(
            MovieDoc.rating,
            factor = 1.1F,
            missing = 0.0F,
            modifier = FunctionScore.FieldValueFactor.Modifier.SQRT,
            filter = Bool.should(MovieDoc.isColored.eq(true))
        ).let { fn ->
            fn.compile() shouldContainExactly mapOf(
                "field_value_factor" to mapOf(
                    "field" to "rating",
                    "factor" to 1.1F,
                    "missing" to 0.0F,
                    "modifier" to "sqrt",
                ),
                "filter" to mapOf(
                    "bool" to mapOf(
                        "should" to listOf(
                            mapOf(
                                "term" to mapOf(
                                    "is_colored" to true
                                )
                            )
                        )
                    )
                )
            )
            fn.reduce().compile() shouldContainExactly mapOf(
                "field_value_factor" to mapOf(
                    "field" to "rating",
                    "factor" to 1.1F,
                    "missing" to 0.0F,
                    "modifier" to "sqrt",
                ),
                "filter" to mapOf(
                    "term" to mapOf(
                        "is_colored" to true
                    )
                )
            )
            checkClone(fn)
        }
    }

    @Test
    fun functionScore_scriptScore() {
        FunctionScore.ScriptScore(Script.Id("rating-boost")).let { fn ->
            fn.compile() shouldContainExactly mapOf(
                "script_score" to mapOf(
                    "script" to mapOf(
                        "id" to "rating-boost"
                    )
                )
            )
        }

        FunctionScore.ScriptScore(
            Script.Id("rating-boost"),
            filter = Bool.must(MovieDoc.name.match("Terminator"))
        ).let { fn ->
            fn.compile() shouldContainExactly mapOf(
                "script_score" to mapOf(
                    "script" to mapOf(
                        "id" to "rating-boost"
                    )
                ),
                "filter" to mapOf(
                    "bool" to mapOf(
                        "must" to listOf(
                            mapOf(
                                "match" to mapOf(
                                    "name" to "Terminator"
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun functionScore_randomScore() {
        FunctionScore.RandomScore().let { fn ->
            fn.compile() shouldContainExactly mapOf(
                "random_score" to emptyMap<Nothing, Nothing>()
            )
            fn.reduce().shouldBeSameInstanceAs(fn)
            checkClone(fn)
        }

        FunctionScore.RandomScore(
            seed = 42,
            field = MovieDoc.runtime.seqNo,
        ).let { fn ->
            fn.compile() shouldContainExactly mapOf(
                "random_score" to mapOf(
                    "seed" to 42,
                    "field" to "_seq_no"
                )
            )
            fn.reduce().shouldBeSameInstanceAs(fn)
            checkClone(fn)
        }
    }
}
