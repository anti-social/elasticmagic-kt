package dev.evo.elasticmagic.aggs

import dev.evo.elasticmagic.SearchHit
import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.query.Source
import dev.evo.elasticmagic.query.StoredField
import dev.evo.elasticmagic.serde.DeserializationException

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

import kotlin.test.Test

class TopHitsTests : TestAggregation() {
    @Test
    fun compile() {
        TopHitsAgg().compile() shouldContainExactly mapOf(
            "top_hits" to emptyMap<String, Any>()
        )

        TopHitsAgg(
            storedFields = listOf(StoredField.None)
        ).compile() shouldContainExactly mapOf(
            "top_hits" to mapOf(
                "stored_fields" to "_none_",
            )
        )

        TopHitsAgg(
            from = 0,
            size = 20,
            sort = listOf(MovieDoc.numRatings.desc()),
            source = Source.Filter.includes(listOf(MovieDoc.name, MovieDoc.status)),
        ).compile() shouldContainExactly mapOf(
            "top_hits" to mapOf(
                "from" to 0,
                "size" to 20,
                "sort" to listOf(
                    mapOf(
                        "num_ratings" to mapOf(
                            "order" to "desc"
                        )
                    )
                ),
                "_source" to listOf("name", "status"),
            )
        )

        TopHitsAgg(
            from = 0,
            size = 20,
            sort = listOf(MovieDoc.numRatings.desc()),
            source = Source.Filter.includes(listOf(MovieDoc.name, MovieDoc.status)),
            fields = listOf(MovieDoc.genre),
            docvalueFields = listOf(MovieDoc.rating),
            storedFields = listOf(MovieDoc.numRatings),
        ).compile() shouldContainExactly mapOf(
            "top_hits" to mapOf(
                "from" to 0,
                "size" to 20,
                "sort" to listOf(
                    mapOf(
                        "num_ratings" to mapOf(
                            "order" to "desc"
                        )
                    )
                ),
                "_source" to listOf("name", "status"),
                "fields" to listOf("genre"),
                "docvalue_fields" to listOf("rating"),
                "stored_fields" to listOf("num_ratings"),
            )
        )
    }

    @Test
    fun result() {
        val agg = TopHitsAgg()
        shouldThrow<DeserializationException> {
            process(agg, emptyMap())
        }

        process(
            agg,
            mapOf(
                "hits" to emptyMap<Nothing, Nothing>()
            )
        ) shouldBe TopHitsAggResult(
            totalHits = null,
            totalHitsRelation = null,
            maxScore = null,
            emptyList()
        )

        process(
            agg,
            mapOf(
                "hits" to mapOf(
                    "total" to mapOf(
                        "value" to 42,
                        "relation" to "eq",
                    ),
                    "max_score" to 1.2,
                    "hits" to listOf(
                        mapOf(
                            "_index" to "movies",
                            "_id" to "AVnNBmauCQpcRyxw6ChK",
                            "_source" to mapOf(
                                "name" to "Simpsons"
                            )
                        )
                    ),
                )
            )
        ) shouldBe TopHitsAggResult(
            totalHits = 42,
            totalHitsRelation = "eq",
            maxScore = 1.2F,
            listOf(
                SearchHit(
                    index = "movies",
                    type = "_doc",
                    id = "AVnNBmauCQpcRyxw6ChK",
                    source = DynDocSource { doc ->
                        doc[MovieDoc.name] = "Simpsons"
                    }
                )
            ),
        )

        val aggWithDocSource = TopHitsAgg({ BaseMovieDocSource() })
        shouldThrow<IllegalStateException> {
            process(
                aggWithDocSource,
                mapOf(
                    "hits" to mapOf(
                        "total" to mapOf(
                            "value" to 42,
                            "relation" to "eq",
                        ),
                        "max_score" to 1.2,
                        "hits" to listOf(
                            mapOf(
                                "_index" to "movies",
                                "_id" to "AVnNBmauCQpcRyxw6ChK",
                                "_source" to mapOf(
                                    "name" to "Simpsons"
                                )
                            )
                        ),
                    )
                )
            )
        }

        process(
            aggWithDocSource,
            mapOf(
                "hits" to mapOf(
                    "total" to mapOf(
                        "value" to 42,
                        "relation" to "eq",
                    ),
                    "max_score" to 1.2,
                    "hits" to listOf(
                        mapOf(
                            "_index" to "movies",
                            "_id" to "AVnNBmauCQpcRyxw6ChK",
                            "_source" to mapOf(
                                "status" to 0,
                                "name" to "Simpsons"
                            )
                        )
                    ),
                )
            )
        ) shouldBe TopHitsAggResult(
            totalHits = 42,
            totalHitsRelation = "eq",
            maxScore = 1.2F,
            listOf(
                SearchHit(
                    index = "movies",
                    type = "_doc",
                    id = "AVnNBmauCQpcRyxw6ChK",
                    source = BaseMovieDocSource().apply {
                        status = 0
                        name = "Simpsons"
                    }
                )
            ),
        )
    }
}
