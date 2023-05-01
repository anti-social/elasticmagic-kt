package dev.evo.elasticmagic.query

import io.kotest.matchers.maps.shouldContainExactly
import kotlin.test.Test

class HasParentTests : BaseExpressionTest() {

    @Test
    fun term() {
        HasParent(
            MovieDoc.join.eq("1999"),
            "year"
        ).compile() shouldContainExactly mapOf(
            "has_parent" to mapOf(
                "score" to false,
                "query" to mapOf("term" to mapOf("join" to "1999")),
                "parent_type" to "year"
            )
        )
    }
}