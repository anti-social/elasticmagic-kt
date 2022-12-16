package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
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

        override fun accept(ctx: Serializer.ArrayCtx, compiler: BaseSearchQueryCompiler) {
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
 * Boosted fields can be used in the [MultiMatch] query expression.
 */
interface BoostedField : ToValue<String> {
    companion object {
        operator fun invoke(field: FieldOperations<String>, boost: Float): BoostedField {
            return object : BoostedField {
                override fun toValue(): String {
                    return "${field.getQualifiedFieldName()}^$boost"
                }
            }
        }
    }
}

/**
 * Holds field operations shortcuts.
 */
interface FieldOperations<T> : Named, FieldFormat, BoostedField, Sort {
    fun getFieldType(): FieldType<*, T>

    fun serializeTerm(v: T & Any): Any {
        return getFieldType().serializeTerm(v)
    }

    fun deserializeTerm(v: Any): T {
        return getFieldType().deserializeTerm(v)
    }

    fun eq(term: T?, boost: Float? = null): QueryExpression {
        if (term == null) {
            return Bool.mustNot(Exists(this, boost = boost))
        }
        return Term(this, term, boost = boost)
    }

    infix fun eq(term: T?): QueryExpression = eq(term, boost = null)

    fun ne(term: T?, boost: Float? = null): QueryExpression {
        if (term == null) {
            return Exists(this)
        }
        return Bool.mustNot(Term(this, term))
    }

    infix fun ne(term: T?): QueryExpression = ne(term, boost = null)

    infix fun oneOf(terms: List<T & Any>): QueryExpression {
        return Terms(this, terms)
    }

    fun range(
        gt: T? = null, gte: T? = null, lt: T? = null, lte: T? = null, boost: Float? = null
    ): Range<T> = Range(this, gt = gt, gte = gte, lt = lt, lte = lte, boost = boost)
    infix fun gt(other: T?): Range<T> = range(gt = other)
    infix fun gte(other: T?): Range<T> = range(gte = other)
    infix fun lt(other: T?): Range<T> = range(lt = other)
    infix fun lte(other: T?): Range<T> = range(lte = other)

    fun asc(
        mode: Sort.Mode? = null,
        numericType: Sort.NumericType? = null,
        missing: Sort.Missing? = null,
        unmappedType: FieldType<*, *>? = null,
        nested: Sort.Nested? = null,
    ): Sort {
        return Sort(
            this,
            order = Sort.Order.ASC,
            mode = mode,
            numericType = numericType,
            missing = missing,
            unmappedType = unmappedType,
            nested = nested,
        )
    }
    fun desc(
        mode: Sort.Mode? = null,
        numericType: Sort.NumericType? = null,
        missing: Sort.Missing? = null,
        unmappedType: FieldType<*, *>? = null,
        nested: Sort.Nested? = null,
    ): Sort {
        return Sort(
            this,
            order = Sort.Order.DESC,
            mode = mode,
            numericType = numericType,
            missing = missing,
            unmappedType = unmappedType,
            nested = nested,
        )
    }

    fun format(format: String? = null): FieldFormat = FieldFormat.Impl(this, format)
}

fun FieldOperations<String>.match(text: String): QueryExpression = Match(this, text)

/**
 * A shortcut to get boosted field.
 */
fun FieldOperations<String>.boost(boost: Float): BoostedField = BoostedField(this, boost)
