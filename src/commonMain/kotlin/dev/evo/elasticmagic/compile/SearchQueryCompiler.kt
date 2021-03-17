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
                visit(this, searchQuery.postFilters)
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
    }

    fun visit(ctx: ObjectCtx, expression: Expression) {
        expression.accept(ctx, this)
    }

    override fun dispatch(ctx: ArrayCtx, value: Any?) {
        when (value) {
            is Expression -> ctx.obj {
                visit(this, value)
            }
            is ExpressionValue -> {
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
            is ExpressionValue -> {
                ctx.field(name, value.toValue())
            }
            else -> super.dispatch(ctx, name, value)
        }
    }
}
