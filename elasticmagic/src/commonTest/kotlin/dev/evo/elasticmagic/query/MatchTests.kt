package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.Params
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

import kotlin.test.Test

class MatchTests : BaseExpressionTest() {
    @Test
    fun minimumShouldMatch() {
        MinimumShouldMatch.Count(0).toValue() shouldBe 0
        MinimumShouldMatch.Count(1).toValue() shouldBe 1
        MinimumShouldMatch.Count(-1).toValue() shouldBe -1

        MinimumShouldMatch.Percent(75).toValue() shouldBe "75%"
        MinimumShouldMatch.Percent(-25).toValue() shouldBe "-25%"

        MinimumShouldMatch.Combinations(
            2 to MinimumShouldMatch.Percent(-25),
            9 to MinimumShouldMatch.Count(-3),
        ).toValue() shouldBe "2<-25% 9<-3"
    }

    @Test
    fun match() {
        Match(MovieDoc.name, "Matrix").compile() shouldContainExactly mapOf(
            "match" to mapOf(
                "name" to "Matrix"
            )
        )

        Match(
            MovieDoc.name,
            "Matrix",
            boost = 1.5F,
            analyzer = "text_en",
            minimumShouldMatch = MinimumShouldMatch.Percent(50),
            params = Params(
                "unknown" to true
            )
        ).compile() shouldContainExactly mapOf(
            "match" to mapOf(
                "name" to mapOf(
                    "query" to "Matrix",
                    "boost" to 1.5F,
                    "analyzer" to "text_en",
                    "minimum_should_match" to "50%",
                    "unknown" to true,
                )
            )
        )
    }

    @Test
    fun matchPhrase() {
        MatchPhrase(
            MovieDoc.description, "quick brown fox"
        ).compile() shouldContainExactly mapOf(
            "match_phrase" to mapOf(
                "description" to "quick brown fox"
            )
        )

        MatchPhrase(
            MovieDoc.description,
            "quick brown fox",
            slop = 3,
            boost = 1.5F,
            analyzer = "text",
            params = Params("boost" to 2),
        ).compile() shouldContainExactly mapOf(
            "match_phrase" to mapOf(
                "description" to mapOf(
                    "query" to "quick brown fox",
                    "slop" to 3,
                    "boost" to 1.5F,
                    "analyzer" to "text",
                )
            )
        )
    }

    @Test
    fun matchAll() {
        MatchAll.compile() shouldContainExactly mapOf(
            "match_all" to emptyMap<String, Any>()
        )
        MatchAll(boost = 1.2F).compile() shouldContainExactly mapOf(
            "match_all" to mapOf("boost" to 1.2F)
        )
    }

    @Test
    fun multiMatch() {
        MultiMatch(
            "Matrix",
            listOf(MovieDoc.name.boost(2.5F), MovieDoc.description)
        ).compile() shouldContainExactly mapOf(
            "multi_match" to mapOf(
                "query" to "Matrix",
                "fields" to listOf("name^2.5", "description")
            )
        )

        MultiMatch(
            "Matrix",
            listOf(MovieDoc.name, MovieDoc.description),
            type = MultiMatch.Type.BEST_FIELDS,
            boost = 0.9F,
            params = Params(
                "cutoff_frequency" to 0.001
            )
        ).compile() shouldContainExactly mapOf(
            "multi_match" to mapOf(
                "query" to "Matrix",
                "fields" to listOf("name", "description"),
                "type" to "best_fields",
                "boost" to 0.9F,
                "cutoff_frequency" to 0.001,
            )
        )
    }
}
