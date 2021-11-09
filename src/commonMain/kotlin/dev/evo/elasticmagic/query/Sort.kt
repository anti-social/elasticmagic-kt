package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.compile.SearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

interface Sort {
    companion object {
        operator fun invoke(
            by: By,
            order: Order? = null,
            mode: Mode? = null,
            numericType: NumericType? = null,
            missing: Missing? = null,
            unmappedType: String? = null,
            nested: Nested? = null,
        ): Sort = Impl(
            by,
            order = order,
            mode = mode,
            numericType = numericType,
            missing = missing,
            unmappedType = unmappedType,
            nested = nested,
        )

        operator fun invoke(
            field: FieldOperations<*>? = null,
            scriptType: String? = null,
            script: Script? = null,
            order: Order? = null,
            mode: Mode? = null,
            numericType: NumericType? = null,
            missing: Missing? = null,
            unmappedType: String? = null,
            nested: Nested? = null,
        ): Sort = Impl(
            by = By(field, scriptType, script),
            order = order,
            mode = mode,
            numericType = numericType,
            missing = missing,
            unmappedType = unmappedType,
            nested = nested,
        )
    }

    // TODO: geo distance
    data class Impl(
        val by: By,
        val order: Order? = null,
        val mode: Mode? = null,
        val numericType: NumericType? = null,
        val missing: Missing? = null,
        val unmappedType: String? = null,
        val nested: Nested? = null,
    ) : ArrayExpression, Sort {
        override fun clone() = copy()

        override fun accept(ctx: Serializer.ArrayCtx, compiler: SearchQueryCompiler) {
            val params = Params(
                "order" to order,
                "mode" to mode,
                "numeric_type" to numericType,
                "missing" to missing,
                "unmapped_type" to unmappedType,
                "nested" to nested,
            )
            if (
                by is By.Field &&
                order == null &&
                mode == null &&
                numericType == null &&
                missing == null &&
                unmappedType == null
            ) {
                ctx.value(by.field.getQualifiedFieldName())
            } else {
                ctx.obj {
                    when (by) {
                        is By.Field -> {
                            obj(by.field.getQualifiedFieldName()) {
                                compiler.visit(this, params)
                            }
                        }
                        is By.Script -> {
                            obj("_script") {
                                field("type", by.type)
                                obj("script") {
                                    compiler.visit(this, by.script)
                                }
                                compiler.visit(this, params)
                            }
                        }
                    }
                }
            }
        }
    }

    sealed class By {
        class Field(val field: FieldOperations<*>) : By()
        class Script(val type: String, val script: dev.evo.elasticmagic.query.Script) : By()

        companion object {
            internal operator fun invoke(
                field: FieldOperations<*>?, scriptType: String?, script: dev.evo.elasticmagic.query.Script?
            ): By {
                return when {
                    field != null && script != null -> throw IllegalArgumentException(
                        "Only field or script are allowed, not both"
                    )
                    field == null && script == null -> throw IllegalArgumentException(
                        "One of field or script are required"
                    )
                    field != null -> Field(field)
                    script != null -> {
                        if (scriptType != null) {
                            Script(scriptType, script)
                        } else {
                            throw IllegalArgumentException(
                                "script requires scriptType"
                            )
                        }
                    }
                    else -> error("Unreachable")
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