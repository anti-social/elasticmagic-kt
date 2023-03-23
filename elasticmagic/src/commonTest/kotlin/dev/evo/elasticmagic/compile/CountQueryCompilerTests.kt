package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.query.match

import io.kotest.matchers.maps.shouldContainExactly

import kotlin.test.Test

class CountQueryCompilerTests : BaseCompilerTest<CountQueryCompiler>(::CountQueryCompiler) {
    @Test
    fun empty() = testWithCompiler {
        val compiled = compile(SearchQuery())
        compiled.body shouldContainExactly emptyMap()
    }

    @Test
    fun query() = testWithCompiler {
        val query = SearchQuery(StringField("name").match("Tesla"))
            .filter(AnyField("status") eq 0)
            .aggs("types" to TermsAgg(AnyField("type")))
            .docvalueFields(AnyField("id"), AnyField("status"))
            .sort(AnyField("id"))
        compile(query).let { compiled ->
            compiled.body shouldContainExactly mapOf(
                "query" to mapOf(
                    "bool" to mapOf(
                        "filter" to listOf(
                            mapOf(
                                "term" to mapOf(
                                    "status" to 0
                                )
                            )
                        ),
                        "must" to listOf(
                            mapOf(
                                "match" to mapOf(
                                    "name" to "Tesla"
                                )
                            )
                        )
                    )
                )
            )
        }
    }
}
