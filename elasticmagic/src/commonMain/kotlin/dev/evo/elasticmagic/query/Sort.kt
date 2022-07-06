package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.types.FieldType

sealed interface Sort {
    companion object {
        operator fun invoke(
            field: FieldOperations<*>,
            order: Order? = null,
            mode: Mode? = null,
            numericType: NumericType? = null,
            missing: Missing? = null,
            unmappedType: FieldType<*, *>? = null,
            nested: Nested? = null,
        ): Field = Field(
            field,
            order = order,
            mode = mode,
            numericType = numericType,
            missing = missing,
            unmappedType = unmappedType,
            nested = nested,
        )

        operator fun invoke(
            script: dev.evo.elasticmagic.query.Script,
            scriptType: String,
            order: Order? = null,
            mode: Mode? = null,
            nested: Nested? = null,
        ): Script = Script(
            script,
            scriptType,
            order = order,
            mode = mode,
            nested = nested,
        )
    }

    // TODO: geo distance
    data class Field(
        val field: FieldOperations<*>,
        val order: Order? = null,
        val mode: Mode? = null,
        val numericType: NumericType? = null,
        val missing: Missing? = null,
        val unmappedType: FieldType<*, *>? = null,
        val nested: Nested? = null,
    ) : ArrayExpression, Sort {
        override fun clone() = copy()

        override fun accept(ctx: Serializer.ArrayCtx, compiler: SearchQueryCompiler) {
            val params = Params(
                "order" to order,
                "mode" to mode,
                "numeric_type" to numericType,
                "missing" to missing,
                "unmapped_type" to unmappedType?.name,
                "nested" to nested,
            )
            if (params.isEmpty()) {
                ctx.value(field.getQualifiedFieldName())
            } else {
                ctx.obj {
                    obj(field.getQualifiedFieldName()) {
                        compiler.visit(this, params)
                    }
                }
            }
        }
    }

    data class Script(
        val script: dev.evo.elasticmagic.query.Script,
        val type: String,
        val order: Order? = null,
        val mode: Mode? = null,
        val nested: Nested? = null,
    ) : ArrayExpression, Sort {
        override fun clone() = copy()

        override fun accept(ctx: Serializer.ArrayCtx, compiler: SearchQueryCompiler) {
            val params = Params(
                "type" to type,
                "order" to order,
                "mode" to mode,
                "nested" to nested,
            )
            ctx.obj {
                obj("_script") {
                    obj("script") {
                        compiler.visit(this, script)
                    }
                    compiler.visit(this, params)
                }
            }
        }
    }

    enum class Order : ToValue<String> {
        ASC, DESC;

        override fun toValue() = name.lowercase()
    }

    enum class Mode : ToValue<String> {
        MIN, MAX, SUM, AVG, MEDIAN;

        override fun toValue() = name.lowercase()
    }

    enum class NumericType : ToValue<String> {
        DOUBLE, LONG, DATE, DATE_NANOS;

        override fun toValue() = name.lowercase()
    }

    sealed class Missing : ToValue<Any> {
        object Last : Missing()
        object First : Missing()
        class Value(val value: Any) : Missing()

        override fun toValue(): Any {
            return when (this) {
                Last -> "_last"
                First -> "_first"
                is Value -> value
            }
        }
    }

    data class Nested(
        val path: FieldOperations<Nothing>,
        val filter: QueryExpression? = null,
        val maxChildren: Int? = null,
        val nested: Nested? = null,
    ) : ObjExpression {
        override fun clone() = copy()

        override fun accept(ctx: Serializer.ObjectCtx, compiler: SearchQueryCompiler) {
            ctx.field("path", path.getQualifiedFieldName())
            if (filter != null) {
                ctx.obj("filter") {
                    compiler.visit(this, filter)
                }
            }
            ctx.fieldIfNotNull("max_children", maxChildren)
            if (nested != null) {
                ctx.obj("nested") {
                    compiler.visit(this, nested)
                }
            }
        }
    }
}
