package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.query.ToValue
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer

abstract class BaseTermsAgg<T: Any, R: AggregationResult> : BucketAggregation<R>() {
    abstract val value: AggValue<T>
    abstract val size: Int?
    abstract val shardSize: Int?
    abstract val minDocCount: Int?
    abstract val shardMinDocCount: Int?
    abstract val include: Include?
    abstract val exclude: Exclude?
    abstract val missing: T?
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

    enum class CollectMode : ToValue<String> {
        BREADTH_FIRST, DEPTH_FIRST;

        override fun toValue() = name.lowercase()
    }

    enum class ExecutionHint : ToValue<String> {
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

data class TermsAgg<T: Any>(
    override val value: AggValue<T>,
    override val size: Int? = null,
    override val shardSize: Int? = null,
    override val minDocCount: Int? = null,
    override val shardMinDocCount: Int? = null,
    override val include: Include? = null,
    override val exclude: Exclude? = null,
    override val missing: T? = null,
    override val order: List<BucketsOrder> = emptyList(),
    override val collectMode: CollectMode? = null,
    override val executionHint: ExecutionHint? = null,
    val showTermDocCountError: Boolean? = null,
    override val params: Params = Params(),
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : BaseTermsAgg<T, TermsAggResult<T>>() {
    override val name = "terms"

    constructor(
        field: FieldOperations<T>,
        size: Int? = null,
        shardSize: Int? = null,
        minDocCount: Int? = null,
        shardMinDocCount: Int? = null,
        include: Include? = null,
        exclude: Exclude? = null,
        missing: T? = null,
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

    override fun processResult(obj: Deserializer.ObjectCtx): TermsAggResult<T> {
        val buckets = mutableListOf<TermBucket<T>>()
        val rawBuckets = obj.array("buckets")
        while (rawBuckets.hasNext()) {
            val rawBucket = rawBuckets.obj()
            buckets.add(
                TermBucket(
                    key = value.deserializeTerm(rawBucket.any("key")),
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

data class TermsAggResult<T>(
    override val buckets: List<TermBucket<T>>,
    val docCountErrorUpperBound: Long,
    val sumOtherDocCount: Long,
) : BucketAggResult<TermBucket<T>>()

data class TermBucket<T>(
    override val key: T,
    override val docCount: Long,
    val docCountErrorUpperBound: Long? = null,
    override val aggs: Map<String, AggregationResult> = emptyMap(),
) : KeyedBucket<T>()

data class SignificantTermsAgg<T: Any>(
    override val value: AggValue<T>,
    override val size: Int? = null,
    override val shardSize: Int? = null,
    override val minDocCount: Int? = null,
    override val shardMinDocCount: Int? = null,
    override val include: Include? = null,
    override val exclude: Exclude? = null,
    override val missing: T? = null,
    override val order: List<BucketsOrder> = emptyList(),
    override val collectMode: CollectMode? = null,
    override val executionHint: ExecutionHint? = null,
    val backgroundFilter: QueryExpression? = null,
    override val params: Params = Params(),
    override val aggs: Map<String, Aggregation<*>> = emptyMap(),
) : BaseTermsAgg<T, SignificantTermsAggResult<T>>() {
    override val name = "significant_terms"

    constructor(
        field: FieldOperations<T>,
        size: Int? = null,
        shardSize: Int? = null,
        minDocCount: Int? = null,
        shardMinDocCount: Int? = null,
        include: Include? = null,
        exclude: Exclude? = null,
        missing: T? = null,
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

    override fun processResult(obj: Deserializer.ObjectCtx): SignificantTermsAggResult<T> {
        val buckets = mutableListOf<SignificantTermBucket<T>>()
        val rawBuckets = obj.array("buckets")
        while (rawBuckets.hasNext()) {
            val rawBucket = rawBuckets.obj()
            buckets.add(
                SignificantTermBucket(
                    key = value.deserializeTerm(rawBucket.any("key")),
                    docCount = rawBucket.long("doc_count"),
                    bgCount = rawBucket.long("bg_count"),
                    score = rawBucket.float("score"),
                    docCountErrorUpperBound = rawBucket.longOrNull("doc_count_error_upper_bound"),
                    aggs = processSubAggs(rawBucket)
                )
            )

        }
        return SignificantTermsAggResult(
            obj.long("doc_count"),
            obj.long("bg_count"),
            buckets
        )
    }
}

data class SignificantTermsAggResult<T>(
    val docCount: Long,
    val bgCount: Long,
    override val buckets: List<SignificantTermBucket<T>>,
) : BucketAggResult<SignificantTermBucket<T>>()

data class SignificantTermBucket<T>(
    override val key: T,
    override val docCount: Long,
    val bgCount: Long,
    val score: Float,
    val docCountErrorUpperBound: Long? = null,
    override val aggs: Map<String, AggregationResult> = emptyMap(),
) : KeyedBucket<T>()
