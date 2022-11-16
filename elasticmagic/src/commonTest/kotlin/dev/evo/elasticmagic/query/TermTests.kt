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

        Term(MovieDoc.status, 1, boost = 2.0F).compile() shouldContainExactly mapOf(
            "term" to mapOf(
                "status" to mapOf(
                    "value" to 1,
                    "boost" to 2.0F
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

        Terms(MovieDoc.status, listOf(1, 2, 3), boost = 0.5F).compile() shouldContainExactly mapOf(
            "terms" to mapOf(
                "status" to listOf(1, 2, 3),
                "boost" to 0.5F
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

        Exists(MovieDoc.description, boost = 1.5F).compile() shouldContainExactly mapOf(
            "exists" to mapOf(
                "field" to "description",
                "boost" to 1.5F
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

        Ids(listOf("11", "12"), boost = 10.0F).compile() shouldContainExactly mapOf(
            "ids" to mapOf(
                "values" to listOf("11", "12"),
                "boost" to 10.0F
            )
        )
    }

    @Test
    fun range() {
        Range(MovieDoc.rating, gt = 8F).compile() shouldContainExactly mapOf(
            "range" to mapOf(
                "rating" to mapOf(
                    "gt" to 8F
                )
            )
        )

        Range(MovieDoc.rating, gte = 6.5F, lte = 8F, boost = 1.1F).compile() shouldContainExactly mapOf(
            "range" to mapOf(
                "rating" to mapOf(
                    "gte" to 6.5F,
                    "lte" to 8F,
                    "boost" to 1.1F
                )
            )
        )

        Range(
            MovieDoc.stars.rank,
            gt = 6F,
            lt = 9F,
            relation = Range.Relation.CONTAINS
        ).compile() shouldContainExactly mapOf(
            "range" to mapOf(
                "stars.rank" to mapOf(
                    "gt" to 6F,
                    "lt" to 9F,
                    "relation" to "CONTAINS",
                )
            )
        )
    }
}
