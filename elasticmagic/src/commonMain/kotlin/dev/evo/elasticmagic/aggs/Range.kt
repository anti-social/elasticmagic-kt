package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.forEachObj

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

abstract class BaseRangeAgg<T, R : AggregationResult, B> : BucketAggregation<R>() {
    abstract val value: AggValue<T>
    abstract val ranges: List<AggRange<T>>
    abstract val format: String?
    abstract val missing: T?
    // TODO: Keyed response
    // abstract val keyed: Boolean?
    abstract val params: Params

    override fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
        compiler.visit(ctx, value)
        ctx.array("ranges") {
            for ((from, to, key) in ranges) {
                obj {
                    from?.let { from ->
                        field("from", value.serializeTerm(from))
                    }
                    to?.let { to ->
                        field("to", value.serializeTerm(to))
                    }
                    fieldIfNotNull("key", key)
                }
            }
        }
        ctx.fieldIfNotNull("format", format)
        missing?.let { missing ->
            ctx.field("missing", value.serializeTerm(missing))
        }
        // ctx.fieldIfNotNull("keyed", keyed)
        if (params.isNotEmpty()) {
            compiler.visit(ctx, params)
        }
    }

    override fun processResult(
        obj: Deserializer.ObjectCtx,
        docSourceFactory: (Deserializer.ObjectCtx) -> BaseDocSource,
    ): R {
        val bucketsArray = obj.arrayOrNull("buckets")
        if (bucketsArray != null) {
            val buckets = buildList {
                bucketsArray.forEachObj { bucketObj ->
                    add(processBucketResult(bucketObj, null, docSourceFactory))
                }
            }
            return makeRangeResult(buckets)
        }

        val bucketsObj = obj.obj("buckets")
        val buckets = buildList {
            bucketsObj.forEachObj { bucketKey, bucketObj ->
                add(processBucketResult(bucketObj, bucketKey, docSourceFactory))
            }
        }
        return makeRangeResult(buckets)
    }

    protected abstract fun makeRangeResult(buckets: List<B>): R

    protected abstract fun processBucketResult(
        bucketObj: Deserializer.ObjectCtx,
        bucketKey: String?,
        docSourceFactory: (Deserializer.ObjectCtx) -> BaseDocSource,
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

data class RangeAgg<T : Number>(
    override val value: AggValue<T>,
    override val ranges: List<AggRange<T>>,
    override val format: String? = null,
    override val missing: T? = null,
    override val params: Params = Params(),
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : BaseRangeAgg<T, RangeAggResult, RangeBucket>() {
    override val name = "range"

    constructor(
        field: FieldOperations<T>,
        ranges: List<AggRange<T>>,
        format: String? = null,
        missing: T? = null,
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
        fun <T : Number> simpleRanges(
            field: FieldOperations<T>,
            ranges: List<Pair<T?, T?>>,
            format: String? = null,
            missing: T? = null,
            aggs: Map<String, Aggregation<*>> = emptyMap(),
            params: Params = Params(),
        ): RangeAgg<T> {
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
        bucketKey: String?,
        docSourceFactory: (Deserializer.ObjectCtx) -> BaseDocSource,
    ): RangeBucket {
        return RangeBucket(
            key = bucketKey ?: bucketObj.string("key"),
            docCount = bucketObj.long("doc_count"),
            from = bucketObj.doubleOrNull("from"),
            fromAsString = bucketObj.stringOrNull("from_as_string"),
            to = bucketObj.doubleOrNull("to"),
            toAsString = bucketObj.stringOrNull("to_as_string"),
            aggs = processSubAggs(bucketObj, docSourceFactory),
        )
    }
}

data class DateRangeAggResult<T>(
    override val buckets: List<DateRangeBucket<T>>,
) : BucketAggResult<DateRangeBucket<T>>()

data class DateRangeBucket<T>(
    override val key: String,
    override val docCount: Long,
    val from: Double? = null,
    val fromAsString: String? = null,
    val fromAsDatetime: T? = null,
    val to: Double? = null,
    val toAsString: String? = null,
    val toAsDatetime: T? = null,
    override val aggs: Map<String, AggregationResult> = emptyMap(),
) : KeyedBucket<String>()

data class DateRangeAgg<T>(
    override val value: AggValue<T>,
    override val ranges: List<AggRange<T>>,
    override val format: String? = null,
    override val missing: T? = null,
    override val params: Params = Params(),
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : BaseRangeAgg<T, DateRangeAggResult<T>, DateRangeBucket<T>>() {
    override val name = "date_range"

    constructor(
        field: FieldOperations<T>,
        ranges: List<AggRange<T>>,
        format: String? = null,
        missing: T? = null,
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
        fun <T> simpleRanges(
            field: FieldOperations<T>,
            ranges: List<Pair<T?, T?>>,
            format: String? = null,
            missing: T? = null,
            aggs: Map<String, Aggregation<*>> = emptyMap(),
            params: Params = Params(),
        ): DateRangeAgg<T> {
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

    override fun makeRangeResult(buckets: List<DateRangeBucket<T>>) = DateRangeAggResult(buckets)

    override fun processBucketResult(
        bucketObj: Deserializer.ObjectCtx,
        bucketKey: String?,
        docSourceFactory: (Deserializer.ObjectCtx) -> BaseDocSource,
    ): DateRangeBucket<T> {
        val fromAsString = bucketObj.stringOrNull("from_as_string")
        val toAsString = bucketObj.stringOrNull("to_as_string")
        return DateRangeBucket(
            key = bucketKey ?: bucketObj.string("key"),
            docCount = bucketObj.long("doc_count"),
            from = bucketObj.doubleOrNull("from"),
            fromAsString = fromAsString,
            fromAsDatetime = fromAsString?.let(value::deserializeTerm),
            to = bucketObj.doubleOrNull("to"),
            toAsString = toAsString,
            toAsDatetime = toAsString?.let(value::deserializeTerm),
            aggs = processSubAggs(bucketObj, docSourceFactory),
        )
    }
}
