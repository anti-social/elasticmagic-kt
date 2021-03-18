package dev.evo.elasticmagic

interface Named : ExpressionValue {
    fun getFieldName(): String

    fun getQualifiedFieldName(): String

    override fun toValue(): Any {
        return getQualifiedFieldName()
    }
}

/**
 * Holds field operations shortcuts.
 */
interface FieldOperations : Named {
    fun eq(term: Any?): QueryExpression {
        if (term == null) {
            return Bool.mustNot(Exists(this))
        }
        return Term(this, term)
    }

    fun ne(term: Any?): QueryExpression {
        if (term == null) {
            return Exists(this)
        }
        return Bool.mustNot(Term(this, term))
    }

    fun match(text: String): QueryExpression = Match(this, text)

    fun range(
        gt: Any? = null, gte: Any? = null, lt: Any? = null, lte: Any? = null
    ): QueryExpression = Range(this, gt = gt, gte = gte, lt = lt, lte = lte)
    fun gt(other: Any?): QueryExpression = range(gt = other)
    fun gte(other: Any?): QueryExpression = range(gte = other)
    fun lt(other: Any?): QueryExpression = range(lt = other)
    fun lte(other: Any?): QueryExpression = range(lte = other)
}
