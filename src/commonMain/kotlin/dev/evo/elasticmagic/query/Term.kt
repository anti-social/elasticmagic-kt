package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

data class Term(
    val field: FieldOperations,
    val term: Any,
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
                field("value", term)
                field("boost", boost)
            }
        } else {
            ctx.field(field.getQualifiedFieldName(), term)
        }
    }
}

data class Terms(
    val field: FieldOperations,
    val terms: List<Any>,
    val boost: Double? = null,
) : QueryExpression {
    override val name = "terms"

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.array(field.getQualifiedFieldName()) {
            compiler.visit(this, terms)
        }
        if (boost != null) {
            ctx.field("boost", boost)
        }
    }
}

data class Ids(
    val values: List<String>,
) : QueryExpression {
    override val name = "ids"

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.array("values") {
            compiler.visit(this, values)
        }
    }
}

data class Exists(
    val field: FieldOperations,
) : QueryExpression {
    override val name = "exists"

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.field("field", field.getQualifiedFieldName())
    }
}

data class Range(
    val field: FieldOperations,
    val gt: Any? = null,
    val gte: Any? = null,
    val lt: Any? = null,
    val lte: Any? = null,
) : QueryExpression {
    override val name = "range"

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: SearchQueryCompiler
    ) {
        ctx.obj(field.getQualifiedFieldName()) {
            if (gt != null) {
                field("gt", gt)
            }
            if (gte != null) {
                field("gte", gte)
            }
            if (lt != null) {
                field("lt", lt)
            }
            if (lte != null) {
                field("lte", lte)
            }
        }
    }
}