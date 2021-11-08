package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.types.FieldType

/**
 * Marker interface for field formatting.
 */
interface FieldFormat {
    companion object {
        operator fun invoke(field: FieldOperations<*>, format: String? = null): FieldFormat {
            return Impl(field, format)
        }
    }

    /**
     * Represents field formatting. Used in [dev.evo.elasticmagic.SearchQuery.fields] and
     * [dev.evo.elasticmagic.SearchQuery.docvalueFields].
     *
     * Format examples:
     * - date fields: "epoch_millis", "yyyy", "yyyy-MM-dd", "yyyy-MM-dd'T'HH:mm:ss" etc.
     *   See more: <https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-date-format.html>
     * - floating point fields (supported only in docvalues): "0", "0.0", "0.00" etc.
     */
    data class Impl(
        val field: FieldOperations<*>,
        val format: String? = null,
    ) : FieldFormat, ArrayExpression {
        override fun clone() = copy()

        override fun accept(ctx: Serializer.ArrayCtx, compiler: SearchQueryCompiler) {
            if (format != null) {
                ctx.obj {
                    field("field", field.getQualifiedFieldName())
                    field("format", format)
                }
            } else {
                ctx.value(field.getQualifiedFieldName())
            }
        }
    }
}

/**
 * Holds field operations shortcuts.
 */
interface FieldOperations<T> : Named, FieldFormat, Sort {
    fun getFieldType(): FieldType<*, T>

    fun serializeTerm(v: T): Any {
        return getFieldType().serializeTerm(v)
    }

    fun deserializeTerm(v: Any): T {
        return getFieldType().deserializeTerm(v)
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

    fun format(format: String? = null): FieldFormat = FieldFormat.Impl(this, format)
}

fun FieldOperations<String>.match(text: String): QueryExpression = Match(this, text)
