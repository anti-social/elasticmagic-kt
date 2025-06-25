package dev.evo.elasticmagic.ext.elasticsearch

import dev.evo.elasticmagic.BaseTest
import dev.evo.elasticmagic.doc.Document
import io.kotest.matchers.shouldBe
import io.kotest.matchers.maps.shouldContainExactly
import kotlin.test.Test

class ElasticsearchKnnTests : BaseTest() {
    object HotelDoc : Document() {
        val status by int()
        val location by Field(
            DenseVector(dims = 2, similarity = VectorSimilarity.L2_NORM),
        )
    }

    @Test
    fun denseVector() = withCompilers {
        DenseVector().let { t ->
            t.name shouldBe "dense_vector"
            t.termType shouldBe Float::class
            t.params() shouldContainExactly mapOf(
                "element_type" to "float",
            )
            t shouldCompileInto mapOf(
                "type" to "dense_vector",
                "element_type" to "float",
            )
        }
        DenseVector(VectorElementType.Byte).let { t ->
            t.name shouldBe "dense_vector"
            t.termType shouldBe Byte::class
            t.params() shouldContainExactly mapOf(
                "element_type" to "byte",
            )
            t shouldCompileInto mapOf(
                "type" to "dense_vector",
                "element_type" to "byte",
            )
        }
        DenseVector(VectorElementType.Bit).let { t ->
            t.name shouldBe "dense_vector"
            t.termType shouldBe Byte::class
            t.params() shouldContainExactly mapOf(
                "element_type" to "bit",
            )
            t shouldCompileInto mapOf(
                "type" to "dense_vector",
                "element_type" to "bit",
            )
        }
        DenseVector(
            dims = 4,
            index = true,
            similarity = VectorSimilarity.COSINE,
            indexOptions = mapOf("type" to "int4_hnsw"),
            params = mapOf("some_param" to 1),
        ).let { t ->
            t.name shouldBe "dense_vector"
            t.termType shouldBe Float::class
            t.params() shouldContainExactly mapOf(
                "element_type" to "float",
                "dims" to 4,
                "index" to true,
                "similarity" to VectorSimilarity.COSINE,
                "index_options" to mapOf("type" to "int4_hnsw"),
                "some_param" to 1,
            )
            t shouldCompileInto mapOf(
                "type" to "dense_vector",
                "element_type" to "float",
                "dims" to 4,
                "index" to true,
                "similarity" to "cosine",
                "index_options" to mapOf("type" to "int4_hnsw"),
                "some_param" to 1,
            )
        }
    }

    @Test
    fun knnBasic() = withCompilers {
        Knn(HotelDoc.location, listOf(1.1F, 1.2F)) shouldCompileInto mapOf(
            "knn" to mapOf(
                "field" to "location",
                "query_vector" to listOf(1.1F, 1.2F),
            )
        )
    }

    @Test
    fun knnQueryVectorBuilder() = withCompilers {
        Knn(
            HotelDoc.location,
            QueryVector.Builder(mapOf(
                "text_embedding" to mapOf(
                    "model_id" to "embedding-model",
                    "model_text" to "The opposite of blue",
                )
            )),
        ) shouldCompileInto mapOf(
            "knn" to mapOf(
                "field" to "location",
                "query_vector_builder" to mapOf(
                    "text_embedding" to mapOf(
                        "model_id" to "embedding-model",
                        "model_text" to "The opposite of blue",
                    )
                ),
            )
        )
    }

    @Test
    fun knnFullParams() = withCompilers {
        Knn(
            HotelDoc.location,
            listOf(0.1F, 2.2F),
            k = 10,
            numCandidates = 100,
            filter = HotelDoc.status eq 0,
            similarity = 36F,
            rescoreVector = mapOf("oversample" to 2.0F),
            boost = 1.5F,
        ) shouldCompileInto mapOf(
            "knn" to mapOf(
                "field" to "location",
                "query_vector" to listOf(0.1F, 2.2F),
                "k" to 10,
                "num_candidates" to 100,
                "filter" to mapOf(
                    "term" to mapOf("status" to 0)
                ),
                "similarity" to 36F,
                "rescore_vector" to mapOf(
                    "oversample" to 2.0F,
                ),
                "boost" to 1.5F,
            )
        )
    }
}
