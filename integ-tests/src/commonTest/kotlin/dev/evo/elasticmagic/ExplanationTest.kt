package dev.evo.elasticmagic

import dev.evo.elasticmagic.doc.BoundField
import dev.evo.elasticmagic.doc.RootFieldSet
import dev.evo.elasticmagic.qf.FIXTURES
import dev.evo.elasticmagic.qf.ItemDoc
import dev.evo.elasticmagic.query.match
import dev.evo.elasticmagic.types.TextType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class ExplanationTest : ElasticsearchTestBase() {
    override val indexName = "explanation"

    class StringField(name: String) : BoundField<String, String>(
        name,
        TextType,
        Params(),
        RootFieldSet
    )

    @Test
    fun withoutExplanation() = runTestWithSerdes {
        withFixtures(ItemDoc, FIXTURES) {
            val searchQuery = SearchQuery()
            val result = searchQuery.execute(index)
            result.totalHits shouldBe 8
            result.hits.mapNotNull { it.explanation } shouldBe emptyList()
        }
    }

    @Test
    fun withExplanationButEmptySearchQuery() = runTestWithSerdes {
        withFixtures(ItemDoc, FIXTURES) {
            val searchQuery = SearchQuery()
            val result = searchQuery.execute(index, Params("explain" to true))
            result.totalHits shouldBe 8
            val explanations = result.hits.mapNotNull { it.explanation }

            explanations.size shouldBe 8
            explanations.map {
                it.value shouldNotBe 1.0f
                it.details shouldBe emptyList()
            }
        }
    }

    @Test
    fun withExplanation() = runTestWithSerdes {
        withFixtures(ItemDoc, FIXTURES) {
            val searchQuery = SearchQuery(StringField("model").match("Galaxy Note 10"))
            val result = searchQuery.execute(index, Params("explain" to true))
            result.totalHits shouldBe 5
            val explanations = result.hits.mapNotNull { it.explanation }

            explanations.size shouldBe 5
            explanations.map {
                it.description shouldBe "sum of:"
                it.details.size shouldNotBe 0
            }

        }
    }
}
