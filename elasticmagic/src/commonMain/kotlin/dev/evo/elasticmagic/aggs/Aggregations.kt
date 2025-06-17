package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.NamedExpression
import dev.evo.elasticmagic.query.ObjExpression
import dev.evo.elasticmagic.types.FieldType
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer

/**
 * Represents value source for an aggregation:
 * - [Field] takes values from a field.
 * - [Script] gets values as a script result.
 * - [ValueScript] combines a field with a script. There is special variable `_value` that contains field value.
 */
sealed class AggValue<T> : ObjExpression {
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

    fun serializeTerm(v: T & Any): Any = getValueType().serializeTerm(v)

    fun deserializeTerm(v: Any): T & Any = getValueType().deserializeTerm(v)

    override fun accept(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
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

/**
 * Base aggregation expression.
 * @param R an aggregation result type for this aggregation
 */
interface Aggregation<R : AggregationResult> : NamedExpression {
    /**
     * Processes corresponding aggregation response.
     */
    fun processResult(
        obj: Deserializer.ObjectCtx,
        docSourceFactory: (Deserializer.ObjectCtx) -> BaseDocSource,
    ): R

    fun processResult(obj: Deserializer.ObjectCtx): R {
        return processResult(obj) { DynDocSource() }
    }
}

/**
 * Aggregation result interface marker.
 */
interface AggregationResult
