package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.Document
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SearchQueryTimeoutTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    @Test
    fun timeoutInSearchQuery() = testWithCompiler {
        val userDoc = object : Document() {
            val login by keyword()
            val isActive by boolean()
        }

        val sq1 = SearchQuery()
            .filter(userDoc.login.eq("root"))
            .setTimeout(4.seconds)

        sq1.prepareSearch().let {
            it.size shouldBe null
            it.filters.size shouldBe 1
            it.timeout shouldBe 4.seconds
        }

        compile(sq1).body shouldBe mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf("term" to mapOf("login" to "root"))
                    )
                )
            ),
            "timeout" to "4000ms"
        )
    }

    @Test
    fun timeoutInParams() = testWithCompiler {
        val userDoc = object : Document() {
            val login by keyword()
            val isActive by boolean()
        }

        val sq1 = SearchQuery(params = Params("timeout" to 4.seconds))
            .filter(userDoc.login.eq("root"))


        sq1.prepareSearch().let {
            it.size shouldBe null
            it.filters.size shouldBe 1
            it.timeout shouldBe null
        }
        val compiled = compile(sq1)
        compiled.body shouldBe mapOf(
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf("term" to mapOf("login" to "root"))
                    )
                )
            )
        )
        compiled.params shouldContainExactly mapOf("timeout" to listOf("4000ms"))
    }
}
