package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.query.FunctionScore
import dev.evo.elasticmagic.query.NodeHandle
import dev.evo.elasticmagic.query.QueryExpressionNode
import dev.evo.elasticmagic.query.SearchExt
import dev.evo.elasticmagic.serde.Serializer
import io.kotest.matchers.shouldBe
import kotlin.test.Test

private data class SimpleExtension(override val name: String) : SearchExt {
    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
    }

}

class SearchQueryTests {
    @Test
    fun cloning() {
        val userDoc = object : Document() {
            val login by keyword()
            val isActive by boolean()
        }

        val sq1 = SearchQuery()
            .filter(userDoc.login.eq("root"))
            .ext(SimpleExtension("test"))

        val sq2 = sq1.clone()
            .filter(userDoc.isActive.eq(true))
            .size(1)

        sq1.prepareSearch().let {
            it.size shouldBe null
            it.filters.size shouldBe 1
            it.extensions.size shouldBe 1
        }
        sq2.prepareSearch().let {
            it.size shouldBe 1
            it.filters.size shouldBe 2
            it.extensions.size shouldBe 1
        }
    }

    @Test
    fun cloningNodes() {
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

        sq1.prepareSearch().query shouldBe QueryExpressionNode(
            fsHandle,
            FunctionScore(
                functions = listOf(FunctionScore.RandomScore())
            )
        )

        sq2.prepareSearch().query shouldBe QueryExpressionNode(
            fsHandle,
            FunctionScore(functions = emptyList())
        )
    }
}
