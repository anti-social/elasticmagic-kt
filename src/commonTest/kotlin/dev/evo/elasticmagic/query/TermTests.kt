package dev.evo.elasticmagic.query

import io.kotest.matchers.maps.shouldContainExactly

import kotlin.test.Test

class TermTests : BaseExpressionTest() {
    @Test
    fun term() {
        Term(MovieDoc.status, 1).compile() shouldContainExactly mapOf(
            "term" to mapOf(
                "status" to 1
            )
        )

        Term(MovieDoc.status, 1, boost = 2.0).compile() shouldContainExactly mapOf(
            "term" to mapOf(
                "status" to mapOf(
                    "value" to 1,
                    "boost" to 2.0
                )
            )
        )
    }
}
