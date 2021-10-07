package dev.evo.elasticmagic

import dev.evo.elasticmagic.aggs.AggRange
import dev.evo.elasticmagic.aggs.AggValue
import dev.evo.elasticmagic.aggs.Aggregation
import dev.evo.elasticmagic.aggs.AggregationResult
import dev.evo.elasticmagic.aggs.AvgAgg
import dev.evo.elasticmagic.aggs.BaseTermsAgg
import dev.evo.elasticmagic.aggs.BucketsOrder
import dev.evo.elasticmagic.aggs.CardinalityAgg
import dev.evo.elasticmagic.aggs.DateHistogramAgg
import dev.evo.elasticmagic.aggs.DateRangeAgg
import dev.evo.elasticmagic.aggs.ExtendedStatsAgg
import dev.evo.elasticmagic.aggs.ExtendedStatsAggResult
import dev.evo.elasticmagic.aggs.FilterAgg
import dev.evo.elasticmagic.aggs.FiltersAgg
import dev.evo.elasticmagic.aggs.GlobalAgg
import dev.evo.elasticmagic.aggs.HistogramAgg
import dev.evo.elasticmagic.aggs.HistogramBounds
import dev.evo.elasticmagic.aggs.MaxAgg
import dev.evo.elasticmagic.aggs.MedianAbsoluteDeviationAgg
import dev.evo.elasticmagic.aggs.MinAgg
import dev.evo.elasticmagic.aggs.RangeAgg
import dev.evo.elasticmagic.aggs.RangeAggResult
import dev.evo.elasticmagic.aggs.RangeBucket
import dev.evo.elasticmagic.aggs.SignificantTermBucket
import dev.evo.elasticmagic.aggs.SignificantTermsAgg
import dev.evo.elasticmagic.aggs.SignificantTermsAggResult
import dev.evo.elasticmagic.aggs.SingleDoubleValueAgg
import dev.evo.elasticmagic.aggs.SingleLongValueAgg
import dev.evo.elasticmagic.aggs.SingleValueMetricAggResult
import dev.evo.elasticmagic.aggs.StatsAgg
import dev.evo.elasticmagic.aggs.SumAgg
import dev.evo.elasticmagic.aggs.TermBucket
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.aggs.ValueCountAgg
import dev.evo.elasticmagic.aggs.WeightedAvgAgg
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.platform
import dev.evo.elasticmagic.serde.Platform
import dev.evo.elasticmagic.serde.StdDeserializer
import dev.evo.elasticmagic.serde.StdSerializer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

import kotlin.test.Test

class AggregationTests {
    private val serializer = object : StdSerializer() {
        override fun objToString(obj: Map<String, Any?>): String {
            TODO("not implemented")
        }
    }
    private val deserializer = object : StdDeserializer() {
        override fun objFromStringOrNull(data: String): Deserializer.ObjectCtx? {
            TODO("not implemented")
        }

    }
    private val compiler = SearchQueryCompiler(
        ElasticsearchVersion(6, 0, 0),
    )

    private fun Expression.compile(): Map<String, *> {
        return serializer.buildObj {
            compiler.visit(this, this@compile)
        }
    }

    private fun <A: Aggregation<R>, R: AggregationResult> process(
        agg: A, rawResult: Map<String, Any?>
    ): R {
        return agg.processResult(deserializer.wrapObj(rawResult))
    }

    object MovieDoc : Document() {
        val genre by keyword()
        val rating by float()
        val numRatings by int("num_ratings")
        val releaseDate by date("release_date")
    }

    @Test
    fun min_max_avg_sum() {
        table<String, (FieldOperations, Any?) -> SingleDoubleValueAgg>(
            headers("aggName", "aggFn"),
            row("min", ::MinAgg),
            row("max", ::MaxAgg),
            row("avg", ::AvgAgg),
            row("sum", ::SumAgg),
            row("median_absolute_deviation", ::MedianAbsoluteDeviationAgg),
        ).forAll { aggName, aggFn ->
            val agg = aggFn(
                MovieDoc.rating,
                0.0,
            )
            agg.compile() shouldContainExactly mapOf(
                aggName to mapOf(
                    "field" to "rating",
                    "missing" to 0.0
                )
            )
            agg.processResult(
                deserializer.wrapObj(
                    mapOf(
                        "value" to null
                    )
                )
            ).let { res ->
                res.value.shouldBeNull()
                res.valueAsString.shouldBeNull()
            }
            agg.processResult(
                deserializer.wrapObj(
                    mapOf(
                        "value" to 1.1,
                        "value_as_string" to "1.1",
                    )
                )
            ).let { res ->
                res.value shouldBe 1.1
                res.valueAsString shouldBe "1.1"
            }
        }
    }

    @Test
    fun valueCount_cardinality() {
        table<String, (FieldOperations, Any?) -> SingleLongValueAgg>(
            headers("aggName", "aggFn"),
            row("value_count", ::ValueCountAgg),
            row("cardinality", ::CardinalityAgg),
        ).forAll { aggName, aggFn ->
            val agg = aggFn(
                MovieDoc.numRatings,
                0,
            )
            agg.compile() shouldContainExactly mapOf(
                aggName to mapOf(
                    "field" to "num_ratings",
                    "missing" to 0
                )
            )
            shouldThrow<IllegalStateException> {
                agg.processResult(
                    deserializer.wrapObj(
                        mapOf(
                            "value" to null
                        )
                    )
                )
            }
            agg.processResult(
                deserializer.wrapObj(
                    mapOf(
                        "value" to 1,
                        "value_as_string" to "1",
                    )
                )
            ).let { res ->
                res.value shouldBe 1
                res.valueAsString shouldBe "1"
            }
            if (platform == Platform.JS) {
                agg.processResult(
                    deserializer.wrapObj(
                        mapOf(
                            "value" to 1.1,
                        )
                    )
                ).let { res ->
                    res.value shouldBe 1L
                    res.valueAsString.shouldBeNull()
                }
            } else {
                shouldThrow<IllegalStateException> {
                    agg.processResult(
                        deserializer.wrapObj(
                            mapOf(
                                "value" to 1.1,
                            )
                        )
                    )
                }
            }
        }
    }

    @Test
    fun weightedAvg() {
        val agg = WeightedAvgAgg(
            value = WeightedAvgAgg.ValueSource(
                MovieDoc.rating,
                missing = 0.0,
            ),
            weight = WeightedAvgAgg.ValueSource(
                AggValue.Script(Script(
                    "doc[params.rating_field].value + 1",
                    params = mapOf(
                        "rating_field" to MovieDoc.numRatings
                    )
                )),
            ),
        )
        agg.compile() shouldContainExactly mapOf(
            "weighted_avg" to mapOf(
                "value" to mapOf(
                    "field" to "rating",
                    "missing" to 0.0,
                ),
                "weight" to mapOf(
                    "script" to mapOf(
                        "source" to "doc[params.rating_field].value + 1",
                        "params" to mapOf(
                            "rating_field" to "num_ratings"
                        )
                    )
                ),
            )
        )
        agg.processResult(
            deserializer.wrapObj(mapOf(
                "value" to null
            ))
        ).let { res ->
            res.value.shouldBeNull()
            res.valueAsString.shouldBeNull()
        }
        agg.processResult(
            deserializer.wrapObj(mapOf(
                "value" to 2.2,
                "value_as_string" to "2.2",
            ))
        ).let { res ->
            res.value shouldBe 2.2
            res.valueAsString shouldBe "2.2"
        }
        agg.processResult(
            deserializer.wrapObj(mapOf(
                "value" to 2,
                "value_as_string" to "2",
            ))
        ).let { res ->
            res.value shouldBe 2.0
            res.valueAsString shouldBe "2"
        }
        // TODO: Make deserialization strict
        // shouldThrow<IllegalStateException> {
        //     agg.processResult(
        //         deserializer.wrapObj(mapOf(
        //             "value" to "aa"
        //         ))
        //     )
        // }
    }

    @Test
    fun stats() {
        val agg = StatsAgg(
            MovieDoc.rating,
            missing = 0.0,
        )
        agg.compile() shouldContainExactly mapOf(
            "stats" to mapOf(
                "field" to "rating",
                "missing" to 0.0,
            )
        )
        shouldThrow<IllegalStateException> {
            process(agg, emptyMap())
        }
        process(agg, mapOf("count" to 0, "sum" to 0.0)).let { res ->
            res.count shouldBe 0
            res.min.shouldBeNull()
            res.max.shouldBeNull()
            res.avg.shouldBeNull()
            res.sum shouldBe 0.0
            res.minAsString.shouldBeNull()
            res.maxAsString.shouldBeNull()
            res.avgAsString.shouldBeNull()
            res.sumAsString.shouldBeNull()
        }
        process(
            agg,
            mapOf(
                "count" to 3,
                "min" to 0.9,
                "max" to 99.9,
                "avg" to 43.84,
                "sum" to 181,
                "min_as_string" to "0.9",
                "max_as_string" to "99.9",
                "avg_as_string" to "43.84",
                "sum_as_string" to "181",
            )
        ).let { res ->
            res.count shouldBe 3
            res.min shouldBe 0.9
            res.max shouldBe 99.9
            res.avg shouldBe 43.84
            res.sum shouldBe 181.0
            res.minAsString shouldBe "0.9"
            res.maxAsString shouldBe "99.9"
            res.avgAsString shouldBe "43.84"
            res.sumAsString shouldBe "181"
        }
    }

    @Test
    fun extendedstats() {
        val agg = ExtendedStatsAgg(
            MovieDoc.rating,
            missing = 0.0,
        )
        agg.compile() shouldContainExactly mapOf(
            "extended_stats" to mapOf(
                "field" to "rating",
                "missing" to 0.0,
            )
        )
        shouldThrow<IllegalStateException> {
            process(agg, emptyMap<String, Nothing>())
        }
        process(
            agg,
            mapOf(
                "count" to 0,
                "sum" to 0.0,
                "std_deviation_bounds" to emptyMap<String, Any?>()
            )
        ).let { res ->
            res.count shouldBe 0
            res.min.shouldBeNull()
            res.max.shouldBeNull()
            res.avg.shouldBeNull()
            res.sum shouldBe 0.0
            res.sumOfSquares.shouldBeNull()
            res.variance.shouldBeNull()
            res.stdDeviation.shouldBeNull()
            res.stdDeviationBounds shouldBe ExtendedStatsAggResult.StdDeviationBounds(
                upper = null,
                lower = null,
            )
        }
        process(
            agg,
            mapOf(
                "count" to 3,
                "min" to 0.9,
                "max" to 99.9,
                "avg" to 43.84,
                "sum" to 181,
                "sum_of_squares" to 10598.93,
                "variance" to 1377.162,
                "std_deviation" to 37.1101,
                "std_deviation_bounds" to mapOf(
                    "upper" to 128.78,
                    "lower" to -19.6536,
                )
            )
        ) shouldBe ExtendedStatsAggResult(
            count = 3,
            min = 0.9,
            max = 99.9,
            avg = 43.84,
            sum = 181.0,
            sumOfSquares = 10598.93,
            variance = 1377.162,
            stdDeviation = 37.1101,
            stdDeviationBounds = ExtendedStatsAggResult.StdDeviationBounds(
                upper = 128.78,
                lower = -19.6536,
            ),
        )
    }

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
        shouldThrow<IllegalStateException> {
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
            backgroundFilter = MovieDoc.rating.gte(10.0),
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
                        "rating" to mapOf("gte" to 10.0)
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
        shouldThrow<IllegalStateException> {
            process(agg, emptyMap())
        }
        process(
            agg,
            mapOf(
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
            buckets = listOf(SignificantTermBucket("comedy", 83, 832, 0.693F)),
        )
    }

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

    @Test
    fun histogram() {
        HistogramAgg(MovieDoc.rating, interval = 10.0).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "histogram" to mapOf(
                    "field" to "rating",
                    "interval" to 10.0,
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
            interval = 10.0,
            offset = 25.0,
            minDocCount = 5,
            missing = 0.0,
            order = listOf(
                BucketsOrder("_count", Sort.Order.DESC),
                BucketsOrder("stats.std_deviation", Sort.Order.ASC)
            ),
            extendedBounds = HistogramBounds(-10.0, 110.0),
            hardBounds = HistogramBounds(10.0, 90.0),
            aggs = mapOf(
                "stats" to ExtendedStatsAgg(MovieDoc.rating)
            )
        ).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "histogram" to mapOf(
                    "field" to "rating",
                    "interval" to 10.0,
                    "offset" to 25.0,
                    "min_doc_count" to 5L,
                    "missing" to 0.0,
                    "order" to listOf(
                        mapOf("_count" to "desc"),
                        mapOf("stats.std_deviation" to "asc")
                    ),
                    "extended_bounds" to mapOf(
                        "min" to -10.0,
                        "max" to 110.0,
                    ),
                    "hard_bounds" to mapOf(
                        "min" to 10.0,
                        "max" to 90.0,
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
            interval = DateHistogramAgg.Interval.Calendar("1y"),
            offset = "1m",
            minDocCount = 1,
            missing = "1970-01-01",
            format = "YYYY",
            order = listOf(BucketsOrder("_count", Sort.Order.DESC)),
            extendedBounds = HistogramBounds.to("2030-01-01"),
            hardBounds = HistogramBounds.from("2000-01-01"),
        ).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "date_histogram" to mapOf(
                    "field" to "release_date",
                    "calendar_interval" to "1y",
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
                res.buckets[0].docCount shouldBe 1L
                res.buckets[0].keyAsDatetime(TimeZone.UTC) shouldBe LocalDateTime(2014, 1, 1, 0, 1)
                res.buckets[1].key shouldBe 1420070460000L
                res.buckets[1].keyAsString shouldBe "2015"
                res.buckets[1].docCount shouldBe 0L
                res.buckets[1].keyAsDatetime(TimeZone.UTC) shouldBe LocalDateTime(2015, 1, 1, 0, 1)
            }
        }
    }

    @Test
    fun global() {
        GlobalAgg().let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "global" to emptyMap<String, Any?>()
            )

            shouldThrow<IllegalStateException> {
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
                res.agg<SingleValueMetricAggResult<Double>>("avg_rating").value shouldBe 43.83
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

            shouldThrow<IllegalStateException> {
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
                res.agg<SingleValueMetricAggResult<Double>>("avg_rating").value shouldBe 28.39
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

            shouldThrow<IllegalStateException> {
                process(agg, emptyMap())
            }
            shouldThrow<IllegalStateException> {
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
                    MovieDoc.rating.gte(80),
                ),
                "horrors" to Bool.must(
                    MovieDoc.genre.eq("horror"),
                    MovieDoc.rating.gte(90),
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
                                        "range" to mapOf("rating" to mapOf("gte" to 80))
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
                                        "range" to mapOf("rating" to mapOf("gte" to 90))
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
