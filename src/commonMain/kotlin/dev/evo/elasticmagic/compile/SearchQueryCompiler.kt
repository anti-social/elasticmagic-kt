package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.*
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx

open class SearchQueryCompiler<OBJ>(
    esVersion: ElasticsearchVersion,
    private val serializer: Serializer<OBJ>,
) : BaseCompiler<BaseSearchQuery<*, *>, SearchQueryCompiler.Result<OBJ>>(esVersion) {

    interface Visitable {
        fun accept(ctx: ObjectCtx, compiler: SearchQueryCompiler<*>)
    }

    data class Result<OBJ>(val docType: String?, val body: OBJ)

    override fun compile(input: BaseSearchQuery<*, *>): Result<OBJ> {
        val preparedSearchQuery = input.prepare()
        return Result(
            preparedSearchQuery.docType,
            serializer.obj {
                visit(this, preparedSearchQuery)
            }
        )
    }

    fun visit(ctx: ObjectCtx, searchQuery: PreparedSearchQuery<*>) {
        val query = searchQuery.query
        val filteredQuery: QueryExpression? = if (searchQuery.filters.isNotEmpty()) {
            if (query != null) {
                Bool(must = listOf(query), filter = searchQuery.filters)
            } else {
                Bool(filter = searchQuery.filters)
            }
        } else {
            searchQuery.query
        }
        if (filteredQuery != null) {
            ctx.obj("query") {
                filteredQuery.accept(this, this@SearchQueryCompiler)
            }
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
            else -> super.dispatch(ctx, value)
        }
    }

    override fun dispatch(ctx: ObjectCtx, name: String, value: Any?) {
        when (value) {
            is Expression -> ctx.obj(name) {
                visit(this, value)
            }
            else -> super.dispatch(ctx, name, value)
        }
    }
}
