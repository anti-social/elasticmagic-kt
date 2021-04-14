package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.Named
import dev.evo.elasticmagic.Parameters
import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx
import dev.evo.elasticmagic.transport.Method

class Compiled<B, R>(
    val method: Method,
    val path: String,
    val parameters: Parameters,
    val body: B?,
    val processResult: (Deserializer.ObjectCtx) -> R,
)

abstract class BaseCompiler(
    val esVersion: ElasticsearchVersion,
) {
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
                is Named -> {
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
                is Named -> {
                    ctx.field(name, value.getQualifiedFieldName())
                }
                else -> {
                    dispatch(ctx, name, value)
                }
            }
        }
    }
}

class CompilerProvider(esVersion: ElasticsearchVersion) {
    val mapping: MappingCompiler = MappingCompiler(esVersion)
    val createIndex: CreateIndexCompiler = CreateIndexCompiler(esVersion, mapping)
    val updateMapping: UpdateMappingCompiler = UpdateMappingCompiler(esVersion, mapping)

    val searchQuery: SearchQueryCompiler = SearchQueryCompiler(esVersion)

    val actionMetaCompiler: ActionMetaCompiler = ActionMetaCompiler(esVersion)
    val actionSourceCompiler: ActionSourceCompiler = ActionSourceCompiler(esVersion, searchQuery)
    val actionCompiler: ActionCompiler = ActionCompiler(
        esVersion, actionMetaCompiler, actionSourceCompiler
    )
    val bulk: BulkCompiler = BulkCompiler(esVersion, actionCompiler)

}
