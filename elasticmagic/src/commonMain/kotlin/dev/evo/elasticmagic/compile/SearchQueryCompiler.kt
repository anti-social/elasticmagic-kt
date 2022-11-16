package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.MultiSearchQueryResult
import dev.evo.elasticmagic.PreparedSearchQuery
import dev.evo.elasticmagic.SearchHit
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.SearchQueryWithIndex
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.query.ArrayExpression
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.Expression
import dev.evo.elasticmagic.query.ObjExpression
import dev.evo.elasticmagic.query.ToValue
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx
import dev.evo.elasticmagic.serde.forEach
import dev.evo.elasticmagic.serde.forEachArray
import dev.evo.elasticmagic.serde.forEachObj
import dev.evo.elasticmagic.serde.toList
import dev.evo.elasticmagic.serde.toMap
import dev.evo.elasticmagic.toRequestParameters
import dev.evo.elasticmagic.transport.BulkRequest
import dev.evo.elasticmagic.transport.ApiRequest
import dev.evo.elasticmagic.transport.Method
import dev.evo.elasticmagic.transport.Parameters

class MultiSearchQueryCompiler(
    features: ElasticsearchFeatures,
    private val searchQueryCompiler: SearchQueryCompiler
) : BaseCompiler(features) {
    fun compile(
        serde: Serde.OneLineJson, input: List<SearchQueryWithIndex<*>>
    ): BulkRequest<MultiSearchQueryResult> {
        val preparedQueries = mutableListOf<PreparedSearchQuery<*>>()
        val body = mutableListOf<ObjectCtx>()
        for (query in input) {
            val preparedQuery = query.searchQuery.prepare()
                .also(preparedQueries::add)
            val compiledQuery = searchQueryCompiler.compile(
                serde, preparedQuery, query.indexName
            )
            val header = serde.serializer.obj {
                searchQueryCompiler.visit(this, compiledQuery.parameters)
                field("index", query.indexName)
            }
            body.add(header)
            body.add(compiledQuery.body ?: serde.serializer.obj {})
        }
        return BulkRequest(
            method = Method.POST,
            path = "_msearch",
            parameters = Parameters(),
            body = body,
            serde = serde,
            processResponse = { ctx ->
                val took = ctx.longOrNull("took")
                val responsesCtx = ctx.array("responses")
                val preparedQueriesIter = preparedQueries.iterator()
                val results = buildList {
                    responsesCtx.forEachObj { respCtx ->
                        add(
                            searchQueryCompiler.processResult(respCtx, preparedQueriesIter.next())
                        )
                    }
                }
                MultiSearchQueryResult(
                    took = took,
                    responses = results,
                )
            }
        )
    }
}

open class SearchQueryCompiler(
    features: ElasticsearchFeatures,
) : BaseCompiler(features) {
    interface Visitable<T: Serializer.Ctx> {
        fun accept(ctx: T, compiler: SearchQueryCompiler)
    }

    fun <S: BaseDocSource> compile(
        serde: Serde, input: PreparedSearchQuery<S>, indexName: String
    ): ApiRequest<SearchQueryResult<S>> {
        val body = serde.serializer.obj {
            visit(this, input)
        }
        return ApiRequest(
            method = Method.POST,
            path = "$indexName/_search",
            parameters = input.params.toRequestParameters(),
            body = body,
            serde = serde,
            processResponse = { ctx ->
                processResult(ctx, input)
            }
        )
    }

    fun <S: BaseDocSource> compile(
        serde: Serde, input: SearchQueryWithIndex<S>
    ): ApiRequest<SearchQueryResult<S>> {
        return compile(serde, input.searchQuery.prepare(), input.indexName)
    }

    @Suppress("ComplexMethod")
    fun visit(ctx: ObjectCtx, searchQuery: PreparedSearchQuery<*>) {
        val query = searchQuery.query?.reduce()
        val filteredQuery = if (searchQuery.filters.isNotEmpty()) {
            if (query != null) {
                Bool(must = listOf(query), filter = searchQuery.filters)
            } else {
                Bool.filter(searchQuery.filters)
            }
        } else {
            query
        }
        if (filteredQuery != null) {
            ctx.obj("query") {
                visit(this, filteredQuery)
            }
        }
        if (searchQuery.postFilters.isNotEmpty()) {
            ctx.obj("post_filter") {
                val postFilterExpr = if (searchQuery.postFilters.size == 1) {
                    searchQuery.postFilters[0]
                } else {
                    Bool.filter(searchQuery.postFilters)
                }
                visit(this, postFilterExpr)
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
                visit(this, searchQuery.sorts)
            }
        }
        if (searchQuery.trackScores != null) {
            ctx.field("track_scores", searchQuery.trackScores)
        }
        if (searchQuery.trackTotalHits != null && features.supportsTrackingOfTotalHits) {
            ctx.field("track_total_hits", searchQuery.trackTotalHits)
        }
        if (searchQuery.source != null) {
            visit(ctx, searchQuery.source)
        }
        if (searchQuery.fields.isNotEmpty()) {
            ctx.array("fields") {
                visit(this, searchQuery.fields)
            }
        }
        if (searchQuery.docvalueFields.isNotEmpty()) {
            ctx.array("docvalue_fields") {
                visit(this, searchQuery.docvalueFields)
            }
        }
        if (searchQuery.storedFields.isNotEmpty()) {
            ctx.array("stored_fields") {
                visit(this, searchQuery.storedFields)
            }
        }
        if (searchQuery.scriptFields.isNotEmpty()) {
            ctx.obj("script_fields") {
                for ((fieldName, script) in searchQuery.scriptFields) {
                    obj(fieldName) {
                        obj("script") {
                            visit(this, script)
                        }
                    }
                }
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
        if (searchQuery.extensions.isNotEmpty()) {
            ctx.obj("ext") {
                for (ext in searchQuery.extensions) {
                    visit(this, ext)
                }
            }
        }
    }

    fun visit(ctx: ObjectCtx, expression: Expression<ObjectCtx>) {
        expression.accept(ctx, this)
    }

    fun visit(ctx: ArrayCtx, expression: Expression<ArrayCtx>) {
        expression.accept(ctx, this)
    }

    override fun dispatch(ctx: ArrayCtx, value: Any?) {
        when (value) {
            is ObjExpression -> ctx.obj {
                visit(this, value)
            }
            is ArrayExpression -> {
                visit(ctx, value)
            }
            is ToValue<*> -> {
                ctx.value(value.toValue())
            }
            else -> super.dispatch(ctx, value)
        }
    }

    override fun dispatch(ctx: ObjectCtx, name: String, value: Any?) {
        when (value) {
            is ObjExpression -> ctx.obj(name) {
                visit(this, value)
            }
            is ArrayExpression -> ctx.array(name) {
                visit(this, value)
            }
            is ToValue<*> -> {
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

        val rawHits = rawHitsData.arrayOrNull("hits")
        val hits = buildList {
            rawHits?.forEachObj { rawHit ->
                add(processSearchHit(rawHit, preparedSearchQuery))
            }
        }

        val rawAggs = ctx.objOrNull("aggregations")
        val aggResults = buildMap {
            if (rawAggs != null) {
                for ((aggName, agg) in preparedSearchQuery.aggregations) {
                    put(aggName, agg.processResult(rawAggs.obj(aggName)))
                }
            }
        }

        return SearchQueryResult(
            // TODO: Flag to add raw result
            null,
            took = ctx.long("took"),
            timedOut = ctx.boolean("timed_out"),
            totalHits = totalHits,
            totalHitsRelation = totalHitsRelation,
            maxScore = rawHitsData.floatOrNull("max_score"),
            hits = hits,
            aggs = aggResults,
        )
    }

    private fun <S: BaseDocSource> processSearchHit(
        rawHit: Deserializer.ObjectCtx,
        preparedSearchQuery: PreparedSearchQuery<S>,
    ): SearchHit<S> {
        val source = rawHit.objOrNull("_source")?.let { rawSource ->
            preparedSearchQuery.docSourceFactory(rawHit).apply {
                // TODO: Don't convert to a map
                fromSource(rawSource.toMap())
            }
        }
        val fields = rawHit.objOrNull("fields").let { rawFields ->
            val fields = buildMap {
                rawFields?.forEachArray { fieldName, fieldValues ->
                    put(fieldName, fieldValues.toList().filterNotNull())
                }
            }
            SearchHit.Fields(fields)
        }
        val rawSort = rawHit.arrayOrNull("sort")
        val sort = buildList {
            rawSort?.forEach { sortValue ->
                add(sortValue)
            }
        }
        return SearchHit(
            index = rawHit.string("_index"),
            type = rawHit.stringOrNull("_type") ?: "_doc",
            id = rawHit.string("_id"),
            routing = rawHit.stringOrNull("_routing"),
            version = rawHit.longOrNull("_version"),
            seqNo = rawHit.longOrNull("_seq_no"),
            primaryTerm = rawHit.longOrNull("_primary_term"),
            score = rawHit.floatOrNull("_score"),
            sort = sort.ifEmpty { null },
            source = source,
            fields = fields,
        )
    }
}
