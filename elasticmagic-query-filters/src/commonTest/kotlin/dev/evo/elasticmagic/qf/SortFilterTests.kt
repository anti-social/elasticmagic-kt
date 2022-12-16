package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

import kotlin.test.Test

fun emptySearchResult(): SearchQueryResult<Nothing> {
    return SearchQueryResult(
        rawResult = null,
        took = 10,
        timedOut = false,
        totalHits = 100,
        totalHitsRelation = null,
        maxScore = null,
        hits = emptyList(),
        aggs = emptyMap(),
    )
}

class SortFilterTests  : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    @Test
    fun sort() = testWithCompiler {
        val filter = SortFilter(
            SortFilterValue("score", emptyList()),
            SortFilterValue("price", listOf(BikeDocument.price)),
            SortFilterValue("-price", listOf(BikeDocument.price.desc())),
        )

        SearchQuery().also { sq ->
            val ctx = filter.prepare("sort", emptyMap())
            ctx.apply(sq, emptyList())
            compile(sq).body shouldContainExactly emptyMap()

            val sort = ctx.processResult(emptySearchResult())
            sort.name shouldBe "sort"
            sort.values.size shouldBe 3
            sort.values[0].value shouldBe "score"
            sort.values[0].selected.shouldBeTrue()
            sort.values[1].value shouldBe "price"
            sort.values[1].selected.shouldBeFalse()
            sort.values[2].value shouldBe "-price"
            sort.values[2].selected.shouldBeFalse()
        }

        SearchQuery().also { sq ->
            val ctx = filter.prepare("sort", mapOf(listOf("sort") to listOf("price")))
            ctx.apply(sq, emptyList())
            compile(sq).body shouldContainExactly mapOf(
                "sort" to listOf("price")
            )

            val sort = ctx.processResult(emptySearchResult())
            sort.name shouldBe "sort"
            sort.values.size shouldBe 3
            sort.values[0].value shouldBe "score"
            sort.values[0].selected.shouldBeFalse()
            sort.values[1].value shouldBe "price"
            sort.values[1].selected.shouldBeTrue()
            sort.values[2].value shouldBe "-price"
            sort.values[2].selected.shouldBeFalse()
        }

        SearchQuery().also { sq ->
            val ctx = filter.prepare("sort", mapOf(listOf("sort") to listOf("-price")))
            ctx.apply(sq, emptyList())
            compile(sq).body shouldContainExactly mapOf(
                "sort" to listOf(mapOf("price" to mapOf("order" to "desc")))
            )

            val sort = ctx.processResult(emptySearchResult())
            sort.name shouldBe "sort"
            sort.values.size shouldBe 3
            sort.values[0].value shouldBe "score"
            sort.values[0].selected.shouldBeFalse()
            sort.values[1].value shouldBe "price"
            sort.values[1].selected.shouldBeFalse()
            sort.values[2].value shouldBe "-price"
            sort.values[2].selected.shouldBeTrue()
        }
    }

    @Test
    fun missingSortValues() {
        shouldThrow<IllegalArgumentException> {
            SortFilter()
        }
    }
}
