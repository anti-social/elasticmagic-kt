package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.query.Sort

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly

import kotlinx.datetime.LocalDate

import kotlin.test.Test

class HistogramTests : TestAggregation() {
    @Test
    fun histogram() {
        HistogramAgg(MovieDoc.rating, interval = 10F).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "histogram" to mapOf(
                    "field" to "rating",
                    "interval" to 10.0F,
                )
            )

            shouldThrow<IllegalStateException> {
                process(agg, emptyMap())
            }
            process(
                agg,
                mapOf(
                    "buckets" to emptyList<Nothing>()
                )
            ).let { res ->
                res.buckets.shouldBeEmpty()
            }
            process(
                agg,
                mapOf(
                    "buckets" to listOf(
                        mapOf(
                            "key" to 0.0,
                            "doc_count" to 2,
                        ),
                        mapOf(
                            "key" to 10.0,
                            "doc_count" to 39,
                        ),
                        mapOf(
                            "key" to 90.0,
                            "doc_count" to 88,
                        ),
                    )
                )
            ).let { res ->
                res.buckets shouldHaveSize 3
                res.buckets[0].key shouldBe 0.0
                res.buckets[0].docCount shouldBe 2
                res.buckets[1].key shouldBe 10.0
                res.buckets[1].docCount shouldBe 39
                res.buckets[2].key shouldBe 90.0
                res.buckets[2].docCount shouldBe 88
            }
        }

        HistogramAgg(
            MovieDoc.rating,
            interval = 10.0F,
            offset = 25.0F,
            minDocCount = 5,
            missing = 0.0F,
            order = listOf(
                BucketsOrder("_count", Sort.Order.DESC),
                BucketsOrder("stats.std_deviation", Sort.Order.ASC)
            ),
            extendedBounds = HistogramBounds(-10.0F, 110.0F),
            hardBounds = HistogramBounds(10.0F, 90.0F),
            aggs = mapOf(
                "stats" to ExtendedStatsAgg(MovieDoc.rating)
            )
        ).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "histogram" to mapOf(
                    "field" to "rating",
                    "interval" to 10.0F,
                    "offset" to 25.0F,
                    "min_doc_count" to 5L,
                    "missing" to 0.0F,
                    "order" to listOf(
                        mapOf("_count" to "desc"),
                        mapOf("stats.std_deviation" to "asc")
                    ),
                    "extended_bounds" to mapOf(
                        "min" to -10.0F,
                        "max" to 110.0F,
                    ),
                    "hard_bounds" to mapOf(
                        "min" to 10.0F,
                        "max" to 90.0F,
                    ),
                ),
                "aggs" to mapOf(
                    "stats" to mapOf(
                        "extended_stats" to mapOf(
                            "field" to "rating"
                        )
                    )
                )
            )
        }
    }

    @Test
    fun dateHistogram() {
        DateHistogramAgg(
            MovieDoc.releaseDate,
            interval = DateHistogramAgg.Interval.Calendar(CalendarInterval.YEAR),
            offset = FixedInterval.Minutes(1),
            minDocCount = 1,
            missing = LocalDate(1970, 1, 1),
            format = "YYYY",
            order = listOf(BucketsOrder("_count", Sort.Order.DESC)),
            extendedBounds = HistogramBounds.to(LocalDate(2030, 1, 1)),
            hardBounds = HistogramBounds.from(LocalDate(2000, 1, 1)),
        ).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "date_histogram" to mapOf(
                    "field" to "release_date",
                    "calendar_interval" to "year",
                    "offset" to "1m",
                    "min_doc_count" to 1L,
                    "missing" to "1970-01-01",
                    "format" to "YYYY",
                    "order" to mapOf(
                        "_count" to "desc"
                    ),
                    "extended_bounds" to mapOf(
                        "max" to "2030-01-01",
                    ),
                    "hard_bounds" to mapOf(
                        "min" to "2000-01-01",
                    )
                )
            )

            shouldThrow<IllegalStateException> {
                process(agg, emptyMap())
            }
            process(
                agg,
                mapOf(
                    "buckets" to listOf(
                        mapOf(
                            "key" to 1388534460000L,
                            "key_as_string" to "2014",
                            "doc_count" to 1,
                        ),
                        mapOf(
                            "key" to 1420070460000L,
                            "key_as_string" to "2015",
                            "doc_count" to 0,
                        )
                    )
                )
            ).let { res ->
                res.buckets.shouldHaveSize(2)
                res.buckets[0].key shouldBe 1388534460000L
                res.buckets[0].keyAsString shouldBe "2014"
                res.buckets[0].keyAsDatetime shouldBe LocalDate(2014, 1, 1)
                res.buckets[0].docCount shouldBe 1L
                res.buckets[1].key shouldBe 1420070460000L
                res.buckets[1].keyAsString shouldBe "2015"
                res.buckets[1].keyAsDatetime shouldBe LocalDate(2015, 1, 1)
                res.buckets[1].docCount shouldBe 0L
            }
        }
    }
}
