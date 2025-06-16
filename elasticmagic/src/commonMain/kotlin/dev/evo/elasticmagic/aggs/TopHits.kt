package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.SearchHit
import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.query.Sort
import dev.evo.elasticmagic.query.Source
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.forEachObj

data class TopHitsAggResult<S : BaseDocSource>(
    val totalHits: Long?,
    val totalHitsRelation: String?,
    val maxScore: Float?,
    val hits: List<SearchHit<S>>,
) : AggregationResult

data class TopHitsAgg<S : BaseDocSource>(
    val docSourceFactory: (obj: Deserializer.ObjectCtx) -> S,
    val from: Int? = null,
    val size: Int? = null,
    val sort: List<Sort>? = null,
    val source: Source? = null,
) : MetricAggregation<TopHitsAggResult<S>>() {
    companion object {
        operator fun <S : BaseDocSource> invoke(
            docSourceFactory: () -> S,
            from: Int? = null,
            size: Int? = null,
            sort: List<Sort>? = null,
            source: Source? = null,
        ): TopHitsAgg<S> {
            return TopHitsAgg(
                { docSourceFactory() },
                from = from,
                size = size,
                sort = sort,
                source = source,
            )
        }

        operator fun invoke(
            from: Int? = null,
            size: Int? = null,
            sort: List<Sort>? = null,
            source: Source? = null,
        ): TopHitsAgg<DynDocSource> {
            return TopHitsAgg(
                { DynDocSource() },
                from = from,
                size = size,
                sort = sort,
                source = source,
            )
        }
    }

    override val name = "top_hits"

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
        ctx.fieldIfNotNull("from", from)
        ctx.fieldIfNotNull("size", size)
        if (sort != null) {
            ctx.array("sort") {
                compiler.visit(this, sort)
            }
        }
        if (source != null) {
            compiler.visit(ctx, source)
        }
    }

    override fun processResult(obj: Deserializer.ObjectCtx): TopHitsAggResult<S> {
        val rawHitsData = obj.obj("hits")
        val rawTotal = rawHitsData.objOrNull("total")
        val (totalHits, totalHitsRelation) = if (rawTotal != null) {
            rawTotal.long("value") to rawTotal.string("relation")
        } else {
            rawHitsData.longOrNull("total") to null
        }

        val rawHits = rawHitsData.arrayOrNull("hits")
        val hits = buildList {
            rawHits?.forEachObj { rawHit ->
                add(SearchHit(rawHit, docSourceFactory))
            }
        }

        return TopHitsAggResult(
            totalHits = totalHits,
            totalHitsRelation = totalHitsRelation,
            maxScore = rawHitsData.floatOrNull("max_score"),
            hits = hits,
        )
    }

}
