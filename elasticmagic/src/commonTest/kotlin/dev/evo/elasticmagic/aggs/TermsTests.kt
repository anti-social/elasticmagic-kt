package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.query.Sort
import dev.evo.elasticmagic.serde.DeserializationException
import dev.evo.elasticmagic.types.IntType

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

import kotlin.test.Test

class TermsTests : TestAggregation() {
    @Test
    fun terms() {
        TermsAgg(MovieDoc.genre).compile() shouldContainExactly mapOf(
            "terms" to mapOf(
                "field" to "genre"
            )
        )
        TermsAgg(
            MovieDoc.genre,
            size = 100,
            shardSize = 1000,
            minDocCount = 5,
            include = BaseTermsAgg.Include.Values("comedy", "horror"),
            exclude = BaseTermsAgg.Exclude.Regex("test_*"),
            missing = "unknown",
            order = listOf(BucketsOrder("_key", Sort.Order.ASC)),
            collectMode = BaseTermsAgg.CollectMode.BREADTH_FIRST,
            executionHint = BaseTermsAgg.ExecutionHint.GLOBAL_ORDINALS,
            showTermDocCountError = true,
            params = mapOf(
                "unknown_parameter" to "test",
            ),
            aggs = mapOf(
                "avg_rating" to AvgAgg(MovieDoc.rating),
            )
        ).compile() shouldContainExactly mapOf(
            "terms" to mapOf(
                "field" to "genre",
                "size" to 100,
                "shard_size" to 1000,
                "min_doc_count" to 5,
                "include" to listOf("comedy", "horror"),
                "exclude" to "test_*",
                "missing" to "unknown",
                "order" to mapOf("_key" to "asc"),
                "collect_mode" to "breadth_first",
                "execution_hint" to "global_ordinals",
                "show_term_doc_count_error" to true,
                "unknown_parameter" to "test",
            ),
            "aggs" to mapOf(
                "avg_rating" to mapOf(
                    "avg" to mapOf(
                        "field" to "rating",
                    )
                )
            )
        )

        val agg = TermsAgg(MovieDoc.genre)
        shouldThrow<DeserializationException> {
            process(agg, emptyMap())
        }
        process(
            agg,
            mapOf(
                "doc_count_error_upper_bound" to 0,
                "sum_other_doc_count" to 0,
                "buckets" to listOf(
                    mapOf(
                        "key" to "comedy",
                        "doc_count" to 83,
                    )
                )
            )
        ) shouldBe TermsAggResult(
            buckets = listOf(TermBucket("comedy", 83)),
            docCountErrorUpperBound = 0,
            sumOtherDocCount = 0,
        )
    }

    @Test
    fun terms_withScript() {
        TermsAgg(
            AggValue.Script(
                Script.Source(
                    "(int) doc[params.field].value",
                    params = mapOf(
                        "field" to MovieDoc.rating
                    )
                ),
                IntType
            ),
            showTermDocCountError = true,
        ).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "terms" to mapOf(
                    "script" to mapOf(
                        "source" to "(int) doc[params.field].value",
                        "params" to mapOf(
                            "field" to "rating"
                        )
                    ),
                    "show_term_doc_count_error" to true,
                )
            )

            process(
                agg,
                mapOf(
                    "doc_count_error_upper_bound" to 0,
                    "sum_other_doc_count" to 0,
                    "buckets" to listOf(
                        mapOf(
                            "key" to 7,
                            "doc_count" to 2,
                            "doc_count_error_upper_bound" to 1,
                        ),
                        mapOf(
                            "key" to 8,
                            "doc_count" to 43,
                            "doc_count_error_upper_bound" to 5,
                        ),
                        mapOf(
                            "key" to 9,
                            "doc_count" to 32,
                            "doc_count_error_upper_bound" to 2,
                        ),
                    )
                )
            ) shouldBe TermsAggResult(
                buckets = listOf(
                    TermBucket(7, 2, docCountErrorUpperBound = 1),
                    TermBucket(8, 43, docCountErrorUpperBound = 5),
                    TermBucket(9, 32, docCountErrorUpperBound = 2),
                ),
                docCountErrorUpperBound = 0,
                sumOtherDocCount = 0,
            )
        }
    }

    @Test
    fun terms_withValueScript() {
        TermsAgg(
            AggValue.ValueScript(
                MovieDoc.rating,
                Script.Source(
                    "(int) _value",
                ),
                IntType
            )
        ).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "terms" to mapOf(
                    "field" to "rating",
                    "script" to mapOf(
                        "source" to "(int) _value",
                    ),
                )
            )
        }
    }

    @Test
    fun significantTerms() {
        SignificantTermsAgg(MovieDoc.genre).compile() shouldContainExactly mapOf(
            "significant_terms" to mapOf(
                "field" to "genre"
            )
        )
        SignificantTermsAgg(
            MovieDoc.genre,
            size = 100,
            shardSize = 1000,
            minDocCount = 5,
            include = BaseTermsAgg.Include.Values("comedy", "horror"),
            exclude = BaseTermsAgg.Exclude.Regex("test_*"),
            missing = "unknown",
            order = listOf(BucketsOrder("_key", Sort.Order.ASC)),
            collectMode = BaseTermsAgg.CollectMode.BREADTH_FIRST,
            executionHint = BaseTermsAgg.ExecutionHint.GLOBAL_ORDINALS,
            backgroundFilter = MovieDoc.rating.gte(10.0F),
            params = mapOf(
                "unknown_parameter" to "test",
            ),
            aggs = mapOf(
                "avg_rating" to AvgAgg(MovieDoc.rating),
            )
        ).compile() shouldContainExactly mapOf(
            "significant_terms" to mapOf(
                "field" to "genre",
                "size" to 100,
                "shard_size" to 1000,
                "min_doc_count" to 5,
                "include" to listOf("comedy", "horror"),
                "exclude" to "test_*",
                "missing" to "unknown",
                "order" to mapOf("_key" to "asc"),
                "collect_mode" to "breadth_first",
                "execution_hint" to "global_ordinals",
                "background_filter" to mapOf(
                    "range" to mapOf(
                        "rating" to mapOf("gte" to 10.0F)
                    )
                ),
                "unknown_parameter" to "test",
            ),
            "aggs" to mapOf(
                "avg_rating" to mapOf(
                    "avg" to mapOf(
                        "field" to "rating",
                    )
                )
            )
        )

        val agg = SignificantTermsAgg(MovieDoc.genre)
        shouldThrow<DeserializationException> {
            process(agg, emptyMap())
        }
        process(
            agg,
            mapOf(
                "doc_count" to 158,
                "bg_count" to 2097,
                "buckets" to listOf(
                    mapOf(
                        "key" to "comedy",
                        "doc_count" to 83,
                        "bg_count" to 832,
                        "score" to 0.693,
                    )
                )
            )
        ) shouldBe SignificantTermsAggResult(
            docCount = 158,
            bgCount = 2097,
            buckets = listOf(SignificantTermBucket("comedy", 83, 832, 0.693F)),
        )
    }
}
