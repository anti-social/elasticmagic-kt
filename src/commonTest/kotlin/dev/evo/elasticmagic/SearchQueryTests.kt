package dev.evo.elasticmagic

import dev.evo.elasticmagic.query.FunctionScore
import dev.evo.elasticmagic.query.FunctionScoreNode
import dev.evo.elasticmagic.query.NodeHandle
import io.kotest.matchers.shouldBe

import kotlin.test.Test

class SearchQueryTests {
    @Test
    fun cloning() {
        val userDoc = object : Document() {
            val login by keyword()
            val isActive by boolean()
        }

        val sq1 = SearchQuery()
            .filter(userDoc.login.eq("root"))
        val sq2 = sq1.clone()
            .filter(userDoc.isActive.eq(true))
            .size(1)

        sq1.prepare().let {
            it.size shouldBe null
            it.filters.size shouldBe 1
        }
        sq2.prepare().let {
            it.size shouldBe 1
            it.filters.size shouldBe 2
        }
    }

    @Test
    fun cloningNodes() {
        val userDoc = object : Document() {
            val login by keyword()
            val isActive by boolean()
        }

        val fsHandle = NodeHandle<FunctionScoreNode>()
        val sq1 = SearchQuery(
            FunctionScoreNode(fsHandle, null)
        )
        val sq2 = sq1.clone()

        sq1.queryNode(fsHandle) {
            it.functions.add(FunctionScore.RandomScore())
        }

        sq1.prepare().query shouldBe FunctionScoreNode(
            fsHandle, null,
            functions = listOf(FunctionScore.RandomScore())
        )

        sq2.prepare().query shouldBe FunctionScoreNode(fsHandle, null)
    }
}
