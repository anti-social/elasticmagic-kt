package dev.evo.elasticmagic.query

import io.kotest.matchers.maps.shouldContainExactly

import kotlin.test.Test

class FieldOperationsTests : BaseExpressionTest() {
    @Test
    fun eq() {
        (MovieDoc.genre eq "horror").compile() shouldContainExactly mapOf(
            "term" to mapOf(
                "genre" to "horror"
            )
        )

        (MovieDoc.releaseDate eq null).compile() shouldContainExactly mapOf(
            "bool" to mapOf(
                "must_not" to listOf(
                    mapOf(
                        "exists" to mapOf(
                            "field" to "release_date"
                        )
                    )
                )
            )
        )
    }

    @Test
    fun ne() {
        (MovieDoc.genre ne "comedy").compile() shouldContainExactly mapOf(
            "bool" to mapOf(
                "must_not" to listOf(
                    mapOf(
                        "term" to mapOf(
                            "genre" to "comedy"
                        )
                    )
                )
            )
        )

        (MovieDoc.releaseDate ne null).compile() shouldContainExactly mapOf(
            "exists" to mapOf(
                "field" to "release_date"
            )
        )
    }

    @Test
    fun gt() {
        (MovieDoc.rating gt 5.9f).compile() shouldContainExactly mapOf(
            "range" to mapOf(
                "rating" to mapOf(
                    "gt" to 5.9f
                )
            )
        )
    }

    @Test
    fun gte() {
        (MovieDoc.rating gte 6f).compile() shouldContainExactly mapOf(
            "range" to mapOf(
                "rating" to mapOf(
                    "gte" to 6f
                )
            )
        )
    }

    @Test
    fun lt() {
        (MovieDoc.rating lt 2.5f).compile() shouldContainExactly mapOf(
            "range" to mapOf(
                "rating" to mapOf(
                    "lt" to 2.5f
                )
            )
        )
    }

    @Test
    fun lte() {
        (MovieDoc.rating lte 2.5f).compile() shouldContainExactly mapOf(
            "range" to mapOf(
                "rating" to mapOf(
                    "lte" to 6f
                )
            )
        )
    }
}