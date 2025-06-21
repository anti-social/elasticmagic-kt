package dev.evo.elasticmagic.ext.opensearch

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.ToValue
import dev.evo.elasticmagic.compile.BaseSearchQueryCompiler
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.QueryExpression
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.types.AbstractListType
import dev.evo.elasticmagic.types.ByteType
import dev.evo.elasticmagic.types.FloatType
import dev.evo.elasticmagic.types.NumberType

sealed class KnnDataType<T: Number>(val type: NumberType<T>, val name: String? = null) {
    object Float : KnnDataType<kotlin.Float>(FloatType)
    object Byte : KnnDataType<kotlin.Byte>(ByteType)
    object Binary : KnnDataType<kotlin.Byte>(ByteType, "binary")
}

enum class KnnSpace : ToValue<String> {
    L1, L2, LINF, COSINESIMIL, INNERPRODUCT, HAMMING, HAMMINGBIT;

    override fun toValue() = name.lowercase()
}

enum class KnnMode : ToValue<String> {
    IN_MEMORY, ON_DISK;

    override fun toValue() = name.lowercase()
}

class KnnVector<T: Number>(
    private val dataType: KnnDataType<T>,
    private val dimension: Short,
    private val spaceType: KnnSpace,
    private val mode: KnnMode? = null,
    private val compressionLevel: String? = null,
    private val method: Params? = null,
    private val modelId: String? = null,
    private val params: Params? = null,
) : AbstractListType<T>(dataType.type) {
    companion object {
        operator fun invoke(
            dimension: Short,
            spaceType: KnnSpace,
            mode: KnnMode? = null,
            compressionLevel: String? = null,
            method: Params? = null,
            modelId: String? = null,
            params: Params? = null,
        ): KnnVector<Float> {
            return KnnVector(
                KnnDataType.Float,
                dimension = dimension,
                spaceType = spaceType,
                mode = mode,
                compressionLevel = compressionLevel,
                method = method,
                modelId = modelId,
                params = params,
            )
        }
    }

    override val name = "knn_vector"

    override fun params(): Params {
        return Params(
            params,
            "data_type" to (dataType.name ?: dataType.type.name),
            "dimension" to dimension.toInt(),
            "space_type" to spaceType,
            "mode" to mode,
            "compression_level" to compressionLevel,
            "method" to method,
            "model_id" to modelId,
        )
    }
}

data class Knn<D: KnnDataType<T>, T: Number>(
    val field: FieldOperations<List<T>>,
    val vector: List<T>,
    val k: Int? = null,
    val maxDistance: Float? = null,
    val minScore: Float? = null,
    val filter: QueryExpression? = null,
    val methodParameters: Params? = null,
    val rescore: Params? = null,
    val expandNestedDocs: Boolean? = null,
    val params: Params? = null,
) : QueryExpression {
    override val name = "knn"

    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: BaseSearchQueryCompiler
    ) {
        ctx.obj(field.getQualifiedFieldName()) {
            array("vector", vector)
            fieldIfNotNull("k", k)
            fieldIfNotNull("max_distance", maxDistance)
            fieldIfNotNull("min_score", minScore)
            if (filter != null) {
                obj("filter") {
                    compiler.visit(this, filter)
                }
            }
            if (methodParameters != null) {
                obj("method_parameters") {
                    compiler.visit(this, methodParameters)
                }
            }
            if (rescore != null) {
                obj("rescore") {
                    compiler.visit(this, rescore)
                }
            }
            fieldIfNotNull("expand_nested_docs", expandNestedDocs)
            if (params != null) {
                compiler.visit(this, params)
            }
        }
    }
}
