package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.query.Expression
import dev.evo.elasticmagic.FieldOperations
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
