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

    @Test
    fun terms() {
        Terms(MovieDoc.status, listOf(1, 2, 3)).compile() shouldContainExactly mapOf(
            "terms" to mapOf(
                "status" to listOf(1, 2, 3)
            )
        )

        Terms(MovieDoc.status, listOf(1, 2, 3), boost = 0.5).compile() shouldContainExactly mapOf(
            "terms" to mapOf(
                "status" to listOf(1, 2, 3),
                "boost" to 0.5
            )
        )
    }

    @Test
    fun exists() {
        Exists(MovieDoc.description).compile() shouldContainExactly mapOf(
            "exists" to mapOf(
                "field" to "description"
            )
        )

        Exists(MovieDoc.description, boost = 1.5).compile() shouldContainExactly mapOf(
            "exists" to mapOf(
                "field" to "description",
                "boost" to 1.5
            )
        )
    }

    @Test
    fun ids() {
        Ids(listOf("11", "12")).compile() shouldContainExactly mapOf(
            "ids" to mapOf(
                "values" to listOf("11", "12")
            )
        )

        Ids(listOf("11", "12"), boost = 10.0).compile() shouldContainExactly mapOf(
            "ids" to mapOf(
                "values" to listOf("11", "12"),
                "boost" to 10.0
            )
        )
    }

    @Test
    fun range() {
        Range(MovieDoc.rating, gt = 8f).compile() shouldContainExactly mapOf(
            "range" to mapOf(
                "rating" to mapOf(
                    "gt" to 8f
                )
            )
        )

        Range(MovieDoc.rating, gte = 6.5f, lte = 8f, boost = 1.1).compile() shouldContainExactly mapOf(
            "range" to mapOf(
                "rating" to mapOf(
                    "gte" to 6.5f,
                    "lte" to 8f,
                    "boost" to 1.1
                )
            )
        )

        Range(
            MovieDoc.stars.rank,
            gt = 6f,
            lt = 9f,
            relation = Range.Relation.CONTAINS
        ).compile() shouldContainExactly mapOf(
            "range" to mapOf(
                "stars.rank" to mapOf(
                    "gt" to 6f,
                    "lt" to 9f,
                    "relation" to "CONTAINS",
                )
            )
        )
    }
}
