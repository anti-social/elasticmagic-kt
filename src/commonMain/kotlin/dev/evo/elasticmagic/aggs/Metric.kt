package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.query.Expression
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer

abstract class MetricAggregation<R: AggregationResult> : Aggregation<R>

data class SingleValueMetricAggResult<T>(
    val value: T?,
    val valueAsString: String? = null,
) : AggregationResult

abstract class NumericValueAgg<T, R: AggregationResult> : MetricAggregation<R>() {
    abstract val value: AggValue<T>
    abstract val missing: T?

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        compiler.visit(ctx, value)
        ctx.fieldIfNotNull("missing", missing)
    }
}

abstract class SingleDoubleValueAgg<T> : NumericValueAgg<T, SingleValueMetricAggResult<Double>>() {
    override fun processResult(obj: Deserializer.ObjectCtx): SingleValueMetricAggResult<Double> {
        return SingleValueMetricAggResult(
            obj.doubleOrNull("value"),
            obj.stringOrNull("value_as_string"),
        )
    }
}

abstract class SingleLongValueAgg<T> : NumericValueAgg<T, SingleValueMetricAggResult<Long>>() {
    override fun processResult(obj: Deserializer.ObjectCtx): SingleValueMetricAggResult<Long> {
        return SingleValueMetricAggResult(
            obj.long("value"),
            obj.stringOrNull("value_as_string"),
        )
    }
}

data class MinAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
    val format: String? = null,
) : SingleDoubleValueAgg<T>() {
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

data class MaxAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
    val format: String? = null,
) : SingleDoubleValueAgg<T>() {
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

data class AvgAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
    val format: String? = null,
) : SingleDoubleValueAgg<T>() {
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

data class WeightedAvgAgg<T>(
    val value: ValueSource<T>,
    val weight: ValueSource<T>,
    val format: String? = null,
) : MetricAggregation<SingleValueMetricAggResult<Double>>() {
    override val name = "weighted_avg"

    data class ValueSource<T>(
        val value: AggValue<T>,
        val script: Script? = null,
        val missing: T? = null,
    ) : Expression {
        override fun clone() = copy()

        constructor(
            field: FieldOperations<T>,
            missing: T? = null,
        ) : this(
            AggValue.Field(field),
            missing = missing,
        )

        override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
            compiler.visit(ctx, value)
            ctx.fieldIfNotNull("missing", missing)
        }
    }

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.obj("value") {
            compiler.visit(this, value)
        }
        ctx.obj("weight") {
            compiler.visit(this, weight)
        }
        ctx.fieldIfNotNull("format", format)
    }

    override fun processResult(obj: Deserializer.ObjectCtx): SingleValueMetricAggResult<Double> {
        return SingleValueMetricAggResult(
            obj.doubleOrNull("value"),
            obj.stringOrNull("value_as_string"),
        )
    }
}

data class SumAgg<T: Number>(
    override val value: AggValue<T>,
    override val missing: T? = null,
    val format: String? = null,
) : SingleDoubleValueAgg<T>() {
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

data class MedianAbsoluteDeviationAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
) : SingleDoubleValueAgg<T>() {
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

data class ValueCountAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
) : SingleLongValueAgg<T>() {
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

data class CardinalityAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
) : SingleLongValueAgg<T>() {
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

data class StatsAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
    val format: String? = null,
) : NumericValueAgg<T, StatsAggResult>() {
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

    override fun processResult(obj: Deserializer.ObjectCtx): StatsAggResult {
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
) : AggregationResult

data class ExtendedStatsAgg<T>(
    override val value: AggValue<T>,
    override val missing: T? = null,
    val format: String? = null,
) : NumericValueAgg<T, ExtendedStatsAggResult>() {
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

    override fun processResult(obj: Deserializer.ObjectCtx): ExtendedStatsAggResult {
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
            stdDeviationBounds = ExtendedStatsAggResult.StdDeviationBounds(
                upper = stdDevBoundsRaw.doubleOrNull("upper"),
                lower = stdDevBoundsRaw.doubleOrNull("lower"),
            )
        )
    }
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
}
