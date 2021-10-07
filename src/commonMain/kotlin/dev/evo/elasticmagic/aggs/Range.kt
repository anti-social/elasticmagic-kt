package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.FieldOperations
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
