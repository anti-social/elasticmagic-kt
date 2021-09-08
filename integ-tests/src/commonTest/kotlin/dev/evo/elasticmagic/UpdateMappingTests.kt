package dev.evo.elasticmagic

import io.kotest.matchers.shouldBe

import kotlin.test.Test

object UserV1Doc : Document() {
    override val dynamic = Dynamic.FALSE

    val id by int()
}

object UserV2Doc : Document() {
    override val dynamic = Dynamic.FALSE

    val id by int()
    val name by text()
}

class UserDocSource : DocSource() {
    var id by UserV2Doc.id.required()
    var name by UserV2Doc.name
}

class UpdateMappingTests : ElasticsearchTestBase("update-mapping") {
    @Test
    fun testUpdateMapping() = runTest {
        cluster.createIndex(
            index.name,
            mapping = UserV1Doc,
            settings = Params(
                "index.number_of_replicas" to 0,
            ),
        )
        try {
            index.bulk(listOf(
                CreateAction(
                    meta = IdentActionMeta("1"),
                    source = UserDocSource().apply {
                        id = 1
                        name = "Hell boy"
                    }
                ),
            ), refresh = Refresh.TRUE)
            SearchQuery(
                ::UserDocSource, UserV2Doc.name.match("boy")
            )
                .execute(index)
                .totalHits shouldBe 0

            cluster.updateMapping(
                index.name,
                mapping = UserV2Doc
            )
            index.bulk(listOf(
                IndexAction(
                    meta = IdentActionMeta("1"),
                    source = UserDocSource().apply {
                        id = 1
                        name = "Hell boy"
                    }
                ),
            ), refresh = Refresh.TRUE)
            SearchQuery(
                ::UserDocSource, UserV2Doc.name.match("boy")
            )
                .execute(index)
                .totalHits shouldBe 1
        } finally {
            cluster.deleteIndex(index.name)
        }
    }
}
