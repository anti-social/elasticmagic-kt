package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.query.Named
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx

@Suppress("UnnecessaryAbstractClass")
abstract class BaseCompiler(
    val esVersion: ElasticsearchVersion,
) {
    open fun dispatch(ctx: ArrayCtx, value: Any?) {
        ctx.value(value)
    }

    fun visit(ctx: ArrayCtx, values: List<*>) {
        for (value in values) {
            arrayValue(ctx, value)
        }
    }

    fun visit(ctx: ArrayCtx, values: Array<*>) {
        for (value in values) {
            arrayValue(ctx, value)
        }
    }

    private fun arrayValue(ctx: ArrayCtx, value: Any?) {
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

@Suppress("UnnecessaryAbstractClass")
abstract class ElasticsearchFeatures {
    abstract val requiresMappingTypeName: Boolean
}

@Suppress("ClassNaming")
object ElasticsearchFeatures_6_0 : ElasticsearchFeatures() {
    override val requiresMappingTypeName = true
}

@Suppress("ClassNaming")
object ElasticsearchFeatures_7_0 : ElasticsearchFeatures() {
    override val requiresMappingTypeName = false
}

class CompilerSet(esVersion: ElasticsearchVersion) {
    @Suppress("MagicNumber")
    val features = when (esVersion.major) {
        6 -> ElasticsearchFeatures_6_0
        in 7..Int.MAX_VALUE -> ElasticsearchFeatures_7_0
        else -> throw IllegalArgumentException(
            "Elasticsearch version is not supported: $esVersion"
        )
    }

    val searchQuery: SearchQueryCompiler = SearchQueryCompiler(esVersion)
    val multiSearchQuery: MultiSearchQueryCompiler = MultiSearchQueryCompiler(esVersion, searchQuery)

    val mapping: MappingCompiler = MappingCompiler(esVersion, searchQuery)
    val createIndex: CreateIndexCompiler = CreateIndexCompiler(esVersion, features, mapping)
    val updateMapping: UpdateMappingCompiler = UpdateMappingCompiler(esVersion, features, mapping)

    val actionMetaCompiler: ActionMetaCompiler = ActionMetaCompiler(esVersion, features)
    val actionSourceCompiler: ActionSourceCompiler = ActionSourceCompiler(esVersion, searchQuery)
    val actionCompiler: ActionCompiler = ActionCompiler(
        esVersion, actionMetaCompiler, actionSourceCompiler
    )
    val bulk: BulkCompiler = BulkCompiler(esVersion, actionCompiler)

}
