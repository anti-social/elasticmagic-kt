package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
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

interface Expression<T: Serializer.Ctx> : BaseSearchQueryCompiler.Visitable<T> {
    fun clone(): Expression<T>

    fun children(): Iterator<Expression<*>>? {
        return null
    }

    fun rewrite(newNode: QueryExpressionNode<*>): Expression<T> {
        return this
    }

    fun reduce(): Expression<T>? {
        return this
    }
}

internal inline fun <T> replaceNodeInExpressions(
    expressions: List<T>,
    nodeReplacer: (T) -> T,
    block: (List<T>) -> Unit,
) {
    var changed = false
    val newExpressions = expressions.map { expr ->
        val newExpr = nodeReplacer(expr)
        if (newExpr !== expr) {
            changed = true
            newExpr
        } else {
            expr
        }
    }
    if (changed) {
        block(newExpressions)
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

    override fun accept(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
        ctx.obj(name) {
            visit(this, compiler)
        }
    }

    fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler)
}

interface QueryExpression : NamedExpression {
    override fun clone(): QueryExpression

    override fun rewrite(newNode: QueryExpressionNode<*>): QueryExpression {
        return this
    }

    override fun reduce(): QueryExpression? {
        return this
    }
}

data class NodeHandle<T: QueryExpression>(val name: String? = null) {
    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}

data class QueryExpressionNode<T: QueryExpression>(
    val handle: NodeHandle<T>,
    val expression: T,
) : QueryExpression {
    override val name = expression.name

    override fun children(): Iterator<Expression<*>>? {
        return expression.children()
    }

    override fun clone(): QueryExpressionNode<T> = copy()

    override fun rewrite(newNode: QueryExpressionNode<*>): QueryExpression {
        if (handle != newNode.handle) {
            return this
        }
        return newNode
    }

    override fun reduce(): QueryExpression? {
        return expression.reduce()
    }

    override fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
        expression.visit(ctx, compiler)
    }
}

interface SearchExt : NamedExpression
