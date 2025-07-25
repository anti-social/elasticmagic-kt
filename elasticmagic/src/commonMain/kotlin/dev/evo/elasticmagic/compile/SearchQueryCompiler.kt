package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.AsyncResult
import dev.evo.elasticmagic.BulkError
import dev.evo.elasticmagic.BulkScrollFailure
import dev.evo.elasticmagic.BulkScrollRetries
import dev.evo.elasticmagic.CountResult
import dev.evo.elasticmagic.DeleteByQueryPartialResult
import dev.evo.elasticmagic.DeleteByQueryResult
import dev.evo.elasticmagic.MultiSearchQueryResult
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.PreparedSearchQuery
import dev.evo.elasticmagic.SearchHit
import dev.evo.elasticmagic.SearchQuery
import dev.evo.elasticmagic.SearchQueryResult
import dev.evo.elasticmagic.ToValue
import dev.evo.elasticmagic.UpdateByQueryPartialResult
import dev.evo.elasticmagic.UpdateByQueryResult
import dev.evo.elasticmagic.WithIndex
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.query.ArrayExpression
import dev.evo.elasticmagic.query.Bool
import dev.evo.elasticmagic.query.Expression
import dev.evo.elasticmagic.query.ObjExpression
import dev.evo.elasticmagic.query.StoredField
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serde
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx
import dev.evo.elasticmagic.serde.forEachObj
import dev.evo.elasticmagic.toRequestParameters
import dev.evo.elasticmagic.transport.ApiRequest
import dev.evo.elasticmagic.transport.BulkRequest
import dev.evo.elasticmagic.transport.Method
import dev.evo.elasticmagic.transport.Parameters
import dev.evo.elasticmagic.util.toTimeoutString

abstract class BaseSearchQueryCompiler(
    features: ElasticsearchFeatures,
) : BaseCompiler(features) {

    interface Visitable<T : Serializer.Ctx> {
        fun accept(ctx: T, compiler: BaseSearchQueryCompiler)
    }

    open fun visit(ctx: ObjectCtx, searchQuery: PreparedSearchQuery) {
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
        if (searchQuery.terminateAfter != null) {
            ctx.field("terminate_after", searchQuery.terminateAfter)
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
}

open class SearchQueryCompiler(
    features: ElasticsearchFeatures,
) : BaseSearchQueryCompiler(features) {

    @Suppress("CyclomaticComplexMethod")
    fun visit(ctx: ObjectCtx, searchQuery: SearchQuery.Search<*>) {
        super.visit(ctx, searchQuery)

        if (searchQuery.knn != null) {
            visit(ctx, searchQuery.knn)
        }

        if (searchQuery.aggregations.isNotEmpty()) {
            ctx.obj("aggs") {
                visit(this, searchQuery.aggregations)
            }
        }

        if (searchQuery.timeout != null) {
            ctx.field("timeout", searchQuery.timeout.toTimeoutString())
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
            val firstStoredField = searchQuery.storedFields.first()
            if (firstStoredField === StoredField.None) {
                // Elasticsearch 8.x requires _none_ to be a scalar value
                ctx.field("stored_fields", firstStoredField.toValue())
            } else {
                ctx.array("stored_fields") {
                    visit(this, searchQuery.storedFields)
                }
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
        if (searchQuery.runtimeMappings.isNotEmpty()) {
            if (features.runtimeFieldsSearchQueryName == null) {
                throw CompilationException(
                    "Runtime fields are not supported by this Elasticsearch/Opensearch version"
                )
            }
            ctx.obj(features.runtimeFieldsSearchQueryName) {
                for ((fieldName, field) in searchQuery.runtimeMappings) {
                    obj(fieldName) {
                        field("type", field.getFieldType().name)
                        obj("script") {
                            visit(this, field.script)
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
        if (searchQuery.extensions.isNotEmpty()) {
            ctx.obj("ext") {
                for (ext in searchQuery.extensions) {
                    visit(this, ext)
                }
            }
        }
    }

    fun <S : BaseDocSource> compile(
        serde: Serde, searchQuery: WithIndex<SearchQuery.Search<S>>
    ): ApiRequest<SearchQueryResult<S>> {
        val body = serde.serializer.obj {
            visit(this, searchQuery.request)
        }
        return ApiRequest(
            method = Method.POST,
            path = "${searchQuery.indexName}/_search",
            parameters = searchQuery.request.params.toRequestParameters(),
            body = body,
            serde = serde,
            processResponse = { resp ->
                processResult(resp.content, searchQuery.request)
            }
        )
    }

    fun <S : BaseDocSource> processResult(
        ctx: Deserializer.ObjectCtx,
        preparedSearchQuery: SearchQuery.Search<S>,
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
                add(SearchHit(rawHit, preparedSearchQuery.docSourceFactory))
            }
        }

        val rawAggs = ctx.objOrNull("aggregations")
        val aggResults = buildMap {
            if (rawAggs != null) {
                for ((aggName, agg) in preparedSearchQuery.aggregations) {
                    put(aggName, agg.processResult(rawAggs.obj(aggName), preparedSearchQuery.docSourceFactory))
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
}

class CountQueryCompiler(
    features: ElasticsearchFeatures,
) : BaseSearchQueryCompiler(features) {
    fun compile(
        serde: Serde, countQuery: WithIndex<SearchQuery.Count>
    ): ApiRequest<CountResult> {
        val body = serde.serializer.obj {
            visit(this, countQuery.request)
        }
        return ApiRequest(
            method = Method.POST,
            path = "${countQuery.indexName}/_count",
            parameters = countQuery.request.params.toRequestParameters(),
            body = body,
            serde = serde,
            processResponse = { resp ->
                CountResult(resp.content.long("count"))
            }
        )
    }
}

open class BaseUpdateByQueryCompiler(
    features: ElasticsearchFeatures,
) : BaseSearchQueryCompiler(features) {
    fun processPartialResult(ctx: Deserializer.ObjectCtx): UpdateByQueryPartialResult {
        return UpdateByQueryPartialResult(
            total = ctx.long("total"),
            updated = ctx.long("updated"),
            created = ctx.long("created"),
            deleted = ctx.long("deleted"),
            batches = ctx.int("batches"),
            versionConflicts = ctx.long("version_conflicts"),
            noops = ctx.long("noops"),
            retries = ctx.obj("retries").let { retries ->
                BulkScrollRetries(
                    search = retries.long("search"),
                    bulk = retries.long("bulk"),
                )
            },
            throttledMillis = ctx.long("throttled_millis"),
            requestsPerSecond = ctx.float("requests_per_second"),
            throttledUntilMillis = ctx.long("throttled_until_millis"),
        )
    }

    fun processBulkScrollFailure(ctx: Deserializer.ObjectCtx): BulkScrollFailure {
        return BulkScrollFailure(
            id = ctx.string("id"),
            index = ctx.string("index"),
            type = ctx.stringOrNull("type"),
            status = ctx.int("status"),
            cause = processBulkError(ctx.obj("cause")),
        )
    }

    fun processBulkError(ctx: Deserializer.ObjectCtx): BulkError {
        return BulkError(
            type = ctx.string("type"),
            reason = ctx.string("reason"),
            index = ctx.string("index"),
            indexUuid = ctx.string("index_uuid"),
            shard = ctx.intOrNull("shard"),
        )
    }
}

class UpdateByQueryCompiler(
    features: ElasticsearchFeatures,
) : BaseUpdateByQueryCompiler(features) {
    private val apiEndpoint = "_update_by_query"

    fun visit(ctx: ObjectCtx, updateByQuery: WithIndex<SearchQuery.Update>) {
        super.visit(ctx, updateByQuery.request)
        if (updateByQuery.request.script != null) {
            ctx.obj("script") {
                visit(this, updateByQuery.request.script)
            }
        }
    }

    fun compile(
        serde: Serde, updateByQuery: WithIndex<SearchQuery.Update>
    ): ApiRequest<UpdateByQueryResult> {
        val body = serde.serializer.obj {
            visit(this, updateByQuery)
        }
        return ApiRequest(
            method = Method.POST,
            path = "${updateByQuery.indexName}/$apiEndpoint",
            parameters = updateByQuery.request.params.toRequestParameters(),
            body = body,
            serde = serde,
            processResponse = { resp ->
                processResult(resp.content)
            }
        )
    }

    fun processResult(ctx: Deserializer.ObjectCtx): UpdateByQueryResult {
        return UpdateByQueryResult(
            took = ctx.long("took"),
            timedOut = ctx.boolean("timed_out"),
            total = ctx.long("total"),
            updated = ctx.long("updated"),
            deleted = ctx.long("deleted"),
            batches = ctx.int("batches"),
            versionConflicts = ctx.long("version_conflicts"),
            noops = ctx.long("noops"),
            retries = ctx.obj("retries").let { retries ->
                BulkScrollRetries(
                    search = retries.long("search"),
                    bulk = retries.long("bulk"),
                )
            },
            throttledMillis = ctx.long("throttled_millis"),
            requestsPerSecond = ctx.float("requests_per_second"),
            throttledUntilMillis = ctx.long("throttled_until_millis"),
            failures = buildList {
                ctx.array("failures").forEachObj { failure ->
                    add(processBulkScrollFailure(failure))
                }
            }
        )
    }

    fun compileAsync(
        serde: Serde, updateByQuery: WithIndex<SearchQuery.Update>
    ): ApiRequest<AsyncResult<UpdateByQueryPartialResult, UpdateByQueryResult?>> {
        val body = serde.serializer.obj {
            visit(this, updateByQuery)
        }
        val params = Params(updateByQuery.request.params, "wait_for_completion" to false)
        return ApiRequest(
            method = Method.POST,
            path = "${updateByQuery.indexName}/$apiEndpoint",
            parameters = params.toRequestParameters(),
            body = body,
            serde = serde,
            processResponse = { resp ->
                AsyncResult(
                    resp.content.string("task"),
                    { ctx -> processPartialResult(ctx.obj("task").obj("status")) },
                    { ctx -> ctx.objOrNull("response")?.let(::processResult) },
                )
            }
        )
    }
}

class DeleteByQueryCompiler(
    features: ElasticsearchFeatures,
) : BaseUpdateByQueryCompiler(features) {
    private val apiEndpoint = "_delete_by_query"

    fun visit(ctx: ObjectCtx, deleteByQuery: WithIndex<SearchQuery.Delete>) {
        super.visit(ctx, deleteByQuery.request)
    }

    fun compile(
        serde: Serde, deleteByQuery: WithIndex<SearchQuery.Delete>
    ): ApiRequest<DeleteByQueryResult> {
        val body = serde.serializer.obj {
            visit(this, deleteByQuery)
        }
        return ApiRequest(
            method = Method.POST,
            path = "${deleteByQuery.indexName}/$apiEndpoint",
            parameters = deleteByQuery.request.params.toRequestParameters(),
            body = body,
            serde = serde,
            processResponse = { resp ->
                processResult(resp.content)
            }
        )
    }

    fun processResult(ctx: Deserializer.ObjectCtx): DeleteByQueryResult {
        return DeleteByQueryResult(
            took = ctx.long("took"),
            timedOut = ctx.boolean("timed_out"),
            total = ctx.long("total"),
            deleted = ctx.long("deleted"),
            batches = ctx.int("batches"),
            versionConflicts = ctx.long("version_conflicts"),
            noops = ctx.long("noops"),
            retries = ctx.obj("retries").let { retries ->
                BulkScrollRetries(
                    search = retries.long("search"),
                    bulk = retries.long("bulk"),
                )
            },
            throttledMillis = ctx.long("throttled_millis"),
            requestsPerSecond = ctx.float("requests_per_second"),
            throttledUntilMillis = ctx.long("throttled_until_millis"),
            failures = buildList {
                ctx.array("failures").forEachObj { failure ->
                    add(processBulkScrollFailure(failure))
                }
            }
        )
    }

    fun compileAsync(
        serde: Serde, deleteByQuery: WithIndex<SearchQuery.Delete>
    ): ApiRequest<AsyncResult<DeleteByQueryPartialResult, DeleteByQueryResult?>> {
        val body = serde.serializer.obj {
            visit(this, deleteByQuery)
        }
        val params = Params(deleteByQuery.request.params, "wait_for_completion" to false)
        return ApiRequest(
            method = Method.POST,
            path = "${deleteByQuery.indexName}/$apiEndpoint",
            parameters = params.toRequestParameters(),
            body = body,
            serde = serde,
            processResponse = { resp ->
                AsyncResult(
                    resp.content.string("task"),
                    { ctx -> processPartialResult(ctx.obj("task").obj("status")) },
                    { ctx -> ctx.objOrNull("response")?.let(::processResult) },
                )
            }
        )
    }
}

class MultiSearchQueryCompiler(
    features: ElasticsearchFeatures,
    private val searchQueryCompiler: SearchQueryCompiler
) : BaseCompiler(features) {

    fun compile(
        serde: Serde.OneLineJson, searchQueries: List<WithIndex<SearchQuery.Search<*>>>
    ): BulkRequest<MultiSearchQueryResult> {
        val body = mutableListOf<ObjectCtx>()
        for (query in searchQueries) {
            val compiledQuery = searchQueryCompiler.compile(serde, query)
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
            processResponse = { resp ->
                val took = resp.content.longOrNull("took")
                val responsesCtx = resp.content.array("responses")
                val preparedQueriesIter = searchQueries.iterator()
                val results = buildList {
                    responsesCtx.forEachObj { respCtx ->
                        add(
                            searchQueryCompiler.processResult(respCtx, preparedQueriesIter.next().request)
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
