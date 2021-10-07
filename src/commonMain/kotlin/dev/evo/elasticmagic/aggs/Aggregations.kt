package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.query.Expression
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.NamedExpression
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer

sealed class AggValue : Expression {
    data class Field(val field: FieldOperations) : AggValue() {
        override fun clone() = copy()
    }
    data class Script(val script: dev.evo.elasticmagic.query.Script) : AggValue() {
        override fun clone() = copy()
    }
    data class ValueScript(
        val field: FieldOperations,
        val script: dev.evo.elasticmagic.query.Script,
    ) : AggValue() {
        override fun clone() = copy()
    }

    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        when (this) {
            is Field -> ctx.field("field", field.getQualifiedFieldName())
            is Script -> ctx.obj("script") {
                compiler.visit(this, script)
            }
            is ValueScript -> {
                ctx.field("field", field.getQualifiedFieldName())
                ctx.obj("script") {
                    compiler.visit(this, script)
                }
            }
        }
    }
}

interface Aggregation<R: AggregationResult> : NamedExpression {
    fun processResult(obj: Deserializer.ObjectCtx): R
}

interface AggregationResult
