package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.AggAwareResult
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.ObjExpression
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.query.Sort
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer

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

abstract class BucketAggResult<B: BaseBucket> : AggregationResult {
    abstract val buckets: List<B>
}

abstract class BaseBucket : AggAwareResult() {
    abstract val docCount: Long
}

data class SingleBucketAggResult(
    override val docCount: Long,
    override val aggs: Map<String, AggregationResult>,
) : BaseBucket(), AggregationResult

abstract class KeyedBucket<K> : BaseBucket() {
    abstract val key: K
}

data class BucketsOrder(
    val key: String,
    val order: Sort.Order,
) : ObjExpression {
    override fun clone() = copy()

    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.field(key, order.toValue())
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
    val path: FieldOperations<Nothing>,
    override val aggs: Map<String, Aggregation<*>>,
) : SingleBucketAggregation() {
    override val name = "nested"

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.field("path", path.getQualifiedFieldName())
    }
}
