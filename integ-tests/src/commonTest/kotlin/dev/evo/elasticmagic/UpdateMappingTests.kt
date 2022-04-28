package dev.evo.elasticmagic

import dev.evo.elasticmagic.bulk.CreateAction
import dev.evo.elasticmagic.bulk.IdActionMeta
import dev.evo.elasticmagic.bulk.IndexAction
import dev.evo.elasticmagic.bulk.Refresh
import dev.evo.elasticmagic.doc.DocSource
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.Dynamic
import dev.evo.elasticmagic.query.match

import io.kotest.matchers.shouldBe

import kotlin.test.Test

object UserV1Doc : Document(dynamic = Dynamic.FALSE) {
    val id by int()
}

object UserV2Doc : Document(dynamic = Dynamic.FALSE) {
    val id by int()
    val name by text()
}

class UserDocSource : DocSource() {
    var id by UserV2Doc.id.required()
    var name by UserV2Doc.name
}

class UpdateMappingTests : ElasticsearchTestBase() {
    override val indexName = "update-mapping"
    
    @Test
    fun testUpdateMapping() = runTestWithTransports {
        withTestIndex(UserV1Doc) {
            index.bulk(listOf(
                CreateAction(
                    meta = IdActionMeta("1"),
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
                    meta = IdActionMeta("1"),
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
