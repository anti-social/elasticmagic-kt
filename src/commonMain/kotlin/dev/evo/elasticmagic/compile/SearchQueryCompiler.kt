package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.AggregationResult
import dev.evo.elasticmagic.BaseDocSource
import dev.evo.elasticmagic.BaseSearchQuery
import dev.evo.elasticmagic.Bool
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.Expression
import dev.evo.elasticmagic.PreparedSearchQuery
import dev.evo.elasticmagic.SearchHit
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.ToValue
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx
import dev.evo.elasticmagic.serde.toMap
import dev.evo.elasticmagic.toRequestParameters
import dev.evo.elasticmagic.transport.Method

class SearchQueryWithIndex<S: BaseDocSource>(
    val searchQuery: BaseSearchQuery<S, *>,
    val indexName: String,
)

fun <S: BaseDocSource> BaseSearchQuery<S, *>.usingIndex(
    indexName: String
): SearchQueryWithIndex<S> {
    return SearchQueryWithIndex(this, indexName)
}

open class SearchQueryCompiler(
    esVersion: ElasticsearchVersion,
) : BaseCompiler(esVersion) {

    interface Visitable {
        fun accept(ctx: ObjectCtx, compiler: SearchQueryCompiler)
    }

    fun <OBJ, S: BaseDocSource> compile(
        serializer: Serializer<OBJ>, input: SearchQueryWithIndex<S>
    ): Compiled<OBJ, SearchQueryResult<S>> {
        val searchQuery = input.searchQuery.prepare()
        val body = serializer.buildObj {
            visit(this, searchQuery)
        }
        return Compiled(
            method = Method.POST,
            path = "${input.indexName}/_search",
            parameters = searchQuery.params.toRequestParameters(),
            body = body,
            processResult = { ctx ->
                processResult(ctx, searchQuery)
            }
        )
    }

    fun visit(ctx: ObjectCtx, searchQuery: PreparedSearchQuery<*>) {
        val query = searchQuery.query?.reduce()
        val filteredQuery = if (searchQuery.filters.isNotEmpty()) {
            if (query != null) {
                Bool(must = listOf(query), filter = searchQuery.filters)
            } else {
                Bool(filter = searchQuery.filters)
            }
        } else {
            query
        }
        if (filteredQuery != null) {
            ctx.obj("query") {
                filteredQuery.accept(this, this@SearchQueryCompiler)
            }
        }
        if (searchQuery.postFilters.isNotEmpty()) {
            ctx.array("post_filter") {
                visit(this, searchQuery.postFilters)
            }
        }
        if (searchQuery.aggregations.isNotEmpty()) {
            ctx.obj("aggs") {
                visit(this, searchQuery.aggregations)
            }
        }
        if (searchQuery.rescores.isNotEmpty()) {
            ctx.array("rescore") {
                visit(this, searchQuery.rescores)
            }
        }
        if (searchQuery.sorts.isNotEmpty()) {
            ctx.array("sort") {
                for (sort in searchQuery.sorts) {
                    val simpleSortName = sort.simplifiedName()
                    if (simpleSortName != null) {
                        value(simpleSortName)
                    } else {
                        obj {
                            visit(this, sort)
                        }
                    }
                }
            }
        }
        if (searchQuery.trackScores != null) {
            ctx.field("track_scores", searchQuery.trackScores)
        }
        if (searchQuery.trackTotalHits != null) {
            ctx.field("track_total_hits", searchQuery.trackTotalHits)
        }
        if (searchQuery.docvalueFields.isNotEmpty()) {
            ctx.array("docvalue_fields") {
                for (field in searchQuery.docvalueFields) {
                    if (field.format != null) {
                        obj {
                            field("field", field.field.getQualifiedFieldName())
                            field("format", field.format)
                        }
                    } else {
                        value(field.field.getQualifiedFieldName())
                    }
                }
            }
        }
        if (searchQuery.storedFields.isNotEmpty()) {
            ctx.array("stored_fields") {
                visit(this, searchQuery.storedFields)
            }
        }
        if (searchQuery.scriptFields.isNotEmpty()) {
            ctx.obj("script_fields") {
                visit(this, searchQuery.scriptFields)
            }
        }
        if (searchQuery.size != null) {
            ctx.field("size", searchQuery.size)
        }
        if (searchQuery.from != null) {
            ctx.field("from", searchQuery.from)
        }
        if (searchQuery.terminateAfter != null) {
            ctx.field("terminate_after", searchQuery.terminateAfter)
        }
    }

    fun visit(ctx: ObjectCtx, expression: Expression) {
        expression.accept(ctx, this)
    }

    override fun dispatch(ctx: ArrayCtx, value: Any?) {
        when (value) {
            is Expression -> ctx.obj {
                visit(this, value)
            }
            is ToValue -> {
                ctx.value(value.toValue())
            }
            else -> super.dispatch(ctx, value)
        }
    }

    override fun dispatch(ctx: ObjectCtx, name: String, value: Any?) {
        when (value) {
            is Expression -> ctx.obj(name) {
                visit(this, value)
            }
            is ToValue -> {
                ctx.field(name, value.toValue())
            }
            else -> super.dispatch(ctx, name, value)
        }
    }

    fun <S: BaseDocSource> processResult(
        ctx: Deserializer.ObjectCtx,
        preparedSearchQuery: PreparedSearchQuery<S>,
    ): SearchQueryResult<S> {
        val rawHitsData = ctx.obj("hits")
        val rawTotal = rawHitsData.objOrNull("total")
        val (totalHits, totalHitsRelation) = if (rawTotal != null) {
            rawTotal.long("value") to rawTotal.string("relation")
        } else {
            rawHitsData.long("total") to null
        }
        val hits = mutableListOf<SearchHit<S>>()
        val rawHits = rawHitsData.arrayOrNull("hits")
        if (rawHits != null) {
            while (rawHits.hasNext()) {
                val rawHit = rawHits.obj()
                val source = rawHit.objOrNull("_source")?.let { rawSource ->
                    preparedSearchQuery.sourceFactory().apply {
                        setSource(rawSource.toMap())
                    }
                }
                val rawSort = rawHit.arrayOrNull("sort")
                val sort = mutableListOf<Any>()
                if (rawSort != null) {
                    while (rawSort.hasNext()) {
                        sort.add(rawSort.any())
                    }
                }
                hits.add(
                    SearchHit(
                        index = rawHit.string("_index"),
                        type = rawHit.stringOrNull("_type") ?: "_doc",
                        id = rawHit.string("_id"),
                        routing = rawHit.stringOrNull("_routing"),
                        version = rawHit.longOrNull("_version"),
                        seqNo = rawHit.longOrNull("_seq_no"),
                        primaryTerm = rawHit.longOrNull("_primary_term"),
                        score = rawHit.doubleOrNull("_score"),
                        sort = sort.ifEmpty { null },
                        source = source,
                    )
                )
            }
        }
        val rawAggs = ctx.objOrNull("aggregations")
        val aggResults = mutableMapOf<String, AggregationResult>()
        if (rawAggs != null) {
            for ((aggName, agg) in preparedSearchQuery.aggregations) {
                aggResults[aggName] = agg.processResult(rawAggs.obj(aggName))
            }
        }
        return SearchQueryResult(
            // TODO: Flag to add raw result
            null,
            took = ctx.long("took"),
            timedOut = ctx.boolean("timed_out"),
            totalHits = totalHits,
            totalHitsRelation = totalHitsRelation,
            maxScore = rawHitsData.doubleOrNull("max_score"),
            hits = hits,
            aggs = aggResults,
        )
    }
}
