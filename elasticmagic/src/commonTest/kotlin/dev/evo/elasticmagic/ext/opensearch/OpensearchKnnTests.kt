package dev.evo.elasticmagic.ext.opensearch

import dev.evo.elasticmagic.BaseTest
import dev.evo.elasticmagic.doc.Document
import io.kotest.matchers.shouldBe
import io.kotest.matchers.maps.shouldContainExactly
import kotlin.test.Test

class OpensearchKnnTests : BaseTest() {
    object HotelDoc : Document() {
        val status by int()
        val location by Field(
            KnnVector(dimension = 2, spaceType = KnnSpace.L2),
        )
    }

    @Test
    fun knnVector() = withCompilers {
        KnnVector(dimension = 2, spaceType = KnnSpace.L2).let { t ->
            t.name shouldBe "knn_vector"
            t.termType shouldBe Float::class
            t.params() shouldContainExactly mapOf(
                "data_type" to "float",
                "dimension" to 2,
                "space_type" to KnnSpace.L2,
            )
            t shouldCompileInto mapOf(
                "type" to "knn_vector",
                "data_type" to "float",
                "dimension" to 2,
                "space_type" to "l2",
            )
        }
        KnnVector(
            KnnDataType.Byte,
            dimension = 2,
            spaceType = KnnSpace.COSINESIMIL,
        ).let { t ->
            t.name shouldBe "knn_vector"
            t.termType shouldBe Byte::class
            t.params() shouldContainExactly mapOf(
                "data_type" to "byte",
                "dimension" to 2,
                "space_type" to KnnSpace.COSINESIMIL,
            )
            t shouldCompileInto mapOf(
                "type" to "knn_vector",
                "data_type" to "byte",
                "dimension" to 2,
                "space_type" to "cosinesimil",
            )
        }
        KnnVector(
            KnnDataType.Binary,
            dimension = 256,
            spaceType = KnnSpace.L1,
        ).let { t ->
            t.name shouldBe "knn_vector"
            t.termType shouldBe Byte::class
            t.params() shouldContainExactly mapOf(
                "data_type" to "binary",
                "dimension" to 256,
                "space_type" to KnnSpace.L1,
            )
            t shouldCompileInto mapOf(
                "type" to "knn_vector",
                "data_type" to "binary",
                "dimension" to 256,
                "space_type" to "l1",
            )
        }
        KnnVector(
            dimension = 2,
            spaceType = KnnSpace.L2,
            mode = KnnMode.IN_MEMORY,
            compressionLevel = "2x",
            method = mapOf(
                "name" to "hnsw",
                "engine" to "faiss",
            ),
            params = mapOf(
                "some_param" to 1,
            ),
        ).let { t ->
            t.name shouldBe "knn_vector"
            t.termType shouldBe Float::class
            t.params() shouldContainExactly mapOf(
                "data_type" to "float",
                "dimension" to 2,
                "space_type" to KnnSpace.L2,
                "mode" to KnnMode.IN_MEMORY,
                "compression_level" to "2x",
                "method" to mapOf(
                    "name" to "hnsw",
                    "engine" to "faiss",
                ),
                "some_param" to 1,
            )
            t shouldCompileInto mapOf(
                "type" to "knn_vector",
                "data_type" to "float",
                "dimension" to 2,
                "space_type" to "l2",
                "mode" to "in_memory",
                "compression_level" to "2x",
                "method" to mapOf(
                    "name" to "hnsw",
                    "engine" to "faiss",
                ),
                "some_param" to 1,
            )
        }
        KnnVector(
            modelId = "test-model",
        ).let { t ->
            t.name shouldBe "knn_vector"
            t.termType shouldBe Float::class
            t.params() shouldContainExactly mapOf(
                "data_type" to "float",
                "model_id" to "test-model",
            )
            t shouldCompileInto mapOf(
                "type" to "knn_vector",
                "data_type" to "float",
                "model_id" to "test-model",
            )
        }
        KnnVector(
            KnnDataType.Byte,
            modelId = "test-model",
        ).let { t ->
            t.name shouldBe "knn_vector"
            t.termType shouldBe Byte::class
            t.params() shouldContainExactly mapOf(
                "data_type" to "byte",
                "model_id" to "test-model",
            )
            t shouldCompileInto mapOf(
                "type" to "knn_vector",
                "data_type" to "byte",
                "model_id" to "test-model",
            )
        }
    }

    @Test
    fun knnBasic() = withCompilers {
        Knn(HotelDoc.location, listOf(1.1F, 1.2F)) shouldCompileInto mapOf(
            "knn" to mapOf(
                "location" to mapOf(
                    "vector" to listOf(1.1F, 1.2F),
                )
            )
        )
    }

    @Test
    fun knnFullParams() = withCompilers {
        Knn(
            HotelDoc.location,
            listOf(0.1F, 2.2F),
            k = 10,
            maxDistance = 100F,
            minScore = 1.8F,
            filter = HotelDoc.status eq 0,
            methodParameters = mapOf(
                "ef_search" to 100,
            ),
            rescore = mapOf("oversample" to 2.0F),
            expandNestedDocs = true,
            params = mapOf("boost" to 1.5F),
        ) shouldCompileInto mapOf(
            "knn" to mapOf(
                "location" to mapOf(
                    "vector" to listOf(0.1F, 2.2F),
                    "k" to 10,
                    "max_distance" to 100F,
                    "min_score" to 1.8F,
                    "filter" to mapOf(
                        "term" to mapOf("status" to 0)
                    ),
                    "method_parameters" to mapOf(
                        "ef_search" to 100,
                    ),
                    "rescore" to mapOf(
                        "oversample" to 2.0F,
                    ),
                    "expand_nested_docs" to true,
                    "boost" to 1.5F,
                )
            )
        )
    }
}
