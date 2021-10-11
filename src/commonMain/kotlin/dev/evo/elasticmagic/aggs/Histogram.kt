package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.query.Expression
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.query.ToValue
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer

data class HistogramBounds<T>(
    val min: T?,
    val max: T?,
) {
    companion object {
        fun <T> from(min: T) = HistogramBounds(min, null)
        fun <T> to(max: T) = HistogramBounds(null, max)
    }
}

abstract class BaseHistogramAgg<T, R: AggregationResult, B> : BucketAggregation<R>() {
    abstract val value: AggValue<T>
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
        ctx.fieldIfNotNull("min_doc_count", minDocCount)
        missing?.let { missing ->
            ctx.field("missing", value.serializeTerm(missing))
        }
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
                visitHistogramBounds(this, extendedBounds)
            }
        }
        hardBounds?.let { hardBounds ->
            ctx.obj("hard_bounds") {
                visitHistogramBounds(this, hardBounds)
            }
        }
        if (params.isNotEmpty()) {
            compiler.visit(ctx, params)
        }
    }

    private fun visitHistogramBounds(ctx: Serializer.ObjectCtx, bounds: HistogramBounds<T>) {
        bounds.min?.let { min ->
            ctx.field("min", value.serializeTerm(min))
        }
        bounds.max?.let { max ->
            ctx.field("max", value.serializeTerm(max))
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

data class HistogramAgg<T: Number>(
    override val value: AggValue<T>,
    val interval: T,
    val offset: T? = null,
    override val minDocCount: Long? = null,
    override val missing: T? = null,
    override val format: String? = null,
    override val order: List<BucketsOrder> = emptyList(),
    override val extendedBounds: HistogramBounds<T>? = null,
    override val hardBounds: HistogramBounds<T>? = null,
    override val params: Params = Params(),
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : BaseHistogramAgg<T, HistogramAggResult, HistogramBucket>() {
    override val name = "histogram"

    constructor(
        field: FieldOperations<T>,
        interval: T,
        offset: T? = null,
        minDocCount: Long? = null,
        missing: T? = null,
        format: String? = null,
        order: List<BucketsOrder> = emptyList(),
        extendedBounds: HistogramBounds<T>? = null,
        hardBounds: HistogramBounds<T>? = null,
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
        ctx.fieldIfNotNull("offset", offset)

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

enum class CalendarInterval : ToValue {
    MINUTE, HOUR, DAY, WEEK, MONTH, QUARTER, YEAR;

    override fun toValue() = name.lowercase()
}

sealed class FixedInterval : ToValue {
    abstract val value: Int
    abstract val unit: String

    override fun toValue(): String {
        return "$value$unit"
    }

    data class Milliseconds(override val value: Int) : FixedInterval() {
        override val unit = "ms"
    }
    data class Seconds(override val value: Int) : FixedInterval() {
        override val unit = "s"
    }
    data class Minutes(override val value: Int) : FixedInterval() {
        override val unit = "m"
    }
    data class Hours(override val value: Int) : FixedInterval() {
        override val unit = "h"
    }
    data class Days(override val value: Int) : FixedInterval() {
        override val unit = "d"
    }
}

data class DateHistogramAgg<T>(
    override val value: AggValue<T>,
    val interval: Interval,
    val offset: FixedInterval? = null,
    override val minDocCount: Long? = null,
    override val missing: T? = null,
    override val format: String? = null,
    override val order: List<BucketsOrder> = emptyList(),
    override val extendedBounds: HistogramBounds<T>? = null,
    override val hardBounds: HistogramBounds<T>? = null,
    override val params: Params = Params(),
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : BaseHistogramAgg<T, DateHistogramAggResult<T>, DateHistogramBucket<T>>() {
    override val name = "date_histogram"

    constructor(
        field: FieldOperations<T>,
        interval: Interval,
        offset: FixedInterval? = null,
        minDocCount: Long? = null,
        missing: T? = null,
        format: String? = null,
        order: List<BucketsOrder> = emptyList(),
        extendedBounds: HistogramBounds<T>? = null,
        hardBounds: HistogramBounds<T>? = null,
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

        abstract fun intervalValue(): String

        override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
            ctx.field(name, intervalValue())
        }

        data class Auto(val interval: String) : Interval() {
            override val name: String = "interval"

            override fun clone() = copy()

            override fun intervalValue(): String = interval
        }

        data class Calendar(val interval: CalendarInterval) : Interval() {
            override val name = "calendar_interval"

            override fun clone() = copy()

            override fun intervalValue() = interval.toValue()
        }

        data class Fixed(val interval: FixedInterval) : Interval() {
            override val name = "fixed_interval"

            override fun clone() = copy()

            override fun intervalValue() = interval.toValue()
        }
    }

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        compiler.visit(ctx, interval)
        offset?.let { offset ->
            ctx.field("offset", offset.toValue())
        }

        super.visit(ctx, compiler)
    }

    override fun processBucketResult(bucketObj: Deserializer.ObjectCtx): DateHistogramBucket<T> {
        val key = bucketObj.long("key")
        val deserializedKey = value.deserializeTerm(key)
        return DateHistogramBucket(
            key = key,
            keyAsString = bucketObj.stringOrNull("key_as_string"),
            keyAsDatetime = value.deserializeTerm(key),
            docCount = bucketObj.long("doc_count"),
            aggs = processSubAggs(bucketObj)
        )
    }

    override val makeHistogramResult: (List<DateHistogramBucket<T>>) -> DateHistogramAggResult<T> =
        ::DateHistogramAggResult
}

data class DateHistogramAggResult<T>(
    override val buckets: List<DateHistogramBucket<T>>,
) : BucketAggResult<DateHistogramBucket<T>>()

data class DateHistogramBucket<T>(
    override val key: Long,
    val keyAsString: String?,
    val keyAsDatetime: T,
    override val docCount: Long,
    override val aggs: Map<String, AggregationResult>,
) : KeyedBucket<Long>()
