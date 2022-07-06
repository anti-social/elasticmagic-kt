package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.query.Named
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx

@Suppress("UnnecessaryAbstractClass")
abstract class BaseCompiler(
    val esVersion: ElasticsearchVersion,
) {
    val features = ElasticsearchFeatures(esVersion)

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

enum class ElasticsearchFeatures(
    val requiresMappingTypeName: Boolean,
    val supportsTrackingOfTotalHits: Boolean,
) {
    ES_6_0(
        requiresMappingTypeName = true,
        supportsTrackingOfTotalHits = false,
    ),
    ES_7_0(
        requiresMappingTypeName = false,
        supportsTrackingOfTotalHits = true,
    ),
    ;

    companion object {
        val VERSION_FEATURES = listOf(
            ElasticsearchVersion(6, 0, 0) to ES_6_0,
            ElasticsearchVersion(7, 0, 0) to ES_7_0,
        )

        @Suppress("MagicNumber")
        operator fun invoke(esVersion: ElasticsearchVersion): ElasticsearchFeatures {
            for (versionToFeature in VERSION_FEATURES.reversed()) {
                if (esVersion >= versionToFeature.first) {
                    return versionToFeature.second
                }
            }
            throw IllegalArgumentException(
                "Elasticsearch version is not supported: $esVersion"
            )
        }
    }
}

class CompilerSet(esVersion: ElasticsearchVersion) {
    val searchQuery: SearchQueryCompiler = SearchQueryCompiler(esVersion)
    val multiSearchQuery: MultiSearchQueryCompiler = MultiSearchQueryCompiler(esVersion, searchQuery)

    val mapping: MappingCompiler = MappingCompiler(esVersion, searchQuery)
    val createIndex: CreateIndexCompiler = CreateIndexCompiler(esVersion, mapping)
    val updateMapping: UpdateMappingCompiler = UpdateMappingCompiler(esVersion, mapping)

    val actionMetaCompiler: ActionMetaCompiler = ActionMetaCompiler(esVersion)
    val actionSourceCompiler: ActionSourceCompiler = ActionSourceCompiler(esVersion, searchQuery)
    val actionCompiler: ActionCompiler = ActionCompiler(
        esVersion, actionMetaCompiler, actionSourceCompiler
    )
    val bulk: BulkCompiler = BulkCompiler(esVersion, actionCompiler)

}
