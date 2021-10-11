package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

data class Nested(
    val path: FieldOperations<Nothing>,
    val query: QueryExpression,
    val scoreMode: ScoreMode? = null,
    val ignoreUnmapped: Boolean? = null,
) : QueryExpression {
    override val name = "nested"

    enum class ScoreMode : ToValue {
        AVG, MAX, MIN, NONE, SUM;

        override fun toValue() = name.lowercase()
    }

    override fun clone() = copy()

    override fun visit(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        ctx.field("path", path.getQualifiedFieldName())
        ctx.obj("query") {
            compiler.visit(this, query)
        }
        ctx.fieldIfNotNull("score_mode", scoreMode?.toValue())
        ctx.fieldIfNotNull("ignore_unmapped", ignoreUnmapped)
    }
}
