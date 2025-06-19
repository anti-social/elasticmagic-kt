package dev.evo.elasticmagic.ext.elasticsearch

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

sealed class VectorElementType<T: Number>(val type: NumberType<T>, val name: String? = null) {
    object Float : VectorElementType<kotlin.Float>(FloatType)
    object Byte : VectorElementType<kotlin.Byte>(ByteType)
    object Binary : VectorElementType<kotlin.Byte>(ByteType, "bit")
}

enum class VectorSimilarity : ToValue<String> {
    L2_NORM, DOT_PRODUCT, COSINE, MAX_INNER_PRODUCT;

    override fun toValue() = name.lowercase()
}

class DenseVector<T: Number>(
    private val elementType: VectorElementType<T>,
    private val dims: Short? = null,
    private val index: Boolean? = null,
    private val similarity: VectorSimilarity? = null,
    private val indexOptions: Params? = null,
    private val params: Params? = null,
) : AbstractListType<T>(elementType.type) {
    companion object {
        operator fun invoke(
            dims: Short? = null,
            index: Boolean? = null,
            similarity: VectorSimilarity? = null,
            indexOptions: Params? = null,
            params: Params? = null,
        ): DenseVector<Float> {
            return DenseVector(
                VectorElementType.Float,
                dims = dims,
                index = index,
                similarity = similarity,
                indexOptions = indexOptions,
                params = params,
            )
        }
    }

    override val name = "dense_vector"

    override fun params(): Params {
        return Params(
            params,
            "element_type" to (elementType.name ?: elementType.type.name),
            "dims" to dims?.toInt(),
            "index" to index,
            "similarity" to similarity,
            "indexOptions" to indexOptions,
        )
    }
}

sealed class QueryVector<T: Number> {
    data class Raw<T: Number>(val vector: List<T>) : QueryVector<T>()
    data class Builder(val params: Params) : QueryVector<Nothing>()
}

data class Knn<E: VectorElementType<T>, T: Number>(
    val field: FieldOperations<List<T>>,
    val queryVector: QueryVector<T>,
    val k: Int? = null,
    val numCandidates: Int? = null,
    val filter: QueryExpression? = null,
    val similarity: Float? = null,
    val rescoreVector: Params? = null,
    val oversample: Float? = null,
    val boost: Float? = null,
    val params: Params? = null,
) : QueryExpression {
    companion object {
        operator fun <T: Number> invoke(
            field: FieldOperations<List<T>>,
            queryVector: List<T>,
            k: Int? = null,
            numCandidates: Int? = null,
            filter: QueryExpression? = null,
            similarity: Float? = null,
            rescoreVector: Params? = null,
            oversample: Float? = null,
            boost: Float? = null,
            params: Params? = null,
        ): Knn<VectorElementType<T>, T> {
            return Knn(
                field = field,
                queryVector = QueryVector.Raw(queryVector),
                k = k,
                numCandidates = numCandidates,
                filter = filter,
                similarity = similarity,
                rescoreVector = rescoreVector,
                oversample = oversample,
                boost = boost,
                params = params,
            )
        }
    }

    override val name = "knn"
    
    override fun clone() = copy()

    override fun visit(
        ctx: Serializer.ObjectCtx,
        compiler: BaseSearchQueryCompiler,
    ) {
        ctx.field("field", field.getQualifiedFieldName())
        when (queryVector) {
            is QueryVector.Raw -> ctx.array("query_vector", queryVector.vector)
            is QueryVector.Builder -> ctx.obj("query_vector") {
                compiler.visit(this, queryVector.params)
            }
        }
        ctx.fieldIfNotNull("k", k)
        ctx.fieldIfNotNull("num_candidates", numCandidates)
        if (filter != null) {
            ctx.obj("filter") {
                compiler.visit(this, filter)
            }
        }
        ctx.fieldIfNotNull("similarity", similarity)
        if (rescoreVector != null) {
            ctx.obj("rescore_vector") {
                compiler.visit(this, rescoreVector)
            }
        }
        ctx.fieldIfNotNull("oversample", oversample)
        ctx.fieldIfNotNull("boost", boost)
        if (params != null) {
            compiler.visit(ctx, params)
        }
    }
}
