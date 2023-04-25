package dev.evo.elasticmagic

import dev.evo.elasticmagic.aggs.TermBucket
import dev.evo.elasticmagic.aggs.TermsAgg
import dev.evo.elasticmagic.aggs.TermsAggResult
import dev.evo.elasticmagic.bulk.DocSourceAndMeta
import dev.evo.elasticmagic.bulk.IdActionMeta
import dev.evo.elasticmagic.doc.DocSource
import dev.evo.elasticmagic.doc.DocSourceFactory
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.MetaFields
import dev.evo.elasticmagic.query.HasChild
import dev.evo.elasticmagic.query.HasParent
import dev.evo.elasticmagic.query.match
import dev.evo.elasticmagic.query.MatchAll
import dev.evo.elasticmagic.types.Join
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
    fun testParentChildQueries() = runTestWithSerdes {
        val q1 = QuestionDocSource().apply {
            id = 1
            join = Join("question")
            text = "What is the answer to life, the universe, and everything?"
        }
        val q1A1 = AnswerDocSource().apply {
            id = 1
            join = Join("answer", "q~1")
            text = "Maybe 50?"
        }
        val q1A2 = AnswerDocSource().apply {
            id = 2
            join = Join("answer", "q~1")
            text = "It's 42."
            accepted = true
        }

        val q2 = QuestionDocSource().apply {
            id = 2
            join = Join("question")
            text = "I cannot think of a question, so I'll just ask you to guess the number I'm thinking of."
        }
        val q2A1 = AnswerDocSource().apply {
            id = 3
            join = Join("answer", "q~2")
            text = "8"
        }
        val q2A2 = AnswerDocSource().apply {
            id = 4
            join = Join("answer", "q~2")
            text = "11"
        }
        val q2A3 = AnswerDocSource().apply {
            id = 5
            join = Join("answer", "q~2")
            text = "17"
            accepted = true
        }


        val docSources = listOf(
            DocSourceAndMeta(IdActionMeta("q~1", routing = "q~1"), q1),
            DocSourceAndMeta(IdActionMeta("a~1", routing = "q~1"), q1A1),
            DocSourceAndMeta(IdActionMeta("a~2", routing = "q~1"), q1A2),
            DocSourceAndMeta(IdActionMeta("q~2", routing = "q~2"), q2),
            DocSourceAndMeta(IdActionMeta("a~3", routing = "q~2"), q2A1),
            DocSourceAndMeta(IdActionMeta("a~4", routing = "q~2"), q2A2),
            DocSourceAndMeta(IdActionMeta("a~5", routing = "q~2"), q2A3),
        )
        withFixtures(listOf(QuestionDoc, AnswerDoc), docSources) {
            val sourceFactory = DocSourceFactory.byJoin(
                KnowledgeDoc.join,
                "question" to DocSourceFactory.FromSource(::QuestionDocSource),
                "answer" to DocSourceFactory.FromSource(::AnswerDocSource),
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
                        source = q1A1,
                    ),
                    SearchHit(
                        index = index.name,
                        type = "_doc",
                        id = "a~2",
                        routing = "q~1",
                        score = 1.0F,
                        source = q1A2,
                    ),
                    SearchHit(
                        index = index.name,
                        type = "_doc",
                        id = "q~2",
                        routing = "q~2",
                        score = 1.0F,
                        source = q2,
                    ),
                    SearchHit(
                        index = index.name,
                        type = "_doc",
                        id = "a~3",
                        routing = "q~2",
                        score = 1.0F,
                        source = q2A1,
                    ),
                    SearchHit(
                        index = index.name,
                        type = "_doc",
                        id = "a~4",
                        routing = "q~2",
                        score = 1.0F,
                        source = q2A2,
                    ),
                    SearchHit(
                        index = index.name,
                        type = "_doc",
                        id = "a~5",
                        routing = "q~2",
                        score = 1.0F,
                        source = q2A3,
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
                            source = q1A1,
                        ),
                        SearchHit(
                            index = index.name,
                            type = "_doc",
                            id = "a~2",
                            routing = "q~1",
                            score = 0.0F,
                            source = q1A2,
                        ),
                        SearchHit(
                            index = index.name,
                            type = "_doc",
                            id = "a~3",
                            routing = "q~2",
                            score = 0.0F,
                            source = q2A1,
                        ),
                        SearchHit(
                            index = index.name,
                            type = "_doc",
                            id = "a~4",
                            routing = "q~2",
                            score = 0.0F,
                            source = q2A2,
                        ),
                        SearchHit(
                            index = index.name,
                            type = "_doc",
                            id = "a~5",
                            routing = "q~2",
                            score = 0.0F,
                            source = q2A3,
                        )
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
                    parentsAgg.buckets shouldBe listOf(
                        TermBucket("q~2", docCount = 4),
                        TermBucket("q~1", docCount = 3)
                    )
                }

            SearchQuery(sourceFactory)
                .filter(HasParent(QuestionDoc.text.match("What"), "question"))
                .execute(index)
                .let { searchResult ->
                    searchResult.hits.toSet() shouldContainExactly setOf(
                        SearchHit(
                            index = index.name,
                            type = "_doc",
                            id = "a~1",
                            routing = "q~1",
                            score = 0.0F,
                            source = q1A1,
                        ),
                        SearchHit(
                            index = index.name,
                            type = "_doc",
                            id = "a~2",
                            routing = "q~1",
                            score = 0.0F,
                            source = q1A2,
                        )
                    )
                }

            SearchQuery(sourceFactory)
                .filter(HasChild(MatchAll, "answer", minChildren = 1))
                .execute(index)
                .let { searchResult ->
                    searchResult.hits.toSet() shouldContainExactly setOf(
                        SearchHit(
                            index = index.name,
                            type = "_doc",
                            id = "q~1",
                            routing = "q~1",
                            score = 0.0f,
                            source = q1,
                        ),
                        SearchHit(
                            index = index.name,
                            type = "_doc",
                            id = "q~2",
                            routing = "q~2",
                            score = 0.0f,
                            source = q2,
                        )
                    )
                }

            SearchQuery(sourceFactory)
                .filter(HasChild(MatchAll, "answer", maxChildren = 2))
                .execute(index)
                .let { searchResult ->
                    searchResult.hits.toSet() shouldContainExactly setOf(
                        SearchHit(
                            index = index.name,
                            type = "_doc",
                            id = "q~1",
                            routing = "q~1",
                            score = 0.0f,
                            source = q1,
                        )
                    )
                }

            SearchQuery(sourceFactory)
                .filter(HasChild(MatchAll, "answer", minChildren = 3))
                .execute(index)
                .let { searchResult ->
                    searchResult.hits.toSet() shouldContainExactly setOf(
                        SearchHit(
                            index = index.name,
                            type = "_doc",
                            id = "q~2",
                            routing = "q~2",
                            score = 0.0f,
                            source = q2,
                        )
                    )
                }
        }
    }
}
