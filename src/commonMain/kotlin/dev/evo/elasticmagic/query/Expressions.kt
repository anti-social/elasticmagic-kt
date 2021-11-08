package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

interface ToValue<T> {
    fun toValue(): T
}

interface Named : ToValue<String> {
    fun getFieldName(): String

    fun getQualifiedFieldName(): String

    override fun toValue(): String {
        return getQualifiedFieldName()
    }
}

interface Expression<T: Serializer.Ctx> : SearchQueryCompiler.Visitable<T> {
    fun clone(): Expression<T>

    fun children(): Iterator<Expression<*>>? {
        return null
    }

    fun reduce(): Expression<T>? {
        return this
    }
}

interface ObjExpression : Expression<Serializer.ObjectCtx>

interface ArrayExpression : Expression<Serializer.ArrayCtx>

internal inline fun Expression<*>.collect(process: (Expression<*>) -> Unit) {
    val stack = ArrayList<Expression<*>>()
    stack.add(this)

    while (true) {
        val currentExpression = stack.removeLastOrNull() ?: break
        process(currentExpression)

        val children = currentExpression.children()
        if (children != null) {
            for (child in children) {
                stack.add(child)
            }
        }
    }
}

interface NamedExpression : ObjExpression {
    val name: String

    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.obj(name) {
            visit(this, compiler)
        }
    }

    fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler)
}

interface QueryExpression : NamedExpression {
    override fun clone(): QueryExpression

    override fun reduce(): QueryExpression? {
        return this
    }
}

data class NodeHandle<T: QueryExpressionNode<T>>(val name: String? = null)

abstract class QueryExpressionNode<T: QueryExpressionNode<T>> : QueryExpression {
    abstract val handle: NodeHandle<T>

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        toQueryExpression().visit(ctx, compiler)
    }

    override fun reduce(): QueryExpression? {
        return toQueryExpression().reduce()
    }

    abstract fun toQueryExpression(): QueryExpression
}
