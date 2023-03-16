package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

/**
 * Returns documents that contains exact @param[term] in a @param[field].
 *
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/7.15/query-dsl-term-query.html>
 */
data class Term<T>(
    val field: FieldOperations<T>,
    val term: T & Any,
    val boost: Float? = null,
) : QueryExpression {
    override val name = "term"

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: BaseSearchQueryCompiler
    ) {
        if (boost != null) {
            ctx.obj(field.getQualifiedFieldName()) {
                field("value", field.serializeTerm(term))
                field("boost", boost)
            }
        } else {
            ctx.field(field.getQualifiedFieldName(), field.serializeTerm(term))
        }
    }
}

/**
 * Returns documents that contains one or more exact @param[terms] in a @param[field].
 *
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/7.15/query-dsl-terms-query.html>
 */
data class Terms<T>(
    val field: FieldOperations<T>,
    val terms: List<T & Any>,
    val boost: Float? = null,
) : QueryExpression {
    override val name = "terms"

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: BaseSearchQueryCompiler
    ) {
        ctx.array(field.getQualifiedFieldName()) {
            for (term in terms) {
                value(field.serializeTerm(term))
            }
        }
        if (boost != null) {
            ctx.field("boost", boost)
        }
    }
}

/**
 * Returns documents which ID is in @param[values].
 *
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/7.15/query-dsl-ids-query.html>
 */
data class Ids(
    val values: List<String>,
    val boost: Float? = null,
) : QueryExpression {
    override val name = "ids"

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
        ctx.array("values") {
            compiler.visit(this, values)
        }
        ctx.fieldIfNotNull("boost", boost)
    }
}

/**
 * Returns documents which have a value for a @param[field].
 *
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/7.15/query-dsl-exists-query.html>
 */
data class Exists(
    val field: FieldOperations<*>,
    val boost: Float? = null,
) : QueryExpression {
    override val name = "exists"

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: BaseSearchQueryCompiler
    ) {
        ctx.field("field", field.getQualifiedFieldName())
        ctx.fieldIfNotNull("boost", boost)
    }
}
/**
 * Returns documents that contain @param[field] values within a range specified by
 * parameters: @param[gt], @param[gte], @param[lt], @param[lte].
 *
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/7.15/query-dsl-range-query.html>
 */

data class Range<T>(
    val field: FieldOperations<T>,
    val gt: T? = null,
    val gte: T? = null,
    val lt: T? = null,
    val lte: T? = null,
    val relation: Relation? = null,
    val boost: Float? = null,
) : QueryExpression {
    override val name = "range"

    enum class Relation : ToValue<String> {
        INTERSECTS, CONTAINS, WITHIN;

        override fun toValue(): String = name
    }

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: BaseSearchQueryCompiler
    ) {
        ctx.obj(field.getQualifiedFieldName()) {
            if (gt != null) {
                field("gt", field.serializeTerm(gt))
            }
            if (gte != null) {
                field("gte", field.serializeTerm(gte))
            }
            if (lt != null) {
                field("lt", field.serializeTerm(lt))
            }
            if (lte != null) {
                field("lte", field.serializeTerm(lte))
            }
            fieldIfNotNull("relation", relation?.toValue())
            fieldIfNotNull("boost", boost)
        }
    }
}
