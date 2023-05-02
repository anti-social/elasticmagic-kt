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
}
