package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.types.FloatType
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.serde.platform
import dev.evo.elasticmagic.serde.Platform
import dev.evo.elasticmagic.serde.DeserializationException

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

import kotlin.test.Test

class MetricTests : TestAggregation() {
    @Test
    fun min_max_avg() {
        table<String, (FieldOperations<Float>, Float?) -> NumericValueAgg<Float, OptionalDoubleValueAggResult>>(
            headers("aggName", "aggFn"),
            row("min", ::MinAgg),
            row("max", ::MaxAgg),
            row("avg", ::AvgAgg),
            // row("sum", ::SumAgg),
            row("median_absolute_deviation", ::MedianAbsoluteDeviationAgg),
        ).forAll { aggName, aggFn ->
            val agg = aggFn(
                MovieDoc.rating,
                0.0F,
            )
            agg.compile() shouldContainExactly mapOf(
                aggName to mapOf(
                    "field" to "rating",
                    "missing" to 0.0F
                )
            )
            process(
                agg,
                mapOf("value" to null)
            ).let { res ->
                res.value.shouldBeNull()
                res.valueAsString.shouldBeNull()
            }
            process(
                agg,
                mapOf(
                    "value" to 1.1,
                    "value_as_string" to "1.1",
                )
            ).let { res ->
                res.value shouldBe 1.1
                res.valueAsString shouldBe "1.1"
            }
        }
    }

    @Test
    fun sum() {
        val agg = SumAgg(MovieDoc.rating, 0.0F)
        agg.compile() shouldContainExactly mapOf(
            "sum" to mapOf(
                "field" to "rating",
                "missing" to 0.0F
            )
        )
        shouldThrow<DeserializationException> {
            process(
                agg,
                mapOf("value" to null)
            )
        }
        process(
            agg,
            mapOf(
                "value" to 1.1,
                "value_as_string" to "1.1",
            )
        ).let { res ->
            res.value shouldBe 1.1
            res.valueAsString shouldBe "1.1"
        }
    }

    @Test
    fun min_booleanType() {
        MinAgg(MovieDoc.isColored, missing = true).let { agg ->
            agg.compile() shouldContainExactly mapOf(
                "min" to mapOf(
                    "field" to "is_colored",
                    "missing" to true
                )
            )
            agg.processResult(
                deserializer.wrapObj(
                    mapOf(
                        "value" to 0.0,
                        "value_as_string" to "false",
                    )
                )
            ).let { res ->
                res.value shouldBe 0.0
                res.valueAsString shouldBe "false"
            }
        }
    }

    @Test
    fun valueCount_cardinality() {
        table<String, (FieldOperations<Int>, Int?) -> NumericValueAgg<Int, LongValueAggResult>>(
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
            shouldThrow<DeserializationException> {
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
                shouldThrow<DeserializationException> {
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
                missing = 0.0F,
            ),
            weight = WeightedAvgAgg.ValueSource(
                AggValue.Script(
                    Script.Source(
                        "doc[params.rating_field].value + 1",
                        params = mapOf(
                            "rating_field" to MovieDoc.numRatings
                        )
                    ),
                    FloatType,
                ),
            ),
        )
        agg.compile() shouldContainExactly mapOf(
            "weighted_avg" to mapOf(
                "value" to mapOf(
                    "field" to "rating",
                    "missing" to 0.0F,
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
            missing = 0.0F,
        )
        agg.compile() shouldContainExactly mapOf(
            "stats" to mapOf(
                "field" to "rating",
                "missing" to 0.0F,
            )
        )
        shouldThrow<DeserializationException> {
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
    fun extendedStats() {
        val agg = ExtendedStatsAgg(
            MovieDoc.rating,
            missing = 0.0F,
        )
        agg.compile() shouldContainExactly mapOf(
            "extended_stats" to mapOf(
                "field" to "rating",
                "missing" to 0.0F,
            )
        )
        shouldThrow<DeserializationException> {
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
}
