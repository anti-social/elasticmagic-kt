package dev.evo.elasticmagic

import dev.evo.elasticmagic.doc.CreateAction
import dev.evo.elasticmagic.doc.DocSource
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.Dynamic
import dev.evo.elasticmagic.doc.IdentActionMeta
import dev.evo.elasticmagic.doc.IndexAction
import dev.evo.elasticmagic.doc.Refresh

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
        withTestIndex(UserV1Doc) {
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
        }
    }
}
