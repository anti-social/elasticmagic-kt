package dev.evo.elasticmagic.aggs

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

import kotlin.test.Test

class RangeTests : TestAggregation() {
    @Test
    fun range() {
        RangeAgg(
            MovieDoc.rating,
            ranges = listOf(
                AggRange(null, 10.0),
                AggRange(10.0, 50.0),
                AggRange(50.0, null),
            )
        ).compile() shouldContainExactly mapOf(
            "range" to mapOf(
                "field" to "rating",
                "ranges" to listOf(
                    mapOf("to" to 10.0),
                    mapOf("from" to 10.0, "to" to 50.0),
                    mapOf("from" to 50.0),
                )
            )
        )

        val agg = RangeAgg(MovieDoc.rating, ranges = emptyList())
        shouldThrow<IllegalStateException> {
            process(agg, emptyMap())
        }
        process(
            agg,
            mapOf(
                "buckets" to listOf(
                    mapOf(
                        "key" to "*-10.0",
                        "to" to 10.0,
                        "doc_count" to 2,
                    ),
                    mapOf(
                        "key" to "10.0-50.0",
                        "from" to 10.0,
                        "to" to 50.0,
                        "doc_count" to 4,
                    ),
                    mapOf(
                        "key" to "50.0-*",
                        "from" to 50.0,
                        "doc_count" to 0,
                    ),
                )
            )
        ) shouldBe RangeAggResult(
            buckets = listOf(
                RangeBucket(
                    key = "*-10.0",
                    docCount = 2,
                    to = 10.0,
                ),
                RangeBucket(
                    key = "10.0-50.0",
                    docCount = 4,
                    from = 10.0,
                    to = 50.0,
                ),
                RangeBucket(
                    key = "50.0-*",
                    docCount = 0,
                    from = 50.0,
                ),
            )
        )
    }

    @Test
    fun dateRange() {
        DateRangeAgg(
            MovieDoc.releaseDate,
            ranges = listOf(
                AggRange.to("2010-01-01"),
                AggRange("2010-01-01", "2020-01-01"),
                AggRange.from("2020-01-01"),
            )
        ).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "date_range" to mapOf(
                    "field" to "release_date",
                    "ranges" to listOf(
                        mapOf("to" to "2010-01-01"),
                        mapOf("from" to "2010-01-01", "to" to "2020-01-01"),
                        mapOf("from" to "2020-01-01"),
                    )
                )
            )
        }

        DateRangeAgg(
            MovieDoc.releaseDate,
            ranges = listOf(
                AggRange.to("2000-01-01", key = "old"),
                AggRange("2000-01-01", "2022-01-01", key = "modern"),
                AggRange.from("2022-01-01", key = "future"),
            ),
            format = "YYYY-MM-dd",
            missing = "1970-01-01",
        ).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "date_range" to mapOf(
                    "field" to "release_date",
                    "ranges" to listOf(
                        mapOf("to" to "2000-01-01", "key" to "old"),
                        mapOf("from" to "2000-01-01", "to" to "2022-01-01", "key" to "modern"),
                        mapOf("from" to "2022-01-01", "key" to "future"),
                    ),
                    "format" to "YYYY-MM-dd",
                    "missing" to "1970-01-01",
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
                            "key" to "old",
                            "to" to 9.466848E11,
                            "to_as_string" to "2000-01-01",
                            "doc_count" to 100
                        ),
                        mapOf(
                            "key" to "modern",
                            "from" to 9.466848E11,
                            "from_as_string" to "2000-01-01",
                            "to" to 1.6409952E12,
                            "to_as_string" to "2022-01-01",
                            "doc_count" to 47
                        ),
                        mapOf(
                            "key" to "future",
                            "from" to 1.6409952E12,
                            "from_as_string" to "2022-01-01",
                            "doc_count" to 1
                        )
                    )
                )
            ).let { res ->
                res.buckets.shouldHaveSize(3)
                val old = res.buckets[0]
                old.key shouldBe "old"
                old.from.shouldBeNull()
                old.fromAsString.shouldBeNull()
                old.fromAsDatetime(TimeZone.UTC).shouldBeNull()
                old.to shouldBe 9.466848E11
                old.toAsString shouldBe "2000-01-01"
                old.toAsDatetime(TimeZone.UTC) shouldBe LocalDateTime(2000, 1, 1, 0, 0)
                old.docCount shouldBe 100L
                val modern = res.buckets[1]
                modern.key shouldBe "modern"
                modern.from shouldBe 9.466848E11
                modern.fromAsString shouldBe "2000-01-01"
                modern.fromAsDatetime(TimeZone.UTC) shouldBe LocalDateTime(2000, 1, 1, 0, 0)
                modern.to shouldBe 1.6409952E12
                modern.toAsString shouldBe "2022-01-01"
                modern.toAsDatetime(TimeZone.UTC) shouldBe LocalDateTime(2022, 1, 1, 0, 0)
                modern.docCount shouldBe 47L
                val future = res.buckets[2]
                future.key shouldBe "future"
                future.from shouldBe 1.6409952E12
                future.fromAsString shouldBe "2022-01-01"
                future.fromAsDatetime(TimeZone.UTC) shouldBe LocalDateTime(2022, 1, 1, 0, 0)
                future.to.shouldBeNull()
                future.toAsString.shouldBeNull()
                future.toAsDatetime(TimeZone.UTC).shouldBeNull()
                future.docCount shouldBe 1L
            }
        }
    }
}
