package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.MultiSearchQueryResult
import dev.evo.elasticmagic.PreparedSearchQuery
import dev.evo.elasticmagic.SearchHit
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.SearchQueryWithIndex
import dev.evo.elasticmagic.aggs.AggregationResult
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.query.ArrayExpression
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.Expression
import dev.evo.elasticmagic.query.ObjExpression
import dev.evo.elasticmagic.query.ToValue
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx
import dev.evo.elasticmagic.serde.toList
import dev.evo.elasticmagic.serde.toMap
import dev.evo.elasticmagic.toRequestParameters
import dev.evo.elasticmagic.transport.BulkRequest
import dev.evo.elasticmagic.transport.JsonRequest
import dev.evo.elasticmagic.transport.Method
import dev.evo.elasticmagic.transport.Parameters

class MultiSearchQueryCompiler(
    esVersion: ElasticsearchVersion,
    private val searchQueryCompiler: SearchQueryCompiler
) : BaseCompiler(esVersion) {
    fun compile(
        serializer: Serializer, input: List<SearchQueryWithIndex<*>>
    ): BulkRequest<MultiSearchQueryResult> {
        val preparedQueries = mutableListOf<PreparedSearchQuery<*>>()
        val body = mutableListOf<ObjectCtx>()
        for (query in input) {
            val preparedQuery = query.searchQuery.prepare()
                .also(preparedQueries::add)
            val compiledQuery = searchQueryCompiler.compile(serializer, preparedQuery, query.indexName)
            val header = serializer.obj {
                searchQueryCompiler.visit(this, compiledQuery.parameters)
                field("index", query.indexName)
            }
            body.add(header)
            body.add(compiledQuery.body ?: serializer.obj {})
        }
        return BulkRequest(
            method = Method.POST,
            path = "_msearch",
            parameters = Parameters(),
            body = body,
            processResult = { ctx ->
                val took = ctx.longOrNull("took")
                val responsesCtx = ctx.array("responses")
                val preparedQueriesIter = preparedQueries.iterator()
                val results = mutableListOf<SearchQueryResult<*>>()
                while (responsesCtx.hasNext()) {
                    val responseCtx = responsesCtx.obj()
                    results.add(
                        searchQueryCompiler.processResult(responseCtx, preparedQueriesIter.next())
                    )
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
    esVersion: ElasticsearchVersion,
) : BaseCompiler(esVersion) {

    interface Visitable<T: Serializer.Ctx> {
        fun accept(ctx: T, compiler: SearchQueryCompiler)
    }

    fun <S: BaseDocSource> compile(
        serializer: Serializer, input: PreparedSearchQuery<S>, indexName: String
    ): JsonRequest<SearchQueryResult<S>> {
        val body = serializer.obj {
            visit(this, input)
        }
        return JsonRequest(
            method = Method.POST,
            path = "$indexName/_search",
            parameters = input.params.toRequestParameters(),
            body = body,
            processResult = { ctx ->
                processResult(ctx, input)
            }
        )
    }

    fun <S: BaseDocSource> compile(
        serializer: Serializer, input: SearchQueryWithIndex<S>
    ): JsonRequest<SearchQueryResult<S>> {
        return compile(serializer, input.searchQuery.prepare(), input.indexName)
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
                visit(this, searchQuery.sorts)
            }
        }
        if (searchQuery.trackScores != null) {
            ctx.field("track_scores", searchQuery.trackScores)
        }
        if (searchQuery.trackTotalHits != null) {
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
        val hits = mutableListOf<SearchHit<S>>()
        val rawHits = rawHitsData.arrayOrNull("hits")
        if (rawHits != null) {
            while (rawHits.hasNext()) {
                val rawHit = rawHits.obj()
                hits.add(
                    processSearchHit(rawHit, preparedSearchQuery)
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
            val fields = mutableMapOf<String, List<Any>>()
            if (rawFields != null) {
                val fieldsIter = rawFields.iterator()
                while (fieldsIter.hasNext()) {
                    val (fieldName, fieldValues) = fieldsIter.array()
                    fields[fieldName] = fieldValues.toList().filterNotNull()
                }
            }
            SearchHit.Fields(fields)
        }
        val rawSort = rawHit.arrayOrNull("sort")
        val sort = mutableListOf<Any>()
        if (rawSort != null) {
            while (rawSort.hasNext()) {
                sort.add(rawSort.any())
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
            score = rawHit.doubleOrNull("_score"),
            sort = sort.ifEmpty { null },
            source = source,
            fields = fields,
        )
    }
}
