package dev.evo.elasticmagic

import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.query.FunctionScore
import dev.evo.elasticmagic.query.NodeHandle
import dev.evo.elasticmagic.query.QueryExpressionNode

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

        val fsHandle = NodeHandle<FunctionScore>()
        val sq1 = SearchQuery(
            QueryExpressionNode(
                fsHandle,
                FunctionScore(functions = emptyList())
            )
        )
        val sq2 = sq1.clone()

        sq1.queryNode(fsHandle) { node ->
            node.copy(
                functions = node.functions + listOf(FunctionScore.RandomScore())
            )
        }

        sq1.prepare().query shouldBe QueryExpressionNode(
            fsHandle,
            FunctionScore(
                functions = listOf(FunctionScore.RandomScore())
            )
        )

        sq2.prepare().query shouldBe QueryExpressionNode(
            fsHandle,
            FunctionScore(functions = emptyList())
        )
    }
}
