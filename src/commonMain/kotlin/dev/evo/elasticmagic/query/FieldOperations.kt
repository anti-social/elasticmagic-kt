package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.doc.FieldType

/**
 * Holds field operations shortcuts.
 */
interface FieldOperations : Named {
    fun getFieldType(): FieldType<*>

    fun serializeTerm(v: Any?): Any? {
        return getFieldType().serializeTerm(v)
    }

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

    fun contains(terms: List<Any>): QueryExpression {
        return Terms(this, terms)
    }

    fun match(text: String): QueryExpression = Match(this, text)

    fun range(
        gt: Any? = null, gte: Any? = null, lt: Any? = null, lte: Any? = null
    ): QueryExpression = Range(this, gt = gt, gte = gte, lt = lt, lte = lte)
    fun gt(other: Any?): QueryExpression = range(gt = other)
    fun gte(other: Any?): QueryExpression = range(gte = other)
    fun lt(other: Any?): QueryExpression = range(lt = other)
    fun lte(other: Any?): QueryExpression = range(lte = other)

    fun asc(): Sort = Sort(this, order = Sort.Order.ASC)
    fun desc(): Sort = Sort(this, order = Sort.Order.DESC)
}
