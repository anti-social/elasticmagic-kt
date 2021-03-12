package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.FieldOperations
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx

interface Compiler<I, O> {
    val esVersion: ElasticsearchVersion

    fun compile(input: I): O

    // fun processResult(input: )
}

abstract class BaseCompiler<I, O>(
    override val esVersion: ElasticsearchVersion,
) : Compiler<I, O> {
    open fun dispatch(ctx: ArrayCtx, value: Any?) {
        ctx.value(value)
    }

    fun visit(ctx: ArrayCtx, values: List<*>) {
        for (value in values) {
            when (value) {
                is Map<*, *> -> ctx.obj {
                    visit(this, value)
                }
                is List<*> -> ctx.array {
                    visit(this, value)
                }
                is FieldOperations -> {
                    ctx.value(value.getQualifiedFieldName())
                }
                else -> {
                    dispatch(ctx, value)
                }
            }
        }
    }

    open fun dispatch(ctx: ObjectCtx, name: String, value: Any?) {
        ctx.field(name, value)
    }

    fun visit(ctx: ObjectCtx, params: Map<*, *>) {
        for ((name, value) in params) {
            require(name is String) {
                "Expected string but was: ${if (name != null) name::class else null}"
            }
            when (value) {
                is Map<*, *> -> ctx.obj(name) {
                    visit(this, value)
                }
                is List<*> -> ctx.array(name) {
                    visit(this, value)
                }
                is FieldOperations -> {
                    ctx.field(name, value.getQualifiedFieldName())
                }
                else -> {
                    dispatch(ctx, name, value)
                }
            }
        }
    }
}

class CompilerProvider<OBJ>(
    esVersion: ElasticsearchVersion,
    val serializer: Serializer<OBJ>,
    val deserializer: Deserializer<OBJ>,
) {
    val mapping: MappingCompiler<OBJ> = MappingCompiler(esVersion, serializer)

    val searchQuery: SearchQueryCompiler<OBJ> = SearchQueryCompiler(esVersion, serializer)
}
