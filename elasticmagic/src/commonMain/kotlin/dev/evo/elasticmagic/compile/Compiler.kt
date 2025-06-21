package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.Version
import dev.evo.elasticmagic.query.Named
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx

@Suppress("UnnecessaryAbstractClass")
abstract class BaseCompiler(
    val features: ElasticsearchFeatures
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
        val ES_VERSION_FEATURES = listOf(
            Version.Elasticsearch(6, 0, 0) to ES_6_0,
            Version.Elasticsearch(7, 0, 0) to ES_7_0,
        )
        val OS_VERSION_FEATURES = listOf(
            Version.Opensearch(1, 0, 0) to ES_7_0,
        )

        @Suppress("MagicNumber")
        operator fun invoke(esVersion: Version<*>): ElasticsearchFeatures {
            return when(esVersion) {
                is Version.Elasticsearch -> findFeatures(esVersion, ES_VERSION_FEATURES)
                is Version.Opensearch -> findFeatures(esVersion, OS_VERSION_FEATURES)
            }
        }

        private fun <T : Version<T>> findFeatures(
            esVersion: T, features: List<Pair<T, ElasticsearchFeatures>>
        ): ElasticsearchFeatures {
            for (versionToFeature in features.asReversed()) {
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

class CompilerSet(val features: ElasticsearchFeatures) {
    val searchQuery: SearchQueryCompiler = SearchQueryCompiler(features)
    val countQuery: CountQueryCompiler = CountQueryCompiler(features)
    val updateByQuery: UpdateByQueryCompiler = UpdateByQueryCompiler(features)
    val deleteByQuery: DeleteByQueryCompiler = DeleteByQueryCompiler(features)
    val multiSearchQuery: MultiSearchQueryCompiler = MultiSearchQueryCompiler(features, searchQuery)

    val mapping: MappingCompiler = MappingCompiler(features, searchQuery)
    val createIndex: CreateIndexCompiler = CreateIndexCompiler(features, mapping)
    val updateMapping: UpdateMappingCompiler = UpdateMappingCompiler(features, mapping)

    val actionMetaCompiler: ActionMetaCompiler = ActionMetaCompiler(features)
    val actionSourceCompiler: ActionSourceCompiler = ActionSourceCompiler(features, searchQuery)
    val actionCompiler: ActionCompiler = ActionCompiler(
        features, actionMetaCompiler, actionSourceCompiler
    )
    val bulk: BulkCompiler = BulkCompiler(features, actionCompiler)
}
