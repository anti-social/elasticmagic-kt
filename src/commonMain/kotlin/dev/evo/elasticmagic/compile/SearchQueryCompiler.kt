package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.*
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx

open class SearchQueryCompiler(
    esVersion: ElasticsearchVersion,
) : BaseCompiler<BaseSearchQuery<*, *>>(esVersion) {

    interface Visitable {
        fun accept(ctx: ObjectCtx, compiler: SearchQueryCompiler)
    }

    data class Compiled<T>(val docType: String?, override val body: T?): Compiler.Compiled<T>()

    override fun <T> compile(serializer: Serializer<T>, input: BaseSearchQuery<*, *>): Compiled<T> {
        val preparedSearchQuery = input.prepare()
        val body = serializer.buildObj {
            visit(this, preparedSearchQuery)
        }
        return Compiled(
            preparedSearchQuery.docType,
            body
        )
    }

    fun visit(ctx: ObjectCtx, searchQuery: PreparedSearchQuery<*>) {
        val query = searchQuery.query?.reduce()
        val filteredQuery: QueryExpression? = if (searchQuery.filters.isNotEmpty()) {
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
                for (filter in searchQuery.postFilters) {
                    obj {
                        visit(this, filter)
                    }
                }
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
