package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.ObjExpression
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.toMap
import dev.evo.elasticmagic.types.FieldType

@Suppress("UnnecessaryAbstractClass")
abstract class MetricAggregation<R : AggregationResult> : Aggregation<R>

abstract class SingleValueMetricAggResult<T> : AggregationResult {
    abstract val value: T
    abstract val valueAsString: String?
}

data class DoubleValueAggResult(
    override val value: Double,
    override val valueAsString: String? = null
) : SingleValueMetricAggResult<Double>() {
    companion object {
        operator fun invoke(obj: Deserializer.ObjectCtx): DoubleValueAggResult {
            return DoubleValueAggResult(
                obj.double("value"),
                obj.stringOrNull("value_as_string")
            )
        }
    }
}

data class OptionalDoubleValueAggResult(
    override val value: Double?,
    override val valueAsString: String? = null
) : SingleValueMetricAggResult<Double?>() {
    companion object {
        operator fun invoke(obj: Deserializer.ObjectCtx): OptionalDoubleValueAggResult {
            return OptionalDoubleValueAggResult(
                obj.doubleOrNull("value"),
                obj.stringOrNull("value_as_string")
            )
        }
    }
}

data class LongValueAggResult(
    override val value: Long,
    override val valueAsString: String? = null
) : SingleValueMetricAggResult<Long>() {
    companion object {
        operator fun invoke(obj: Deserializer.ObjectCtx): LongValueAggResult {
            return LongValueAggResult(
                obj.long("value"),
                obj.stringOrNull("value_as_string")
            )
        }
    }
}

abstract class NumericValueAgg<T, R : AggregationResult>(
    private val resultProcessor: (Deserializer.ObjectCtx) -> R
) : MetricAggregation<R>() {
    abstract val value: AggValue<T>
    abstract val missing: T?

    override fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
        compiler.visit(ctx, value)
        ctx.fieldIfNotNull("missing", missing)
    }

    override fun processResult(obj: Deserializer.ObjectCtx): R {
        return resultProcessor(obj)
    }
}

data class MinAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
    val format: String? = null,
) : NumericValueAgg<T, MinAggResult>(MinAggResult::invoke) {
    override val name = "min"

    constructor(
        field: FieldOperations<T>,
        missing: T? = null,
        format: String? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
        format = format,
    )

    override fun clone() = copy()
}

typealias MinAggResult = OptionalDoubleValueAggResult

data class MaxAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
    val format: String? = null,
) : NumericValueAgg<T, MaxAggResult>(MaxAggResult::invoke) {
    override val name = "max"

    constructor(
        field: FieldOperations<T>,
        missing: T? = null,
        format: String? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
        format = format,
    )

    override fun clone() = copy()
}


data class PercentileAggResult(val values: Map<Double, Double>) : AggregationResult {
    companion object {
        operator fun invoke(obj: Deserializer.ObjectCtx): PercentileAggResult {
            val values = obj.obj("values")
            return PercentileAggResult(
                values.toMap().filter { it.value is Double }.map { it.key.toDouble() to it.value as Double }.toMap()
            )
        }
    }
}

@Suppress("MagicNumber")
data class PercentilesAgg(
    val field: FieldOperations<*>,
    val percents: List<Double> = listOf(1.0, 5.0, 25.0, 50.0, 75.0, 95.0, 99.0),
) : MetricAggregation<PercentileAggResult>() {
    override fun processResult(obj: Deserializer.ObjectCtx): PercentileAggResult {
        return PercentileAggResult(obj)
    }

    override val name: String
        get() = "percentiles"

    override fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
        ctx.field("field", field.getQualifiedFieldName())
        ctx.array("percents") {
            percents.forEach {
                value(it)
            }
        }
    }

    override fun clone() = copy()

}

typealias MaxAggResult = OptionalDoubleValueAggResult

data class AvgAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
    val format: String? = null,
) : NumericValueAgg<T, AvgAggResult>(AvgAggResult::invoke) {
    override val name = "avg"

    constructor(
        field: FieldOperations<T>,
        missing: T? = null,
        format: String? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
        format = format,
    )

    override fun clone() = copy()
}

typealias AvgAggResult = OptionalDoubleValueAggResult

data class WeightedAvgAgg<T>(
    val value: ValueSource<T>,
    val weight: ValueSource<T>,
    val format: String? = null,
) : MetricAggregation<WeightedAvgAggResult>() {
    override val name = "weighted_avg"

    data class ValueSource<T>(
        val value: AggValue<T>,
        val script: Script? = null,
        val missing: T? = null,
    ) : ObjExpression {
        override fun clone() = copy()

        constructor(
            field: FieldOperations<T>,
            missing: T? = null,
        ) : this(
            AggValue.Field(field),
            missing = missing,
        )

        override fun accept(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
            compiler.visit(ctx, value)
            ctx.fieldIfNotNull("missing", missing)
        }
    }

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
        ctx.obj("value") {
            compiler.visit(this, value)
        }
        ctx.obj("weight") {
            compiler.visit(this, weight)
        }
        ctx.fieldIfNotNull("format", format)
    }

    override fun processResult(obj: Deserializer.ObjectCtx): WeightedAvgAggResult {
        return WeightedAvgAggResult(obj)
    }
}

typealias WeightedAvgAggResult = OptionalDoubleValueAggResult

data class SumAgg<T : Number>(
    override val value: AggValue<T>,
    override val missing: T? = null,
    val format: String? = null,
) : NumericValueAgg<T, SumAggResult>(SumAggResult::invoke) {
    override val name = "sum"

    constructor(
        field: FieldOperations<T>,
        missing: T? = null,
        format: String? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
        format = format,
    )

    override fun clone() = copy()
}

typealias SumAggResult = DoubleValueAggResult

data class MedianAbsoluteDeviationAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
) : NumericValueAgg<T, MedianAbsoluteDeviationAggResult>(MedianAbsoluteDeviationAggResult::invoke) {
    override val name = "median_absolute_deviation"

    constructor(
        field: FieldOperations<T>,
        missing: T? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
    )

    override fun clone() = copy()
}

typealias MedianAbsoluteDeviationAggResult = OptionalDoubleValueAggResult

data class ValueCountAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
) : NumericValueAgg<T, ValueCountAggResult>(ValueCountAggResult::invoke) {
    override val name = "value_count"

    constructor(
        field: FieldOperations<T>,
        missing: T? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
    )

    override fun clone() = copy()
}

typealias ValueCountAggResult = LongValueAggResult

data class CardinalityAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
) : NumericValueAgg<T, CardinalityAggResult>(CardinalityAggResult::invoke) {
    override val name = "cardinality"

    constructor(
        field: FieldOperations<T>,
        missing: T? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
    )

    override fun clone() = copy()
}

typealias CardinalityAggResult = LongValueAggResult

data class StatsAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
    val format: String? = null,
) : NumericValueAgg<T, StatsAggResult>(StatsAggResult::invoke) {
    override val name = "stats"

    constructor(
        field: FieldOperations<T>,
        missing: T? = null,
        format: String? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
        format = format,
    )

    override fun clone() = copy()
}

data class StatsAggResult(
    val count: Long,
    val min: Double?,
    val max: Double?,
    val avg: Double?,
    val sum: Double,
    val minAsString: String?,
    val maxAsString: String?,
    val avgAsString: String?,
    val sumAsString: String?,
) : AggregationResult {
    companion object {
        operator fun invoke(obj: Deserializer.ObjectCtx): StatsAggResult {
            return StatsAggResult(
                count = obj.long("count"),
                min = obj.doubleOrNull("min"),
                max = obj.doubleOrNull("max"),
                avg = obj.doubleOrNull("avg"),
                sum = obj.double("sum"),
                minAsString = obj.stringOrNull("min_as_string"),
                maxAsString = obj.stringOrNull("max_as_string"),
                avgAsString = obj.stringOrNull("avg_as_string"),
                sumAsString = obj.stringOrNull("sum_as_string"),
            )
        }
    }
}

data class ExtendedStatsAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
    val format: String? = null,
) : NumericValueAgg<T, ExtendedStatsAggResult>(ExtendedStatsAggResult::invoke) {
    override val name = "extended_stats"

    constructor(
        field: FieldOperations<T>,
        missing: T? = null,
        format: String? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
        format = format,
    )

    override fun clone() = copy()
}

data class ExtendedStatsAggResult(
    val count: Long,
    val min: Double?,
    val max: Double?,
    val avg: Double?,
    val sum: Double,
    val sumOfSquares: Double?,
    val variance: Double?,
    val stdDeviation: Double?,
    val stdDeviationBounds: StdDeviationBounds
) : AggregationResult {
    data class StdDeviationBounds(
        val upper: Double?,
        val lower: Double?,
    )

    companion object {
        operator fun invoke(obj: Deserializer.ObjectCtx): ExtendedStatsAggResult {
            val stdDevBoundsRaw = obj.obj("std_deviation_bounds")
            return ExtendedStatsAggResult(
                count = obj.long("count"),
                min = obj.doubleOrNull("min"),
                max = obj.doubleOrNull("max"),
                avg = obj.doubleOrNull("avg"),
                sum = obj.double("sum"),
                sumOfSquares = obj.doubleOrNull("sum_of_squares"),
                variance = obj.doubleOrNull("variance"),
                stdDeviation = obj.doubleOrNull("std_deviation"),
                stdDeviationBounds = StdDeviationBounds(
                    upper = stdDevBoundsRaw.doubleOrNull("upper"),
                    lower = stdDevBoundsRaw.doubleOrNull("lower"),
                )
            )
        }
    }
}

data class ScriptedMetricAgg<T>(
    val resultProcessor: (Deserializer.ObjectCtx) -> T,
    val initScript: Script? = null,
    val mapScript: Script,
    val combineScript: Script? = null,
    val reduceScript: Script? = null,
    val params: Params = Params(),
) : MetricAggregation<ScriptedMetricAggResult<T>>() {
    override val name = "scripted_metric"

    companion object {
        operator fun <T> invoke(
            valueType: FieldType<*, T>,
            initScript: Script? = null,
            mapScript: Script,
            combineScript: Script? = null,
            reduceScript: Script? = null,
            params: Params = Params(),
        ): ScriptedMetricAgg<T> {
            return ScriptedMetricAgg(
                { obj -> valueType.deserializeTerm(obj.any("value")) },
                initScript = initScript,
                mapScript = mapScript,
                combineScript = combineScript,
                reduceScript = reduceScript,
                params = params,
            )
        }
    }

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
        if (initScript != null) {
            ctx.obj("init_script") {
                compiler.visit(this, initScript)
            }
        }
        ctx.obj("map_script") {
            compiler.visit(this, mapScript)
        }
        if (combineScript != null) {
            ctx.obj("combine_script") {
                compiler.visit(this, combineScript)
            }
        }
        if (reduceScript != null) {
            ctx.obj("reduce_script") {
                compiler.visit(this, reduceScript)
            }
        }
        ctx.obj("params") {
            compiler.visit(this, params)
        }
    }

    override fun processResult(obj: Deserializer.ObjectCtx): ScriptedMetricAggResult<T> {
        return ScriptedMetricAggResult(resultProcessor(obj))
    }
}

data class ScriptedMetricAggResult<T>(
    val value: T
) : AggregationResult
