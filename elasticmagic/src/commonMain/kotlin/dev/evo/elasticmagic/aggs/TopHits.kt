package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.SearchHit
import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.query.FieldFormat
import dev.evo.elasticmagic.query.Sort
import dev.evo.elasticmagic.query.Source
import dev.evo.elasticmagic.query.StoredField
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
    val docSourceFactory: ((obj: Deserializer.ObjectCtx) -> S)?,
    val from: Int? = null,
    val size: Int? = null,
    val sort: List<Sort> = emptyList(),
    val source: Source? = null,
    val fields: List<FieldFormat> = emptyList(),
    val docvalueFields: List<FieldFormat> = emptyList(),
    val storedFields: List<StoredField> = emptyList(),
    val params: Params = Params(),
) : MetricAggregation<TopHitsAggResult<S>>() {
    companion object {
        operator fun <S : BaseDocSource> invoke(
            docSourceFactory: () -> S,
            from: Int? = null,
            size: Int? = null,
            sort: List<Sort> = emptyList(),
            source: Source? = null,
            fields: List<FieldFormat> = emptyList(),
            docvalueFields: List<FieldFormat> = emptyList(),
            storedFields: List<StoredField> = emptyList(),
            params: Params = Params(),
        ): TopHitsAgg<S> {
            return TopHitsAgg(
                { docSourceFactory() },
                from = from,
                size = size,
                sort = sort,
                source = source,
                fields = fields,
                docvalueFields = docvalueFields,
                storedFields = storedFields,
                params = params,
            )
        }

        operator fun invoke(
            from: Int? = null,
            size: Int? = null,
            sort: List<Sort> = emptyList(),
            source: Source? = null,
            fields: List<FieldFormat> = emptyList(),
            docvalueFields: List<FieldFormat> = emptyList(),
            storedFields: List<StoredField> = emptyList(),
            params: Params = Params(),
        ): TopHitsAgg<BaseDocSource> {
            return TopHitsAgg(
                null,
                from = from,
                size = size,
                sort = sort,
                source = source,
                fields = fields,
                docvalueFields = docvalueFields,
                storedFields = storedFields,
                params = params,
            )
        }
    }

    override val name = "top_hits"

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
        ctx.fieldIfNotNull("from", from)
        ctx.fieldIfNotNull("size", size)
        if (sort.isNotEmpty()) {
            ctx.array("sort") {
                compiler.visit(this, sort)
            }
        }
        if (source != null) {
            compiler.visit(ctx, source)
        }
        if (fields.isNotEmpty()) {
            ctx.array("fields") {
                compiler.visit(this, fields)
            }
        }
        if (docvalueFields.isNotEmpty()) {
            ctx.array("docvalue_fields") {
                compiler.visit(this, docvalueFields)
            }
        }
        if (storedFields.isNotEmpty()) {
            val firstStoredField = storedFields.first()
            if (firstStoredField === StoredField.None) {
                // Elasticsearch 8.x requires _none_ to be a scalar value
                ctx.field("stored_fields", firstStoredField.toValue())
            } else {
                ctx.array("stored_fields") {
                    compiler.visit(this, storedFields)
                }
            }
        }
    }

    override fun processResult(
        obj: Deserializer.ObjectCtx,
        docSourceFactory: (Deserializer.ObjectCtx) -> BaseDocSource,
    ): TopHitsAggResult<S> {
        @Suppress("UNCHECKED_CAST")
        val docSourceFactory = this.docSourceFactory ?:
            docSourceFactory as (Deserializer.ObjectCtx) -> S

        val rawHitsData = obj.obj("hits")
        val rawTotal = rawHitsData.objOrNull("total")
        val (totalHits, totalHitsRelation) = if (rawTotal != null) {
            rawTotal.long("value") to rawTotal.string("relation")
        } else {
            rawHitsData.longOrNull("total") to null
        }

        val rawHits = rawHitsData.arrayOrNull("hits")
        val hits = buildList<SearchHit<S>> {
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
