package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.SearchHit
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.compile.BaseCompilerTest
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.DynDocSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

import kotlin.test.Test

fun searchResultWithHits(totalHits: Long, hits: List<SearchHit<DynDocSource>>): SearchQueryResult<DynDocSource> {
    return SearchQueryResult(
        rawResult = null,
        took = 10,
        timedOut = false,
        totalHits = totalHits,
        totalHitsRelation = null,
        maxScore = null,
        hits = hits,
        aggs = emptyMap(),
    )
}

class PageFilterTests : BaseCompilerTest<SearchQueryCompiler>(::SearchQueryCompiler) {
    @Test
    fun default() = testWithCompiler {
        val filter = PageFilter()
        val ctx = filter.prepare("page", emptyMap())

        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        val expectedQuery = buildMap {
            put("from", 0)
            put("size", 10)
            if (features.supportsTrackingOfTotalHits) {
                put("track_total_hits", true)
            }
        }
        compile(sq).body shouldContainExactly expectedQuery

        val page = ctx.processResult(
            searchResultWithHits(
                105,
                listOf(
                    SearchHit("test", "_doc", "1"),
                )
            )
        )
        page.page shouldBe 1
        page.perPage shouldBe 10
        page.from shouldBe 0
        page.size shouldBe 10
        page.totalHits shouldBe 105L
        page.totalPages shouldBe 11
        page.hits.size shouldBe 1
    }

    @Test
    fun fromExceedsMaxHits() = testWithCompiler {
        val filter = PageFilter()
        val ctx = filter.prepare("page", mapOf(listOf("page") to listOf("1002")))

        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        val expectedQuery = buildMap {
            put("from", 10000)
            put("size", 0)
            if (features.supportsTrackingOfTotalHits) {
                put("track_total_hits", true)
            }
        }
        compile(sq).body shouldContainExactly expectedQuery

        val page = ctx.processResult(
            searchResultWithHits(
                10_500,
                emptyList()
            )
        )
        page.page shouldBe 1002
        page.perPage shouldBe 10
        page.from shouldBe 10000
        page.size shouldBe 0
        page.totalHits shouldBe 10_500L
        page.totalPages shouldBe 1000
        page.hits.shouldBeEmpty()
    }

    @Test
    fun fromPlusSizeExceedsMaxHits() = testWithCompiler {
        val filter = PageFilter(availablePageSizes = listOf(99))
        val ctx = filter.prepare("page", mapOf(listOf("page") to listOf("102")))

        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        val expectedQuery = buildMap {
            put("from", 9999)
            put("size", 1)
            if (features.supportsTrackingOfTotalHits) {
                put("track_total_hits", true)
            }
        }
        compile(sq).body shouldContainExactly expectedQuery

        val page = ctx.processResult(
            searchResultWithHits(
                10000,
                listOf(
                    SearchHit("test", "_doc", "1"),
                )
            )
        )
        page.page shouldBe 102
        page.perPage shouldBe 99
        page.from shouldBe 9999
        page.size shouldBe 1
        page.totalHits shouldBe 10000L
        page.totalPages shouldBe 102
        page.hits.size shouldBe 1
    }

    @Test
    fun pageSize() = testWithCompiler {
        val filter = PageFilter()
        val ctx = filter.prepare("page", mapOf(listOf("page", "size") to listOf("50")))

        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        val expectedQuery = buildMap {
            put("from", 0)
            put("size", 50)
            if (features.supportsTrackingOfTotalHits) {
                put("track_total_hits", true)
            }
        }
        compile(sq).body shouldContainExactly expectedQuery
    }

    @Test
    fun pageSizeNotAvailable() = testWithCompiler {
        val filter = PageFilter()
        val ctx = filter.prepare("page", mapOf(listOf("page", "size") to listOf("99")))

        val sq = SearchQuery()
        ctx.apply(sq, emptyList())

        val expectedQuery = buildMap {
            put("from", 0)
            put("size", 10)
            if (features.supportsTrackingOfTotalHits) {
                put("track_total_hits", true)
            }
        }
        compile(sq).body shouldContainExactly expectedQuery
    }

    @Test
    fun emptyAvailablePageSizes() {
        shouldThrow<IllegalArgumentException> {
            PageFilter(availablePageSizes = emptyList())
        }
    }
}
