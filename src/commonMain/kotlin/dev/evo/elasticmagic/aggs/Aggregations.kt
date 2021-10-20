package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.query.Expression
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.NamedExpression
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.doc.FieldType
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer

sealed class AggValue<T> : Expression {
    data class Field<T>(val field: FieldOperations<T>) : AggValue<T>() {
        override fun clone() = copy()
        override fun getValueType(): FieldType<*, T> = field.getFieldType()
    }
    data class Script<T>(
        val script: dev.evo.elasticmagic.query.Script,
        val type: FieldType<*, T>,
    ) : AggValue<T>() {
        override fun clone() = copy()
        override fun getValueType(): FieldType<*, T> = type
    }
    data class ValueScript<T>(
        val field: FieldOperations<*>,
        val script: dev.evo.elasticmagic.query.Script,
        val type: FieldType<*, T>,
    ) : AggValue<T>() {
        override fun clone() = copy()
        override fun getValueType(): FieldType<*, T> = type
    }

    abstract fun getValueType(): FieldType<*, T>

    fun serializeTerm(v: T): Any = getValueType().serializeTerm(v)

    fun deserializeTerm(v: Any): T = getValueType().deserializeTerm(v)

    override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
        when (this) {
            is Field<*> -> ctx.field("field", field.getQualifiedFieldName())
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
