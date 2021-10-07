package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.Expression
import dev.evo.elasticmagic.FieldOperations
import dev.evo.elasticmagic.NamedExpression
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.QueryExpression
import dev.evo.elasticmagic.Script
import dev.evo.elasticmagic.Sort
import dev.evo.elasticmagic.ToValue
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

sealed class AggValue : Expression {
    data class Field(val field: FieldOperations) : AggValue() {
        override fun clone() = copy()
    }
    data class Script(val script: dev.evo.elasticmagic.Script) : AggValue() {
        override fun clone() = copy()
    }
    data class ValueScript(
        val field: FieldOperations,
        val script: dev.evo.elasticmagic.Script,
    ) : AggValue() {
        override fun clone() = copy()
    }

    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        when (this) {
            is Field -> ctx.field("field", field.getQualifiedFieldName())
            is Script -> ctx.obj("script") {
                compiler.visit(this, script)
            }
            is ValueScript -> {
                ctx.field("field", field.getQualifiedFieldName())
                ctx.obj("script") {
                    compiler.visit(this, script)
                }
            }
        }
    }
}

interface Aggregation<R: AggregationResult> : NamedExpression {
    fun processResult(obj: Deserializer.ObjectCtx): R
}

abstract class MetricAggregation<R: AggregationResult> : Aggregation<R>

abstract class BucketAggregation<R: AggregationResult> : Aggregation<R> {
    abstract val aggs: Map<String, Aggregation<*>>

    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        super.accept(ctx, compiler)

        if (aggs.isNotEmpty()) {
            ctx.obj("aggs") {
                compiler.visit(this, aggs)
            }
        }
    }

    protected fun processSubAggs(bucketObj: Deserializer.ObjectCtx): Map<String, AggregationResult> {
        val subAggs = mutableMapOf<String, AggregationResult>()
        for ((aggName, agg) in aggs) {
            subAggs[aggName] = agg.processResult(bucketObj.obj(aggName))
        }
        return subAggs
    }
}

abstract class SingleBucketAggregation : BucketAggregation<SingleBucketAggResult>() {
    override fun processResult(obj: Deserializer.ObjectCtx): SingleBucketAggResult {
        return SingleBucketAggResult(
            docCount = obj.long("doc_count"),
            aggs = processSubAggs(obj)
        )
    }
}

interface AggregationResult

abstract class BucketAggResult<B: BaseBucket> : AggregationResult {
    abstract val buckets: List<B>
}

abstract class BaseBucket {
    abstract val docCount: Long
    abstract val aggs: Map<String, AggregationResult>

    inline fun <reified A: AggregationResult> agg(name: String): A {
        return (aggs[name] ?: throw IllegalStateException("Unknown aggregation: [$name]")) as A
    }
}

data class SingleBucketAggResult(
    override val docCount: Long,
    override val aggs: Map<String, AggregationResult>,
) : BaseBucket(), AggregationResult

abstract class KeyedBucket<K> : BaseBucket() {
    abstract val key: K
}

data class SingleValueMetricAggResult<T>(
    val value: T?,
    val valueAsString: String? = null,
) : AggregationResult

abstract class NumericValueAgg<R: AggregationResult> : MetricAggregation<R>() {
    abstract val value: AggValue
    abstract val missing: Any?

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        compiler.visit(ctx, value)
        ctx.fieldIfNotNull("missing", missing)
    }
}

abstract class SingleDoubleValueAgg : NumericValueAgg<SingleValueMetricAggResult<Double>>() {
    override fun processResult(obj: Deserializer.ObjectCtx): SingleValueMetricAggResult<Double> {
        return SingleValueMetricAggResult(
            obj.doubleOrNull("value"),
            obj.stringOrNull("value_as_string"),
        )
    }
}

abstract class SingleLongValueAgg : NumericValueAgg<SingleValueMetricAggResult<Long>>() {
    override fun processResult(obj: Deserializer.ObjectCtx): SingleValueMetricAggResult<Long> {
        return SingleValueMetricAggResult(
            obj.long("value"),
            obj.stringOrNull("value_as_string"),
        )
    }
}

data class MinAgg(
    override val value: AggValue,
    override val missing: Any? = null,
    val format: String? = null,
) : SingleDoubleValueAgg() {
    override val name = "min"

    constructor(
        field: FieldOperations,
        missing: Any? = null,
        format: String? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
        format = format,
    )

    override fun clone() = copy()
}

data class MaxAgg(
    override val value: AggValue,
    override val missing: Any? = null,
    val format: String? = null,
) : SingleDoubleValueAgg() {
    override val name = "max"

    constructor(
        field: FieldOperations,
        missing: Any? = null,
        format: String? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
        format = format,
    )

    override fun clone() = copy()
}

data class AvgAgg(
    override val value: AggValue,
    override val missing: Any? = null,
    val format: String? = null,
) : SingleDoubleValueAgg() {
    override val name = "avg"

    constructor(
        field: FieldOperations,
        missing: Any? = null,
        format: String? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
        format = format,
    )

    override fun clone() = copy()
}

data class WeightedAvgAgg(
    val value: ValueSource,
    val weight: ValueSource,
    val format: String? = null,
) : MetricAggregation<SingleValueMetricAggResult<Double>>() {
    override val name = "weighted_avg"

    data class ValueSource(
        val value: AggValue,
        val script: Script? = null,
        val missing: Any? = null,
    ) : Expression {
        override fun clone() = copy()

        constructor(
            field: FieldOperations,
            missing: Any? = null,
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

data class SumAgg(
    override val value: AggValue,
    override val missing: Any? = null,
    val format: String? = null,
) : SingleDoubleValueAgg() {
    override val name = "sum"

    constructor(
        field: FieldOperations,
        missing: Any? = null,
        format: String? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
        format = format,
    )

    override fun clone() = copy()
}

data class MedianAbsoluteDeviationAgg(
    override val value: AggValue,
    override val missing: Any? = null,
) : SingleDoubleValueAgg() {
    override val name = "median_absolute_deviation"

    constructor(
        field: FieldOperations,
        missing: Any? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
    )

    override fun clone() = copy()
}

data class ValueCountAgg(
    override val value: AggValue,
    override val missing: Any? = null,
) : SingleLongValueAgg() {
    override val name = "value_count"

    constructor(
        field: FieldOperations,
        missing: Any? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
    )

    override fun clone() = copy()
}

data class CardinalityAgg(
    override val value: AggValue,
    override val missing: Any? = null,
) : SingleLongValueAgg() {
    override val name = "cardinality"

    constructor(
        field: FieldOperations,
        missing: Any? = null,
    ) : this(
        AggValue.Field(field),
        missing = missing,
    )

    override fun clone() = copy()
}

data class StatsAgg(
    override val value: AggValue,
    override val missing: Any? = null,
    val format: String? = null,
) : NumericValueAgg<StatsAggResult>() {
    override val name = "stats"

    constructor(
        field: FieldOperations,
        missing: Any? = null,
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

data class ExtendedStatsAgg(
    override val value: AggValue,
    override val missing: Any? = null,
    val format: String? = null,
) : NumericValueAgg<ExtendedStatsAggResult>() {
    override val name = "extended_stats"

    constructor(
        field: FieldOperations,
        missing: Any? = null,
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

data class BucketsOrder(
    val key: String,
    val order: Sort.Order,
) : Expression {
    override fun clone() = copy()

    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.field(key, order.toValue())
    }
}

abstract class BaseTermsAgg<R: AggregationResult> : BucketAggregation<R>() {
    abstract val value: AggValue
    abstract val size: Int?
    abstract val shardSize: Int?
    abstract val minDocCount: Int?
    abstract val shardMinDocCount: Int?
    abstract val include: Include?
    abstract val exclude: Exclude?
    abstract val missing: Any?
    abstract val order: List<BucketsOrder>
    abstract val collectMode: CollectMode?
    abstract val executionHint: ExecutionHint?
    abstract val params: Params

    sealed class Exclude {
        class Values(vararg val values: Any) : Exclude()
        class Regex(val regex: String) : Exclude()
    }

    sealed class Include {
        class Values(vararg val values: Any) : Include()
        class Regex(val regex: String) : Include()
        class Partition(val partition: Int, val numPartitions: Int) : Include()
    }

    enum class CollectMode : ToValue {
        BREADTH_FIRST, DEPTH_FIRST;

        override fun toValue() = name.lowercase()
    }

    enum class ExecutionHint : ToValue {
        MAP, GLOBAL_ORDINALS;

        override fun toValue() = name.lowercase()
    }

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        compiler.visit(ctx, value)
        ctx.fieldIfNotNull("size", size)
        ctx.fieldIfNotNull("shard_size", shardSize)
        ctx.fieldIfNotNull("min_doc_count", minDocCount)
        ctx.fieldIfNotNull("shard_min_doc_count", shardMinDocCount)
        include?.let { include ->
            when (include) {
                is Include.Values -> ctx.array("include") {
                    compiler.visit(this, include.values)
                }
                is Include.Regex -> ctx.field("include", include.regex)
                is Include.Partition -> ctx.obj("include") {
                    field("partition", include.partition)
                    field("num_partitions", include.numPartitions)
                }
            }
        }
        exclude?.let { exclude ->
            when (exclude) {
                is Exclude.Values -> ctx.array("exclude") {
                    compiler.visit(this, exclude.values)
                }
                is Exclude.Regex -> ctx.field("exclude", exclude.regex)
            }
        }
        ctx.fieldIfNotNull("missing", missing)
        when (order.size) {
            1 -> ctx.obj("order") {
                compiler.visit(this, order[0])
            }
            in 2..Int.MAX_VALUE -> ctx.array("order") {
                compiler.visit(this, order)
            }
            else -> {}
        }
        ctx.fieldIfNotNull("collect_mode", collectMode?.toValue())
        ctx.fieldIfNotNull("execution_hint", executionHint?.toValue())
        if (params.isNotEmpty()) {
            compiler.visit(ctx, params)
        }
    }
}

data class TermsAgg(
    override val value: AggValue,
    override val size: Int? = null,
    override val shardSize: Int? = null,
    override val minDocCount: Int? = null,
    override val shardMinDocCount: Int? = null,
    override val include: Include? = null,
    override val exclude: Exclude? = null,
    override val missing: Any? = null,
    override val order: List<BucketsOrder> = emptyList(),
    override val collectMode: CollectMode? = null,
    override val executionHint: ExecutionHint? = null,
    val showTermDocCountError: Boolean? = null,
    override val params: Params = Params(),
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : BaseTermsAgg<TermsAggResult>() {
    override val name = "terms"

    constructor(
        field: FieldOperations,
        size: Int? = null,
        shardSize: Int? = null,
        minDocCount: Int? = null,
        shardMinDocCount: Int? = null,
        include: Include? = null,
        exclude: Exclude? = null,
        missing: Any? = null,
        order: List<BucketsOrder> = emptyList(),
        collectMode: CollectMode? = null,
        executionHint: ExecutionHint? = null,
        showTermDocCountError: Boolean? = null,
        params: Params = Params(),
        aggs: Map<String, Aggregation<*>> = emptyMap(),
    ) : this(
        AggValue.Field(field),
        size = size,
        shardSize = shardSize,
        minDocCount = minDocCount,
        shardMinDocCount = shardMinDocCount,
        include = include,
        exclude = exclude,
        missing = missing,
        order = order,
        collectMode = collectMode,
        executionHint = executionHint,
        showTermDocCountError = showTermDocCountError,
        params = params,
        aggs = aggs,
    )

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.fieldIfNotNull("show_term_doc_count_error", showTermDocCountError)
        super.visit(ctx, compiler)
    }

    override fun processResult(obj: Deserializer.ObjectCtx): TermsAggResult {
        val buckets = mutableListOf<TermBucket>()
        val rawBuckets = obj.array("buckets")
        while (rawBuckets.hasNext()) {
            val rawBucket = rawBuckets.obj()
            buckets.add(
                TermBucket(
                    key = rawBucket.any("key"),
                    docCount = rawBucket.long("doc_count"),
                    docCountErrorUpperBound = rawBucket.longOrNull("doc_count_error_upper_bound"),
                    aggs = processSubAggs(rawBucket)
                )
            )

        }
        return TermsAggResult(
            buckets,
            docCountErrorUpperBound = obj.long("doc_count_error_upper_bound"),
            sumOtherDocCount = obj.long("sum_other_doc_count"),
        )
    }
}

data class TermsAggResult(
    override val buckets: List<TermBucket>,
    val docCountErrorUpperBound: Long,
    val sumOtherDocCount: Long,
) : BucketAggResult<TermBucket>()

data class TermBucket(
    override val key: Any,
    override val docCount: Long,
    val docCountErrorUpperBound: Long? = null,
    override val aggs: Map<String, AggregationResult> = emptyMap(),
) : KeyedBucket<Any>()

data class SignificantTermsAgg(
    override val value: AggValue,
    override val size: Int? = null,
    override val shardSize: Int? = null,
    override val minDocCount: Int? = null,
    override val shardMinDocCount: Int? = null,
    override val include: Include? = null,
    override val exclude: Exclude? = null,
    override val missing: Any? = null,
    override val order: List<BucketsOrder> = emptyList(),
    override val collectMode: CollectMode? = null,
    override val executionHint: ExecutionHint? = null,
    val backgroundFilter: QueryExpression? = null,
    override val params: Params = Params(),
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : BaseTermsAgg<SignificantTermsAggResult>() {
    override val name = "significant_terms"

    constructor(
        field: FieldOperations,
        size: Int? = null,
        shardSize: Int? = null,
        minDocCount: Int? = null,
        shardMinDocCount: Int? = null,
        include: Include? = null,
        exclude: Exclude? = null,
        missing: Any? = null,
        order: List<BucketsOrder> = emptyList(),
        collectMode: CollectMode? = null,
        executionHint: ExecutionHint? = null,
        backgroundFilter: QueryExpression? = null,
        params: Params = Params(),
        aggs: Map<String, Aggregation<*>> = emptyMap(),
    ) : this(
        AggValue.Field(field),
        size = size,
        shardSize = shardSize,
        minDocCount = minDocCount,
        shardMinDocCount = shardMinDocCount,
        include = include,
        exclude = exclude,
        missing = missing,
        order = order,
        collectMode = collectMode,
        executionHint = executionHint,
        backgroundFilter = backgroundFilter,
        params = params,
        aggs = aggs,
    )

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        if (backgroundFilter != null) {
            ctx.obj("background_filter") {
                compiler.visit(this, backgroundFilter)
            }
        }
        super.visit(ctx, compiler)
    }

    override fun processResult(obj: Deserializer.ObjectCtx): SignificantTermsAggResult {
        val buckets = mutableListOf<SignificantTermBucket>()
        val rawBuckets = obj.array("buckets")
        while (rawBuckets.hasNext()) {
            val rawBucket = rawBuckets.obj()
            buckets.add(
                SignificantTermBucket(
                    key = rawBucket.any("key"),
                    docCount = rawBucket.long("doc_count"),
                    bgCount = rawBucket.long("bg_count"),
                    score = rawBucket.float("score"),
                    docCountErrorUpperBound = rawBucket.longOrNull("doc_count_error_upper_bound"),
                    aggs = processSubAggs(rawBucket)
                )
            )

        }
        return SignificantTermsAggResult(buckets)
    }
}

data class SignificantTermsAggResult(
    override val buckets: List<SignificantTermBucket>,
) : BucketAggResult<SignificantTermBucket>()

data class SignificantTermBucket(
    override val key: Any,
    override val docCount: Long,
    val bgCount: Long,
    val score: Float,
    val docCountErrorUpperBound: Long? = null,
    override val aggs: Map<String, AggregationResult> = emptyMap(),
) : KeyedBucket<Any>()

data class AggRange<T>(
    val from: T? = null,
    val to: T? = null,
    val key: String? = null,
) {
    companion object {
        fun <T> from(from: T, key: String? = null) = AggRange(from, null, key)
        fun <T> to(to: T, key: String? = null) = AggRange(null, to, key)
    }
}

abstract class BaseRangeAgg<T, R: AggregationResult, B> : BucketAggregation<R>() {
    abstract val value: AggValue
    abstract val ranges: List<AggRange<T>>
    abstract val format: String?
    abstract val missing: T?
    // TODO: Keyed response
    // abstract val keyed: Boolean?
    abstract val params: Params

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        compiler.visit(ctx, value)
        ctx.array("ranges") {
            for ((from, to, key) in ranges) {
                obj {
                    fieldIfNotNull("from", from)
                    fieldIfNotNull("to", to)
                    fieldIfNotNull("key", key)
                }
            }
        }
        ctx.fieldIfNotNull("format", format)
        ctx.fieldIfNotNull("missing", missing)
        // ctx.fieldIfNotNull("keyed", keyed)
        if (params.isNotEmpty()) {
            compiler.visit(ctx, params)
        }
    }

    override fun processResult(obj: Deserializer.ObjectCtx): R {
        val bucketsArray = obj.arrayOrNull("buckets")
        if (bucketsArray != null) {
            val buckets = mutableListOf<B>()
            while (bucketsArray.hasNext()) {
                buckets.add(
                    processBucketResult(bucketsArray.obj())
                )
            }
            return makeRangeResult(buckets)
        }

        val bucketsObj = obj.obj("buckets")
        val buckets = mutableListOf<B>()
        val bucketsIter = bucketsObj.iterator()
        while (bucketsIter.hasNext()) {
            val (bucketKey, bucketObj) = bucketsIter.obj()
            buckets.add(
                processBucketResult(bucketObj, bucketKey)
            )
        }
        return makeRangeResult(buckets)
    }

    protected abstract fun makeRangeResult(buckets: List<B>): R

    protected abstract fun processBucketResult(
        bucketObj: Deserializer.ObjectCtx,
        bucketKey: String? = null,
    ): B
}

data class RangeAggResult(
    override val buckets: List<RangeBucket>,
) : BucketAggResult<RangeBucket>()

data class RangeBucket(
    override val key: String,
    override val docCount: Long,
    val from: Double? = null,
    val fromAsString: String? = null,
    val to: Double? = null,
    val toAsString: String? = null,
    override val aggs: Map<String, AggregationResult> = emptyMap(),
) : KeyedBucket<String>()

data class RangeAgg(
    override val value: AggValue,
    override val ranges: List<AggRange<Double>>,
    override val format: String? = null,
    override val missing: Double? = null,
    override val params: Params = Params(),
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : BaseRangeAgg<Double, RangeAggResult, RangeBucket>() {
    override val name = "range"

    constructor(
        field: FieldOperations,
        ranges: List<AggRange<Double>>,
        format: String? = null,
        missing: Double? = null,
        params: Params = Params(),
        aggs: Map<String, Aggregation<*>> = emptyMap(),
    ) : this(
        AggValue.Field(field),
        ranges = ranges,
        format = format,
        missing = missing,
        params = params,
        aggs = aggs,
    )

    companion object {
        fun simpleRanges(
            field: FieldOperations,
            ranges: List<Pair<Double?, Double?>>,
            format: String? = null,
            missing: Double? = null,
            aggs: Map<String, Aggregation<*>> = emptyMap(),
            params: Params = Params(),
        ): RangeAgg {
            return RangeAgg(
                AggValue.Field(field),
                ranges = ranges.map { AggRange(it.first, it.second) },
                format = format,
                missing = missing,
                params = params,
                aggs = aggs,
            )
        }
    }

    override fun clone() = copy()

    override fun makeRangeResult(buckets: List<RangeBucket>) = RangeAggResult(buckets)

    override fun processBucketResult(
        bucketObj: Deserializer.ObjectCtx,
        bucketKey: String?
    ): RangeBucket {
        return RangeBucket(
            key = bucketKey ?: bucketObj.string("key"),
            docCount = bucketObj.long("doc_count"),
            from = bucketObj.doubleOrNull("from"),
            fromAsString = bucketObj.stringOrNull("from_as_string"),
            to = bucketObj.doubleOrNull("to"),
            toAsString = bucketObj.stringOrNull("to_as_string"),
            aggs = processSubAggs(bucketObj),
        )
    }
}

data class DateRangeAggResult(
    override val buckets: List<DateRangeBucket>,
) : BucketAggResult<DateRangeBucket>()

data class DateRangeBucket(
    override val key: String,
    override val docCount: Long,
    val from: Double? = null,
    val fromAsString: String? = null,
    val to: Double? = null,
    val toAsString: String? = null,
    override val aggs: Map<String, AggregationResult> = emptyMap(),
) : KeyedBucket<String>() {
    fun fromAsDatetime(timeZone: TimeZone): LocalDateTime? {
        return if (from != null) {
            Instant.fromEpochMilliseconds(from.toLong()).toLocalDateTime(timeZone)
        } else {
            null
        }
    }

    fun toAsDatetime(timeZone: TimeZone): LocalDateTime? {
        return if (to != null) {
            Instant.fromEpochMilliseconds(to.toLong()).toLocalDateTime(timeZone)
        } else {
            null
        }
    }
}

data class DateRangeAgg(
    override val value: AggValue,
    override val ranges: List<AggRange<String>>,
    override val format: String? = null,
    override val missing: String? = null,
    override val params: Params = Params(),
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : BaseRangeAgg<String, DateRangeAggResult, DateRangeBucket>() {
    override val name = "date_range"

    constructor(
        field: FieldOperations,
        ranges: List<AggRange<String>>,
        format: String? = null,
        missing: String? = null,
        params: Params = Params(),
        aggs: Map<String, Aggregation<*>> = emptyMap(),
    ) : this(
        AggValue.Field(field),
        ranges = ranges,
        format = format,
        missing = missing,
        params = params,
        aggs = aggs,
    )

    companion object {
        fun simpleRanges(
            field: FieldOperations,
            ranges: List<Pair<String?, String?>>,
            format: String? = null,
            missing: String? = null,
            aggs: Map<String, Aggregation<*>> = emptyMap(),
            params: Params = Params(),
        ): DateRangeAgg {
            return DateRangeAgg(
                AggValue.Field(field),
                ranges = ranges.map { AggRange(it.first, it.second) },
                format = format,
                missing = missing,
                params = params,
                aggs = aggs,
            )
        }
    }

    override fun clone() = copy()

    override fun makeRangeResult(buckets: List<DateRangeBucket>) = DateRangeAggResult(buckets)

    override fun processBucketResult(
        bucketObj: Deserializer.ObjectCtx,
        bucketKey: String?
    ): DateRangeBucket {
        return DateRangeBucket(
            key = bucketKey ?: bucketObj.string("key"),
            docCount = bucketObj.long("doc_count"),
            from = bucketObj.doubleOrNull("from"),
            fromAsString = bucketObj.stringOrNull("from_as_string"),
            to = bucketObj.doubleOrNull("to"),
            toAsString = bucketObj.stringOrNull("to_as_string"),
            aggs = processSubAggs(bucketObj),
        )
    }
}

data class HistogramBounds<T>(
    val min: T?,
    val max: T?,
) : Expression {
    companion object {
        fun <T> from(min: T) = HistogramBounds(min, null)
        fun <T> to(max: T) = HistogramBounds(null, max)
    }

    override fun clone() = copy()

    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.fieldIfNotNull("min", min)
        ctx.fieldIfNotNull("max", max)
    }
}

abstract class BaseHistogramAgg<T, R: AggregationResult, B> : BucketAggregation<R>() {
    abstract val value: AggValue
    abstract val offset: T?
    abstract val minDocCount: Long?
    abstract val missing: T?
    abstract val format: String?
    // TODO: Keyed format response
    // abstract val keyed: Boolean?
    abstract val order: List<BucketsOrder>
    abstract val extendedBounds: HistogramBounds<T>?
    abstract val hardBounds: HistogramBounds<T>?
    abstract val params: Params

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        compiler.visit(ctx, value)
        ctx.fieldIfNotNull("offset", offset)
        ctx.fieldIfNotNull("min_doc_count", minDocCount)
        ctx.fieldIfNotNull("missing", missing)
        ctx.fieldIfNotNull("format", format)
        // ctx.fieldIfNotNull("keyed", keyed)
        when (order.size) {
            1 -> ctx.obj("order") {
                compiler.visit(this, order[0])
            }
            in 2..Int.MAX_VALUE -> ctx.array("order") {
                compiler.visit(this, order)
            }
            else -> {}
        }
        extendedBounds?.let { extendedBounds ->
            ctx.obj("extended_bounds") {
                extendedBounds.accept(this, compiler)
            }
        }
        hardBounds?.let { hardBounds ->
            ctx.obj("hard_bounds") {
                hardBounds.accept(this, compiler)
            }
        }
        if (params.isNotEmpty()) {
            compiler.visit(ctx, params)
        }
    }

    override fun processResult(obj: Deserializer.ObjectCtx): R {
        val bucketsArray = obj.arrayOrNull("buckets")
        if (bucketsArray != null) {
            val buckets = mutableListOf<B>()
            while (bucketsArray.hasNext()) {
                buckets.add(
                    processBucketResult(bucketsArray.obj())
                )
            }
            return makeHistogramResult(buckets)
        }

        val bucketsObj = obj.obj("buckets")
        val buckets = mutableListOf<B>()
        val bucketsIter = bucketsObj.iterator()
        while (bucketsIter.hasNext()) {
            val (_, bucketObj) = bucketsIter.obj()
            buckets.add(
                processBucketResult(bucketObj)
            )
        }
        return makeHistogramResult(buckets)
    }

    protected abstract fun processBucketResult(bucketObj: Deserializer.ObjectCtx): B

    protected abstract val makeHistogramResult: (List<B>) -> R
}

data class HistogramAgg(
    override val value: AggValue,
    val interval: Double,
    override val offset: Double? = null,
    override val minDocCount: Long? = null,
    override val missing: Double? = null,
    override val format: String? = null,
    override val order: List<BucketsOrder> = emptyList(),
    override val extendedBounds: HistogramBounds<Double>? = null,
    override val hardBounds: HistogramBounds<Double>? = null,
    override val params: Params = Params(),
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : BaseHistogramAgg<Double, HistogramAggResult, HistogramBucket>() {
    override val name = "histogram"

    constructor(
        field: FieldOperations,
        interval: Double,
        offset: Double? = null,
        minDocCount: Long? = null,
        missing: Double? = null,
        format: String? = null,
        order: List<BucketsOrder> = emptyList(),
        extendedBounds: HistogramBounds<Double>? = null,
        hardBounds: HistogramBounds<Double>? = null,
        params: Params = Params(),
        aggs: Map<String, Aggregation<*>> = emptyMap(),
    ) : this(
        AggValue.Field(field),
        interval = interval,
        offset = offset,
        minDocCount = minDocCount,
        missing = missing,
        format = format,
        order = order,
        extendedBounds = extendedBounds,
        hardBounds = hardBounds,
        params = params,
        aggs = aggs,
    )

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.field("interval", interval)

        super.visit(ctx, compiler)
    }

    override fun processBucketResult(bucketObj: Deserializer.ObjectCtx): HistogramBucket {
        return HistogramBucket(
            key = bucketObj.double("key"),
            docCount = bucketObj.long("doc_count"),
            keyAsString = bucketObj.stringOrNull("key_as_string"),
            aggs = processSubAggs(bucketObj)
        )
    }

    override val makeHistogramResult = ::HistogramAggResult
}

data class HistogramAggResult(
    override val buckets: List<HistogramBucket>,
) : BucketAggResult<HistogramBucket>()

data class HistogramBucket(
    override val key: Double,
    override val docCount: Long,
    override val aggs: Map<String, AggregationResult>,
    val keyAsString: String?,
) : KeyedBucket<Double>()

data class DateHistogramAgg(
    override val value: AggValue,
    val interval: Interval,
    override val offset: String? = null,
    override val minDocCount: Long? = null,
    // TODO: Should it be a LocalDateTime?
    override val missing: String? = null,
    override val format: String? = null,
    override val order: List<BucketsOrder> = emptyList(),
    // TODO: Should it be a HistogramBounds<LocalDateTime>?
    override val extendedBounds: HistogramBounds<String>? = null,
    override val hardBounds: HistogramBounds<String>? = null,
    override val params: Params = Params(),
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : BaseHistogramAgg<String, DateHistogramAggResult, DateHistogramBucket>() {
    override val name = "date_histogram"

    constructor(
        field: FieldOperations,
        interval: Interval,
        offset: String? = null,
        minDocCount: Long? = null,
        missing: String? = null,
        format: String? = null,
        order: List<BucketsOrder> = emptyList(),
        extendedBounds: HistogramBounds<String>? = null,
        hardBounds: HistogramBounds<String>? = null,
        params: Params = Params(),
        aggs: Map<String, Aggregation<*>> = emptyMap(),
    ) : this(
        AggValue.Field(field),
        interval = interval,
        offset = offset,
        minDocCount = minDocCount,
        missing = missing,
        format = format,
        order = order,
        extendedBounds = extendedBounds,
        hardBounds = hardBounds,
        params = params,
        aggs = aggs,
    )

    sealed class Interval : Expression {
        abstract val name: String
        abstract val interval: String

        override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
            ctx.field(name, interval)
        }

        data class Auto(override val interval: String) : Interval() {
            override val name: String = "interval"

            override fun clone() = copy()
        }
        data class Calendar(override val interval: String) : Interval() {
            override val name = "calendar_interval"

            override fun clone() = copy()
        }
        data class Fixed(override val interval: String) : Interval() {
            override val name = "fixed_interval"

            override fun clone() = copy()
        }
    }

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        compiler.visit(ctx, interval)
        super.visit(ctx, compiler)
    }

    override fun processBucketResult(bucketObj: Deserializer.ObjectCtx): DateHistogramBucket {
        return DateHistogramBucket(
            key = bucketObj.long("key"),
            docCount = bucketObj.long("doc_count"),
            keyAsString = bucketObj.stringOrNull("key_as_string"),
            aggs = processSubAggs(bucketObj)
        )
    }

    override val makeHistogramResult = ::DateHistogramAggResult
}

data class DateHistogramAggResult(
    override val buckets: List<DateHistogramBucket>,
) : BucketAggResult<DateHistogramBucket>()

data class DateHistogramBucket(
    override val key: Long,
    override val docCount: Long,
    override val aggs: Map<String, AggregationResult>,
    val keyAsString: String?,
) : KeyedBucket<Long>() {
    fun keyAsDatetime(timeZone: TimeZone): LocalDateTime {
        return Instant.fromEpochMilliseconds(key).toLocalDateTime(timeZone)
    }
}

data class GlobalAgg(
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : SingleBucketAggregation() {
    override val name = "global"

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {}
}

data class FilterAgg(
    val filter: QueryExpression,
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : SingleBucketAggregation() {
    override val name = "filter"

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        compiler.visit(ctx, filter)
    }
}

// TODO: Anonymous filters. Possibly we need another class: AnonymousFiltersAgg
data class FiltersAgg(
    val filters: Map<String, QueryExpression>,
    val otherBucketKey: String? = null,
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : BucketAggregation<FiltersAggResult>() {
    override val name = "filters"

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.obj("filters") {
            compiler.visit(this, filters)
        }
        ctx.fieldIfNotNull("other_bucket_key", otherBucketKey)
    }

    override fun processResult(obj: Deserializer.ObjectCtx): FiltersAggResult {
        val bucketsIter = obj.obj("buckets").iterator()
        val buckets = mutableMapOf<String, FiltersBucket>()
        while (bucketsIter.hasNext()) {
            val (bucketKey, bucketObj) = bucketsIter.obj()
            buckets[bucketKey] = FiltersBucket(
                key = bucketKey,
                docCount = bucketObj.long("doc_count"),
                aggs = processSubAggs(bucketObj),
            )
        }
        return FiltersAggResult(buckets)
    }
}

data class FiltersAggResult(
    val buckets: Map<String, FiltersBucket>,
) : AggregationResult {
    fun bucket(key: String): FiltersBucket {
        return buckets[key]
            ?: throw IllegalStateException("Missing bucket: [$key]")
    }
}

data class FiltersBucket(
    override val key: String,
    override val docCount: Long,
    override val aggs: Map<String, AggregationResult>,
) : KeyedBucket<String>()

data class NestedAgg(
    val path: FieldOperations,
    override val aggs: Map<String, Aggregation<*>>,
) : SingleBucketAggregation() {
    override val name = "nested"

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.field("path", path.getQualifiedFieldName())
    }
}
