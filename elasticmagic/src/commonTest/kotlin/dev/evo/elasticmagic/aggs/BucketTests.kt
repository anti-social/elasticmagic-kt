package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.MatchAll
import dev.evo.elasticmagic.serde.DeserializationException

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

import kotlin.test.Test

class BucketTests : TestAggregation() {
    @Test
    fun global() {
        GlobalAgg().let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "global" to emptyMap<String, Any?>()
            )

            shouldThrow<DeserializationException> {
                process(agg, emptyMap())
            }
            process(agg, mapOf("doc_count" to 5)).let { res ->
                res.docCount shouldBe 5
                res.aggs shouldBe emptyMap()
            }
        }

        GlobalAgg(aggs = mapOf("avg_rating" to AvgAgg(MovieDoc.rating))).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "global" to emptyMap<String, Any?>(),
                "aggs" to mapOf(
                    "avg_rating" to mapOf(
                        "avg" to mapOf(
                            "field" to "rating"
                        )
                    )
                )
            )

            process(
                agg,
                mapOf(
                    "doc_count" to 5,
                    "avg_rating" to mapOf(
                        "value" to 43.83
                    )
                )
            ).let { res ->
                res.docCount shouldBe 5
                res.agg<AvgAggResult>("avg_rating").value shouldBe 43.83
            }
        }
    }

    @Test
    fun filter() {
        FilterAgg(MatchAll).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "filter" to mapOf(
                    "match_all" to emptyMap<String, Any?>()
                )
            )

            shouldThrow<DeserializationException> {
                process(agg, emptyMap())
            }
            process(agg, mapOf("doc_count" to 3)).let { res ->
                res.docCount shouldBe 3
                res.aggs shouldBe emptyMap()
            }
        }

        FilterAgg(MovieDoc.genre.eq("drama"), aggs = mapOf("avg_rating" to AvgAgg(MovieDoc.rating))).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "filter" to mapOf(
                    "term" to mapOf(
                        "genre" to "drama"
                    )
                ),
                "aggs" to mapOf(
                    "avg_rating" to mapOf(
                        "avg" to mapOf(
                            "field" to "rating"
                        )
                    )
                )
            )

            process(
                agg,
                mapOf(
                    "doc_count" to 3,
                    "avg_rating" to mapOf(
                        "value" to 28.39
                    )
                )
            ).let { res ->
                res.docCount shouldBe 3
                res.agg<AvgAggResult>("avg_rating").value shouldBe 28.39
            }
        }
    }

    @Test
    fun filters() {
        FiltersAgg(emptyMap()).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "filters" to mapOf(
                    "filters" to emptyMap<String, Any?>()
                )
            )

            shouldThrow<DeserializationException> {
                process(agg, emptyMap())
            }
            shouldThrow<DeserializationException> {
                process(agg, mapOf("buckets" to emptyList<Nothing>()))
            }
            process(agg, mapOf("buckets" to emptyMap<String, Nothing>())).let { res ->
                res.buckets shouldBe emptyMap()
            }
        }

        FiltersAgg(
            mapOf(
                "comedies" to Bool.must(
                    MovieDoc.genre.eq("comedy"),
                    MovieDoc.rating.gte(80F),
                ),
                "horrors" to Bool.must(
                    MovieDoc.genre.eq("horror"),
                    MovieDoc.rating.gte(90F),
                )
            ),
            otherBucketKey = "others",
        ).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "filters" to mapOf(
                    "other_bucket_key" to "others",
                    "filters" to mapOf(
                        "comedies" to mapOf(
                            "bool" to mapOf(
                                "must" to listOf(
                                    mapOf(
                                        "term" to mapOf("genre" to "comedy")
                                    ),
                                    mapOf(
                                        "range" to mapOf("rating" to mapOf("gte" to 80F))
                                    )
                                )
                            )
                        ),
                        "horrors" to mapOf(
                            "bool" to mapOf(
                                "must" to listOf(
                                    mapOf(
                                        "term" to mapOf("genre" to "horror")
                                    ),
                                    mapOf(
                                        "range" to mapOf("rating" to mapOf("gte" to 90F))
                                    )
                                )
                            )
                        ),
                    )
                )
            )

            process(
                agg,
                mapOf(
                    "buckets" to mapOf(
                        "comedies" to mapOf("doc_count" to 8),
                        "horrors" to mapOf("doc_count" to 13),
                        "others" to mapOf("doc_count" to 96),
                    )
                )
            ).let { res ->
                res.buckets.size shouldBe 3
                val comedies = res.bucket("comedies")
                comedies.key shouldBe "comedies"
                comedies.docCount shouldBe 8
                val horrors = res.bucket("horrors")
                horrors.key shouldBe "horrors"
                horrors.docCount shouldBe 13
                val others = res.bucket("others")
                others.key shouldBe "others"
                others.docCount shouldBe 96
                shouldThrow<IllegalStateException> {
                    res.bucket("unknown")
                }
            }
        }
    }
}
