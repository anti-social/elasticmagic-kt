package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

data class Term<T>(
    val field: FieldOperations<T>,
    val term: T,
    val boost: Double? = null,
) : QueryExpression {
    override val name = "term"

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
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

data class Terms<T>(
    val field: FieldOperations<T>,
    val terms: List<T>,
    val boost: Double? = null,
) : QueryExpression {
    override val name = "terms"

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
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

data class Ids(
    val values: List<String>,
    val boost: Double? = null,
) : QueryExpression {
    override val name = "ids"

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.array("values") {
            compiler.visit(this, values)
        }
        ctx.fieldIfNotNull("boost", boost)
    }
}

data class Exists(
    val field: FieldOperations<*>,
    val boost: Double? = null,
) : QueryExpression {
    override val name = "exists"

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.field("field", field.getQualifiedFieldName())
        ctx.fieldIfNotNull("boost", boost)
    }
}

data class Range<T>(
    val field: FieldOperations<T>,
    val gt: T? = null,
    val gte: T? = null,
    val lt: T? = null,
    val lte: T? = null,
    val boost: Double? = null,
) : QueryExpression {
    override val name = "range"

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
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
            ctx.fieldIfNotNull("boost", boost)
        }
    }
}
