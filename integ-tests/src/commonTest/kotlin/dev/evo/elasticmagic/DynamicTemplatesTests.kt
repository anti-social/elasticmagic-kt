package dev.evo.elasticmagic

import dev.evo.elasticmagic.bulk.DocSourceAndMeta
import dev.evo.elasticmagic.bulk.IdActionMeta
import dev.evo.elasticmagic.doc.BoundField
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.DynDocSource
import dev.evo.elasticmagic.doc.DynamicTemplates
import dev.evo.elasticmagic.doc.SubFields
import dev.evo.elasticmagic.transport.ElasticsearchException

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

import kotlin.test.Test

class IdSubFields(field: BoundField<Long, Long>) : SubFields<Long>(field) {
    val id by keyword(docValues = false)
}

object ProductTemplates : DynamicTemplates() {
    val ids by template(
        mapping = long(index = false).subFields(::IdSubFields),
        match = "*_id"
    )
}

object ProductDoc : Document() {
    override val dynamicTemplates = ProductTemplates
}

class DynamicTemplatesTests : ElasticsearchTestBase() {
    override val indexName = "dynamic-templates"

    @Test
    fun dynamicTemplates() = runTestWithTransports {
        val companyIdField = ProductDoc.dynamicTemplates.ids.field("company_id")

        val p1 = DynDocSource {
            it[companyIdField] = 10
        }
        val p2 = DynDocSource {
            it[companyIdField] = 2
        }

        withFixtures(
            ProductDoc,
            listOf(
                DocSourceAndMeta(IdActionMeta("1"), p1),
                DocSourceAndMeta(IdActionMeta("2"), p2),
            ),
        ) {
            shouldThrow<ElasticsearchException.BadRequest> {
                // Field does not support fielddata
                SearchQuery()
                    .sort(companyIdField.id)
                    .execute(index)
            }
            val totalHits = SearchQuery()
                .filter(companyIdField.id eq "10")
                .execute(index)
                .totalHits
            totalHits shouldBe 1L

            val hits = SearchQuery()
                .sort(companyIdField.asc())
                .execute(index)
                .hits
            hits.size shouldBe 2
            hits[0].id shouldBe "2"
            hits[1].id shouldBe "1"
        }
    }
}
