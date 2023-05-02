package dev.evo.elasticmagic.query

import io.kotest.matchers.maps.shouldContainExactly
import kotlin.test.Test

class HasChildTests : BaseExpressionTest() {

    @Test
    fun term() {
        HasChild(
            MatchAll,
            "movie",
            scoreMode = FunctionScore.ScoreMode.MAX
        ).compile() shouldContainExactly mapOf(
            "has_child" to mapOf(
                "query" to mapOf("match_all" to emptyMap<String, Any>()),
                "type" to "movie",
                "score_mode" to "max"
            )
        )
    }

    @Test
    fun termWithChild() {
        HasChild(
            MatchAll,
            "movie",
            scoreMode = FunctionScore.ScoreMode.MAX,
            minChildren = 1,
            maxChildren = 10
        ).compile() shouldContainExactly mapOf(
            "has_child" to mapOf(
                "query" to mapOf("match_all" to emptyMap<String, Any>()),
                "type" to "movie",
                "min_children" to 1,
                "max_children" to 10,
                "score_mode" to "max"
            )
        )
    }
}
