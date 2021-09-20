package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.toMap

interface Aggregation : NamedExpression {
    fun processResult(obj: Deserializer.ObjectCtx): AggregationResult
}

abstract class MetricAggregation : Aggregation

abstract class BucketAggregation(
    val aggs: Map<String, Aggregation>,
) : Aggregation {
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

abstract class SingleBucketAggregation(
    aggs: Map<String, Aggregation>,
) : BucketAggregation(aggs) {
    override fun processResult(obj: Deserializer.ObjectCtx): AggregationResult {
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
        return aggs[name] as A
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

abstract class NumericValueAgg(
    val field: FieldOperations? = null,
    val script: Script? = null,
    val missing: Any? = null,
    val format: String? = null,
) : MetricAggregation() {
    init {
        require(field != null || script != null) {
            "field or script required"
        }
    }

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.fieldIfNotNull("field", field?.getQualifiedFieldName())
        if (script != null) {
            compiler.visit(ctx, script)
        }
        ctx.fieldIfNotNull("missing", missing)
        ctx.fieldIfNotNull("format", format)
    }
}

abstract class SingleDoubleValueAgg(
    field: FieldOperations? = null, script: Script? = null, missing: Any? = null, format: String? = null
) : NumericValueAgg(field, script, missing, format) {
    override fun processResult(obj: Deserializer.ObjectCtx): SingleValueMetricAggResult<Double> {
        return SingleValueMetricAggResult(
            obj.doubleOrNull("value"),
            obj.stringOrNull("value_as_string"),
        )
    }
}

abstract class SingleLongValueAgg(
    field: FieldOperations, script: Script? = null, missing: Any? = null, format: String? = null
) : NumericValueAgg(field, script, missing, format) {
    override fun processResult(obj: Deserializer.ObjectCtx): SingleValueMetricAggResult<Long> {
        return SingleValueMetricAggResult(
            obj.long("value"),
            obj.stringOrNull("value_as_string"),
        )
    }
}

class MinAgg(
    field: FieldOperations, script: Script? = null, missing: Any? = null, format: String? = null
) : SingleDoubleValueAgg(field, script, missing, format) {
    override val name = "min"
}

class MaxAgg(
    field: FieldOperations, script: Script? = null, missing: Any? = null, format: String? = null
) : SingleDoubleValueAgg(field, script, missing, format) {
    override val name = "max"
}

class AvgAgg(
    field: FieldOperations, script: Script? = null, missing: Any? = null, format: String? = null
) : SingleDoubleValueAgg(field, script, missing, format) {
    override val name = "avg"
}

class WeightedAvgAgg(
    val value: ValueSource, val weight: ValueSource, format: String? = null
) : NumericValueAgg(format = format) {
    override val name = "weighted_avg"

    class ValueSource(
        val field: FieldOperations? = null,
        val script: Script? = null,
        val missing: Any? = null,
    )

    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.obj(name) {
            obj("value") {
                visit(ctx, compiler, value)
            }
            obj("weight") {
                visit(ctx, compiler, weight)
            }
            fieldIfNotNull("format", format)
        }
    }

    private fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler, value: ValueSource) {
        ctx.fieldIfNotNull("field", value.field?.getQualifiedFieldName())
        if (value.script != null) {
            ctx.obj("script") {
                compiler.visit(this, value.script)
            }
        }
        ctx.fieldIfNotNull("missing", value.missing)
    }

    override fun processResult(obj: Deserializer.ObjectCtx): SingleValueMetricAggResult<Double> {
        return SingleValueMetricAggResult(
            obj.double("value"),
            obj.stringOrNull("value_as_string"),
        )
    }
}

class SumAgg(
    field: FieldOperations, script: Script? = null, missing: Any? = null, format: String? = null
) : SingleDoubleValueAgg(field, script, missing, format) {
    override val name = "sum"
}

class MedianAbsoluteDeviationAgg(
    field: FieldOperations, script: Script? = null, missing: Any? = null, format: String? = null
) : SingleDoubleValueAgg(field, script, missing, format) {
    override val name = "median_absolute_deviation"
}

class ValueCountAgg(
    field: FieldOperations, script: Script? = null, missing: Any? = null
) : SingleLongValueAgg(field, script, missing) {
    override val name = "value_count"
}

class CardinalityAgg(
    field: FieldOperations, script: Script? = null, missing: Any? = null
) : SingleLongValueAgg(field, script, missing) {
    override val name = "cardinality"
}

class StatsAgg(
    field: FieldOperations? = null, script: Script? = null, missing: Any? = null, format: String? = null
) : NumericValueAgg(field, script, missing, format) {
    override val name = "stats"

    override fun processResult(obj: Deserializer.ObjectCtx): StatsAggResult {
        return StatsAggResult(
            count = obj.long("count"),
            min = obj.double("min"),
            max = obj.double("max"),
            avg = obj.double("avg"),
            sum = obj.double("sum"),
        )
    }
}

data class StatsAggResult(
    val count: Long,
    val min: Double,
    val max: Double,
    val avg: Double,
    val sum: Double,
) : AggregationResult

class ExtendedStatsAgg(
    field: FieldOperations? = null, script: Script? = null, missing: Any? = null, format: String? = null
) : NumericValueAgg(field, script, missing, format) {
    override val name = "extended_stats"

    override fun processResult(obj: Deserializer.ObjectCtx): ExtendedStatsAggResult {
        val stdDevBoundsRaw = obj.obj("std_deviation_bounds")
        return ExtendedStatsAggResult(
            count = obj.long("count"),
            min = obj.double("min"),
            max = obj.double("max"),
            avg = obj.double("avg"),
            sum = obj.double("sum"),
            sumOfSquares = obj.double("sum_of_squares"),
            variance = obj.double("variance"),
            stdDeviation = obj.double("std_deviation"),
            stdDeviationBounds = ExtendedStatsAggResult.StdDeviationBounds(
                upper = stdDevBoundsRaw.double("upper"),
                lower = stdDevBoundsRaw.double("lower"),
            )
        )
    }
}

data class ExtendedStatsAggResult(
    val count: Long,
    val min: Double,
    val max: Double,
    val avg: Double,
    val sum: Double,
    val sumOfSquares: Double,
    val variance: Double,
    val stdDeviation: Double,
    val stdDeviationBounds: StdDeviationBounds
) : AggregationResult {
    data class StdDeviationBounds(
        val upper: Double,
        val lower: Double,
    )
}

abstract class BaseTermsAgg(
    val field: FieldOperations? = null,
    val script: Script? = null,
    val size: Int? = null,
    val shardSize: Int? = null,
    val minDocCount: Int? = null,
    val shardMinDocCount: Int? = null,
    val include: Include? = null,
    val exclude: Exclude? = null,
    val missing: Any? = null,
    val order: Map<String, String> = emptyMap(),
    val collectMode: CollectMode? = null,
    val executionHint: ExecutionHint? = null,
    val showTermDocCountError: Boolean? = null,
    val params: Params = Params(),
    aggs: Map<String, Aggregation> = emptyMap(),
) : BucketAggregation(aggs) {
    init {
        require(field != null || script != null) {
            "field or script required"
        }
    }

    sealed class Exclude {
        class Values(val values: List<Any>) : Exclude()
        class Regex(val regex: String) : Exclude()
    }

    sealed class Include {
        class Values(val values: List<Any>) : Include()
        class Regex(val regex: String) : Include()
        class Partition(val partition: Int, val numPartitions: Int) : Include()
    }

    enum class CollectMode : ToValue {
        BREADTH_FIRST, DEPTH_FIRST;

        override fun toValue(): Any = name.toLowerCase()
    }

    enum class ExecutionHint : ToValue {
        MAP, GLOBAL_ORDINALS;

        override fun toValue(): Any = name.toLowerCase()
    }

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.fieldIfNotNull("field", field?.getQualifiedFieldName())
        if (script != null) {
            compiler.visit(ctx, script)
        }
        ctx.fieldIfNotNull("size", size)
        ctx.fieldIfNotNull("shard_size", shardSize)
        ctx.fieldIfNotNull("min_doc_count", minDocCount)
        ctx.fieldIfNotNull("shard_min_doc_count", shardMinDocCount)
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
        when (exclude) {
            is Exclude.Values -> ctx.array("include") {
                compiler.visit(this, exclude.values)
            }
            is Exclude.Regex -> ctx.field("include", exclude.regex)
        }
        ctx.fieldIfNotNull("missing", missing)
        if (order.isNotEmpty()) {
            ctx.obj("order") {
                compiler.visit(this, order)
            }
        }
        ctx.fieldIfNotNull("collect_mode", collectMode?.toValue())
        ctx.fieldIfNotNull("execution_hint", executionHint?.toValue())
        ctx.fieldIfNotNull("show_term_doc_count_error", showTermDocCountError)
        if (params.isNotEmpty()) {
            compiler.visit(ctx, params)
        }
    }
}

class TermsAgg(
    field: FieldOperations? = null,
    script: Script? = null,
    size: Int? = null,
    shardSize: Int? = null,
    minDocCount: Int? = null,
    shardMinDocCount: Int? = null,
    include: Include? = null,
    exclude: Exclude? = null,
    missing: Any? = null,
    order: Map<String, String> = emptyMap(),
    collectMode: CollectMode? = null,
    executionHint: ExecutionHint? = null,
    showTermDocCountError: Boolean? = null,
    params: Params = Params(),
    aggs: Map<String, Aggregation> = emptyMap(),
) : BaseTermsAgg(
    field = field,
    script = script,
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
) {
    override val name = "terms"

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
        return TermsAggResult(buckets)
    }
}

data class TermsAggResult(
    override val buckets: List<TermBucket>
) : BucketAggResult<TermBucket>()

data class TermBucket(
    override val key: Any,
    override val docCount: Long,
    override val aggs: Map<String, AggregationResult> = emptyMap(),
    val docCountErrorUpperBound: Long? = null,
) : KeyedBucket<Any>()

class SignificantTermsAgg(
    field: FieldOperations? = null,
    script: Script? = null,
    size: Int? = null,
    shardSize: Int? = null,
    minDocCount: Int? = null,
    shardMinDocCount: Int? = null,
    include: BaseTermsAgg.Include? = null,
    exclude: BaseTermsAgg.Exclude? = null,
    missing: Any? = null,
    order: Map<String, String> = emptyMap(),
    collectMode: BaseTermsAgg.CollectMode? = null,
    executionHint: BaseTermsAgg.ExecutionHint? = null,
    showTermDocCountError: Boolean? = null,
    val backgroundFilter: QueryExpression? = null,
    params: Params = Params(),
    aggs: Map<String, Aggregation> = emptyMap(),
) : BaseTermsAgg(
    field = field,
    script = script,
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
) {
    override val name = "significant_terms"

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        super.visit(ctx, compiler)
        if (backgroundFilter != null) {
            compiler.visit(ctx, backgroundFilter)
        }
    }

    override fun processResult(obj: Deserializer.ObjectCtx): SignificantTermAggResult {
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
        return SignificantTermAggResult(buckets)
    }
}

data class SignificantTermAggResult(
    override val buckets: List<SignificantTermBucket>
) : BucketAggResult<SignificantTermBucket>()

data class SignificantTermBucket(
    override val key: Any,
    override val docCount: Long,
    override val aggs: Map<String, AggregationResult>,
    val bgCount: Long,
    val score: Float,
    val docCountErrorUpperBound: Long? = null,
) : KeyedBucket<Any>()

data class AggRange<T>(
    val from: T? = null,
    val to: T? = null,
    val key: String? = null,
) {
    companion object {
        internal fun <T> fromPair(pair: Pair<T?, T?>): AggRange<T> {
            return AggRange(from = pair.first, to = pair.second)
        }
    }
}

abstract class BaseRangeAgg<T>(
    val field: FieldOperations? = null,
    val script: Script? = null,
    val ranges: List<AggRange<T>>,
    val format: String? = null,
    val missing: T? = null,
    val keyed: Boolean? = null,
    val params: Params = Params(),
    aggs: Map<String, Aggregation> = emptyMap(),
) : BucketAggregation(aggs) {
    init {
        require(field != null || script != null) {
            "field or script required"
        }
    }

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.fieldIfNotNull("field", field?.getQualifiedFieldName())
        if (script != null) {
            compiler.visit(ctx, script)
        }
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
        ctx.fieldIfNotNull("keyed", keyed)
        if (params.isNotEmpty()) {
            compiler.visit(ctx, params)
        }
    }

    override fun processResult(obj: Deserializer.ObjectCtx): RangeAggResult {
        val bucketsArray = obj.arrayOrNull("buckets")
        if (bucketsArray != null) {
            val buckets = mutableListOf<RangeBucket>()
            while (bucketsArray.hasNext()) {
                buckets.add(
                    processBucketResult(bucketsArray.obj())
                )
            }
            return RangeAggResult(buckets)
        }

        val bucketsObj = obj.obj("buckets")
        val buckets = mutableListOf<RangeBucket>()
        val bucketsIter = bucketsObj.iterator()
        while (bucketsIter.hasNext()) {
            val (bucketKey, bucketObj) = bucketsIter.obj()
            buckets.add(
                processBucketResult(bucketObj, bucketKey)
            )
        }
        return RangeAggResult(buckets)
    }

    private fun processBucketResult(
        bucketObj: Deserializer.ObjectCtx,
        bucketKey: String? = null,
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

data class RangeAggResult(
    override val buckets: List<RangeBucket>,
) : BucketAggResult<RangeBucket>()

data class RangeBucket(
    override val key: String,
    override val docCount: Long,
    override val aggs: Map<String, AggregationResult>,
    val from: Double? = null,
    val fromAsString: String? = null,
    val to: Double? = null,
    val toAsString: String? = null,
) : KeyedBucket<String>()

class RangeAgg(
    field: FieldOperations? = null,
    script: Script? = null,
    ranges: List<AggRange<Double>>,
    format: String? = null,
    missing: Double? = null,
    keyed: Boolean? = null,
    aggs: Map<String, Aggregation> = emptyMap(),
    params: Params = Params(),
) : BaseRangeAgg<Double>(
    field = field,
    script = script,
    ranges = ranges,
    format = format,
    missing = missing,
    keyed = keyed,
    params = params,
    aggs = aggs,
) {
    override val name = "range"

    companion object {
        fun simpleRanges(
            field: FieldOperations? = null,
            script: Script? = null,
            ranges: List<Pair<Double?, Double?>>,
            format: String? = null,
            missing: Double? = null,
            keyed: Boolean? = null,
            aggs: Map<String, Aggregation> = emptyMap(),
            params: Params = Params(),
        ): RangeAgg {
            return RangeAgg(
                field = field,
                script = script,
                ranges = ranges.map(AggRange.Companion::fromPair),
                format = format,
                missing = missing,
                keyed = keyed,
                params = params,
                aggs = aggs,
            )
        }
    }
}

class DateRangeAgg(
    field: FieldOperations? = null,
    script: Script? = null,
    ranges: List<AggRange<String>>,
    format: String? = null,
    missing: String? = null,
    keyed: Boolean? = null,
    params: Params = Params(),
    aggs: Map<String, Aggregation> = emptyMap(),
) : BaseRangeAgg<String>(
    field = field,
    script = script,
    ranges = ranges,
    format = format,
    missing = missing,
    keyed = keyed,
    params = params,
    aggs = aggs,
) {
    override val name = "date_range"

    companion object {
        fun simpleRanges(
            field: FieldOperations? = null,
            script: Script? = null,
            ranges: List<Pair<String?, String?>>,
            format: String? = null,
            missing: String? = null,
            keyed: Boolean? = null,
            aggs: Map<String, Aggregation> = emptyMap(),
            params: Params = Params(),
        ): DateRangeAgg {
            return DateRangeAgg(
                field = field,
                script = script,
                ranges = ranges.map(AggRange.Companion::fromPair),
                format = format,
                missing = missing,
                keyed = keyed,
                params = params,
                aggs = aggs,
            )
        }
    }
}

data class HistogramBounds<T>(
    val min: T,
    val max: T,
) : Expression {
    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.field("min", min)
        ctx.field("max", max)
    }
}

abstract class BaseHistogramAgg<T, R: AggregationResult, B>(
    val field: FieldOperations? = null,
    val script: Script? = null,
    val offset: T? = null,
    val minDocCount: Long? = null,
    val missing: T? = null,
    val format: String? = null,
    val keyed: Boolean? = null,
    val order: Map<String, String> = emptyMap(),
    val extendedBounds: HistogramBounds<T>? = null,
    val hardBounds: HistogramBounds<T>? = null,
    val params: Params = Params(),
    aggs: Map<String, Aggregation> = emptyMap(),
) : BucketAggregation(aggs) {
    init {
        require(field != null || script != null) {
            "field or script required"
        }
    }

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.fieldIfNotNull("field", field?.getQualifiedFieldName())
        if (script != null) {
            compiler.visit(ctx, script)
        }
        ctx.fieldIfNotNull("offset", offset)
        ctx.fieldIfNotNull("min_doc_count", minDocCount)
        ctx.fieldIfNotNull("missing", missing)
        ctx.fieldIfNotNull("format", format)
        ctx.fieldIfNotNull("keyed", keyed)
        if (order.isNotEmpty()) {
            ctx.obj("order") {
                compiler.visit(this, order)
            }
        }
        if (extendedBounds != null) {
            ctx.obj("extended_bounds") {
                extendedBounds.accept(this, compiler)
            }
        }
        if (hardBounds != null) {
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

class HistogramAgg(
    field: FieldOperations? = null,
    script: Script? = null,
    val interval: Double,
    offset: Double? = null,
    minDocCount: Long? = null,
    missing: Double? = null,
    keyed: Boolean? = null,
    order: Map<String, String> = emptyMap(),
    extendedBounds: HistogramBounds<Double>? = null,
    hardBounds: HistogramBounds<Double>? = null,
    params: Params = Params(),
    aggs: Map<String, Aggregation> = emptyMap(),
) : BaseHistogramAgg<Double, HistogramAggResult, HistogramBucket>(
    field = field,
    script = script,
    offset = offset,
    minDocCount = minDocCount,
    missing = missing,
    keyed = keyed,
    order = order,
    extendedBounds = extendedBounds,
    hardBounds = hardBounds,
    params = params,
    aggs = aggs,
) {
    override val name = "histogram"

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

class DateHistogramAgg(
    field: FieldOperations? = null,
    script: Script? = null,
    val calendarInterval: String? = null,
    val fixedInterval: String? = null,
    val interval: String? = null,
    offset: String? = null,
    minDocCount: Long? = null,
    missing: String? = null,
    keyed: Boolean? = null,
    order: Map<String, String> = emptyMap(),
    extendedBounds: HistogramBounds<String>? = null,
    hardBounds: HistogramBounds<String>? = null,
    params: Params = Params(),
    aggs: Map<String, Aggregation> = emptyMap(),
) : BaseHistogramAgg<String, DateHistogramAggResult, DateHistogramBucket>(
    field = field,
    script = script,
    offset = offset,
    minDocCount = minDocCount,
    missing = missing,
    keyed = keyed,
    order = order,
    extendedBounds = extendedBounds,
    hardBounds = hardBounds,
    params = params,
    aggs = aggs,
) {
    override val name = "date_histogram"

    init {
        require(calendarInterval != null || fixedInterval != null || interval != null) {
            "One of interval argument is required: calendarInterval, fixedInterval or interval"
        }
    }

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.field("calendar_interval", calendarInterval)
        ctx.field("fixed_interval", fixedInterval)
        ctx.field("interval", interval)

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
) : KeyedBucket<Long>()

class GlobalAgg(
    aggs: Map<String, Aggregation> = emptyMap(),
) : SingleBucketAggregation(aggs) {
    override val name = "global"

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {}
}

class FilterAgg(
    val filter: QueryExpression,
    aggs: Map<String, Aggregation> = emptyMap(),
) : SingleBucketAggregation(aggs) {
    override val name = "filter"

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        compiler.visit(ctx, filter)
    }
}

class FiltersAgg(
    val filters: Map<String, QueryExpression>,
    aggs: Map<String, Aggregation>,
) : BucketAggregation(aggs) {
    override val name = "filters"

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.obj("filters") {
            compiler.visit(this, filters)
        }
    }

    override fun processResult(obj: Deserializer.ObjectCtx): FiltersAggResult {
        val bucketsIter = obj.obj("buckets").iterator()
        val buckets = mutableListOf<FiltersBucket>()
        while (bucketsIter.hasNext()) {
            val (bucketKey, bucketObj) = bucketsIter.obj()
            buckets.add(
                FiltersBucket(
                    key = bucketKey,
                    docCount = bucketObj.long("doc_count"),
                    aggs = processSubAggs(bucketObj),
                )
            )
        }
        return FiltersAggResult(buckets)
    }
}

data class FiltersAggResult(
    override val buckets: List<FiltersBucket>,
) : BucketAggResult<FiltersBucket>()

data class FiltersBucket(
    override val key: String,
    override val docCount: Long,
    override val aggs: Map<String, AggregationResult>,
) : KeyedBucket<String>()

class NestedAgg(
    val path: FieldOperations,
    aggs: Map<String, Aggregation>,
) : SingleBucketAggregation(aggs) {
    override val name = "nested"

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.field("path", path.getQualifiedFieldName())
    }
}
