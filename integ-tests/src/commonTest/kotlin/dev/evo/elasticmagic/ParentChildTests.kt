package dev.evo.elasticmagic

import dev.evo.elasticmagic.aggs.TermBucket
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.bulk.DocSourceAndMeta
import dev.evo.elasticmagic.doc.DocSource
import dev.evo.elasticmagic.doc.DocSourceFactory
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.bulk.IdActionMeta
import dev.evo.elasticmagic.types.Join
import dev.evo.elasticmagic.doc.MetaFields
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

import kotlin.test.Test

open class KnowledgeDoc : Document() {
    override val meta = META

    val join by join(relations = mapOf("question" to listOf("answer")))
    val id by int()
    val text by text()

    companion object : KnowledgeDoc() {
        private val META = object : MetaFields() {
            override val routing by RoutingField(required = true)
        }
    }
}

object QuestionDoc : KnowledgeDoc()

object AnswerDoc : KnowledgeDoc() {
    val accepted by boolean()
}

class QuestionDocSource : DocSource() {
    var join by QuestionDoc.join.required()
    var id by QuestionDoc.id.required()
    var text by QuestionDoc.text.required()
}

class AnswerDocSource : DocSource() {
    var join by AnswerDoc.join.required()
    var id by AnswerDoc.id.required()
    var text by AnswerDoc.text.required()
    var accepted by AnswerDoc.accepted
}

class ParentChildTests : ElasticsearchTestBase() {
    override val indexName = "parent-child"

    @Test
    fun testParentChildQueries() = runTestWithTransports {
        val q1 = QuestionDocSource().apply {
            id = 1
            join = Join("question")
            text = "What is the answer to life, the universe, and everything?"
        }
        val a1 = AnswerDocSource().apply {
            id = 1
            join = Join("answer", "q~1")
            text = "Maybe 50?"
        }
        val a2 = AnswerDocSource().apply {
            id = 2
            join = Join("answer", "q~1")
            text = "It's 42."
            accepted = true
        }
        val docSources = listOf(
            DocSourceAndMeta(IdActionMeta("q~1", routing = "q~1"), q1),
            DocSourceAndMeta(IdActionMeta("a~1", routing = "q~1"), a1),
            DocSourceAndMeta(IdActionMeta("a~2", routing = "q~1"), a2),
        )
        withFixtures(listOf(QuestionDoc, AnswerDoc), docSources) {
            val sourceFactory = DocSourceFactory.byJoin(
                "question" to ::QuestionDocSource,
                "answer" to ::AnswerDocSource
            )
            SearchQuery(sourceFactory).execute(index).let { searchResult ->
                searchResult.hits.toSet() shouldContainExactly setOf(
                    SearchHit(
                        index = index.name,
                        type = "_doc",
                        id = "q~1",
                        routing = "q~1",
                        score = 1.0F,
                        source = q1,
                    ),
                    SearchHit(
                        index = index.name,
                        type = "_doc",
                        id = "a~1",
                        routing = "q~1",
                        score = 1.0F,
                        source = a1,
                    ),
                    SearchHit(
                        index = index.name,
                        type = "_doc",
                        id = "a~2",
                        routing = "q~1",
                        score = 1.0F,
                        source = a2,
                    ),
                )
            }

            SearchQuery(sourceFactory)
                .filter(AnswerDoc.join.eq("answer"))
                .execute(index)
                .let { searchResult ->
                    searchResult.hits.toSet() shouldContainExactly setOf(
                        SearchHit(
                            index = index.name,
                            type = "_doc",
                            id = "a~1",
                            routing = "q~1",
                            score = 0.0F,
                            source = a1,
                        ),
                        SearchHit(
                            index = index.name,
                            type = "_doc",
                            id = "a~2",
                            routing = "q~1",
                            score = 0.0F,
                            source = a2,
                        ),
                    )
                }

            SearchQuery(sourceFactory)
                .aggs(
                    "parents" to TermsAgg(
                        KnowledgeDoc.join.parent("question")
                    )
                )
                .execute(index)
                .let { searchResult ->
                    val parentsAgg = searchResult.agg<TermsAggResult<String>>("parents")
                    parentsAgg.buckets shouldBe listOf(TermBucket("q~1", docCount = 3))
                }
        }
    }
}
