package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.doc.FieldType

/**
 * Holds field operations shortcuts.
 */
interface FieldOperations<T> : Named {
    fun getFieldType(): FieldType<*, T>

    fun serializeTerm(v: T): Any {
        return getFieldType().serializeTerm(v)
    }

    fun deserializeTerm(v: Any): T {
        TODO("return getFieldType().deserializeTerm(v)")
    }

    fun eq(term: T?): QueryExpression {
        if (term == null) {
            return Bool.mustNot(Exists(this))
        }
        return Term(this, term)
    }

    fun ne(term: T?): QueryExpression {
        if (term == null) {
            return Exists(this)
        }
        return Bool.mustNot(Term(this, term))
    }

    fun contains(terms: List<T>): QueryExpression {
        return Terms(this, terms)
    }

    fun range(
        gt: T? = null, gte: T? = null, lt: T? = null, lte: T? = null
    ): QueryExpression = Range(this, gt = gt, gte = gte, lt = lt, lte = lte)
    fun gt(other: T?): QueryExpression = range(gt = other)
    fun gte(other: T?): QueryExpression = range(gte = other)
    fun lt(other: T?): QueryExpression = range(lt = other)
    fun lte(other: T?): QueryExpression = range(lte = other)

    fun asc(): Sort = Sort(this, order = Sort.Order.ASC)
    fun desc(): Sort = Sort(this, order = Sort.Order.DESC)
}

fun FieldOperations<String>.match(text: String): QueryExpression = Match(this, text)
