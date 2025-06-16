package dev.evo.elasticmagic.query

import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.serde.Serializer

sealed class Source : ObjExpression {
    val name = "_source"

    data class Filter(
        val includes: List<FieldOperations<*>>,
        val excludes: List<FieldOperations<*>>,
    ) : Source() {
        companion object {
            fun includes(includes: List<FieldOperations<*>>) = Filter(
                includes = includes,
                excludes = emptyList(),
            )

            fun excludes(excludes: List<FieldOperations<*>>) = Filter(
                includes = emptyList(),
                excludes = excludes,
            )
        }

        override fun clone() = copy()

        override fun accept(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
            if (excludes.isEmpty()) {
                ctx.array(name) {
                    compiler.visit(this, includes)
                }
            } else {
                ctx.obj(name) {
                    array("includes") {
                        compiler.visit(this, includes)
                    }
                    array("excludes") {
                        compiler.visit(this, excludes)
                    }
                }
            }
        }
    }

    object Enable : Source() {
        override fun clone() = this

        override fun accept(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
            ctx.field(name, true)
        }

    }
    object Disable : Source() {
        override fun clone() = this

        override fun accept(ctx: Serializer.ObjectCtx, compiler: BaseSearchQueryCompiler) {
            ctx.field(name, false)
        }
    }
}
