package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.BaseTest
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.doc.BoundField
import dev.evo.elasticmagic.doc.DocSourceField
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.Dynamic
import dev.evo.elasticmagic.types.KeywordType
import dev.evo.elasticmagic.doc.SubDocument
import dev.evo.elasticmagic.doc.SubFields
import dev.evo.elasticmagic.doc.datetime
import dev.evo.elasticmagic.doc.mergeDocuments
import dev.evo.elasticmagic.query.Script

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.types.shouldBeInstanceOf

import kotlin.test.Test

class MappingCompilerTests : BaseTest() {
    val esFeatures = ElasticsearchFeatures.ES_6_0
    private val compiler = MappingCompiler(
        esFeatures,
        SearchQueryCompiler(esFeatures)
    )

    private fun compile(doc: Document): Map<String, Any?> {
        val compiled = compiler.compile(serializer, doc)
        return compiled.body.shouldBeInstanceOf<TestSerializer.ObjectCtx>().toMap()
    }

    @Test
    fun emptyMapping() {
        val emptyDoc = object : Document() {}

        compile(emptyDoc) shouldContainExactly mapOf(
            "properties" to emptyMap<String, Any>()
        )
    }

    @Test
    fun mappingOptions() {
        val docWithOptions = object : Document(
            dynamic = Dynamic.STRICT,
            numericDetection = false,
            dateDetection = true,
            dynamicDateFormats = listOf("strict_date_optional_time"),
        ) {}

        compile(docWithOptions) shouldContainExactly mapOf(
            "dynamic" to "strict",
            "numeric_detection" to false,
            "date_detection" to true,
            "dynamic_date_formats" to listOf("strict_date_optional_time"),
            "properties" to emptyMap<String, Any>()
        )
    }

    @Test
    fun subFields() {
        class NameFields<V>(field: BoundField<V, V>) : SubFields<V>(field) {
            val sort by keyword(normalizer = "lowercase")
            val autocomplete by text(analyzer = "ngram")
        }

        val productDoc = object : Document() {
            val name by text(analyzer = "standard").subFields(::NameFields)
            val keywords by text().subFields(::NameFields)
        }

        compile(productDoc) shouldContainExactly mapOf(
            "properties" to mapOf(
                "name" to mapOf(
                    "type" to "text",
                    "analyzer" to "standard",
                    "fields" to mapOf(
                        "sort" to mapOf(
                            "type" to "keyword",
                            "normalizer" to "lowercase",
                        ),
                        "autocomplete" to mapOf(
                            "type" to "text",
                            "analyzer" to "ngram",
                        )
                    )
                ),
                "keywords" to mapOf(
                    "type" to "text",
                    "fields" to mapOf(
                        "sort" to mapOf(
                            "type" to "keyword",
                            "normalizer" to "lowercase",
                        ),
                        "autocomplete" to mapOf(
                            "type" to "text",
                            "analyzer" to "ngram",
                        )
                    )
                )
            )
        )
    }

    @Test
    fun subDocument() {
        class OpinionDoc(field: DocSourceField) : SubDocument(field) {
            val count by int()
        }

        class CompanyDoc(field: DocSourceField) : SubDocument(field) {
            val name by text(analyzer = "standard")
            val opinion by obj(
                ::OpinionDoc, dynamic = Dynamic.TRUE, params = Params("enabled" to false)
            )
        }

        val userDoc = object : Document() {
            val company by obj(::CompanyDoc)
            val opinion by obj(::OpinionDoc)
        }

        compile(userDoc) shouldContainExactly mapOf(
            "properties" to mapOf(
                "company" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf(
                            "type" to "text",
                            "analyzer" to "standard"
                        ),
                        "opinion" to mapOf(
                            "type" to "object",
                            "enabled" to false,
                            "dynamic" to true,
                            "properties" to mapOf(
                                "count" to mapOf(
                                    "type" to "integer"
                                )
                            )
                        ),
                    )
                ),
                "opinion" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "count" to mapOf(
                            "type" to "integer"
                        )
                    )
                ),
            )
        )
    }

    @Test
    fun runtimeFields() {
        val logDoc = object : Document() {
            // TODO: Replace with date field
            val timestamp by datetime("@timestamp")

            val dayOfWeek by runtime(
                "day_of_week",
                KeywordType,
                Script.Source(
                    """
                    emit(
                        doc[params.timestampField].value
                            .dayOfWeekEnum
                            .getDisplayName(TextStyle.FULL, Locale.ROOT)
                    )""".trimIndent(),
                    params = Params(
                        "timestampField" to timestamp
                    )
                )
            )
        }

        compile(logDoc) shouldContainExactly mapOf(
            "runtime" to mapOf(
                "day_of_week" to mapOf(
                    "type" to "keyword",
                    "script" to mapOf(
                        "source" to """
                            emit(
                                doc[params.timestampField].value
                                    .dayOfWeekEnum
                                    .getDisplayName(TextStyle.FULL, Locale.ROOT)
                            )""".trimIndent(),
                        "params" to mapOf(
                            "timestampField" to "@timestamp"
                        )
                    )
                )
            ),
            "properties" to mapOf(
                "@timestamp" to mapOf(
                    "type" to "date",
                ),
            )
        )
    }

    @Test
    fun dynamicTemplates_mappingParams() {
        class MyDoc : Document() {
            val strings by template(
                mapping = Mapping(
                    docValues = false,
                ),
                matchMappingType = MatchMappingType.STRING,
            )
        }
        val myDoc = MyDoc()

        compile(myDoc) shouldContainExactly mapOf(
            "dynamic_templates" to listOf(
                mapOf(
                    "strings" to mapOf(
                        "match_mapping_type" to "string",
                        "mapping" to mapOf(
                            "doc_values" to false,
                        )
                    )
                )
            ),
            "properties" to emptyMap<String, Nothing>()
        )
    }

    @Test
    fun dynamicTemplates_matchMappingType() {
        class MyDoc : Document() {
            val strings by template(
                mapping = keyword(index = false),
                matchMappingType = MatchMappingType.STRING,
            )
        }
        val myDoc = MyDoc()

        compile(myDoc) shouldContainExactly mapOf(
            "dynamic_templates" to listOf(
                mapOf(
                    "strings" to mapOf(
                        "match_mapping_type" to "string",
                        "mapping" to mapOf(
                            "type" to "keyword",
                            "index" to false,
                        )
                    )
                )
            ),
            "properties" to emptyMap<String, Nothing>()
        )
    }

    @Test
    fun dynamicTemplates_subFields() {
        class IdSubFields(field: BoundField<Long, Long>) : SubFields<Long>(field) {
            val id by keyword(docValues = false)
        }

        class MyDoc : Document() {
            val ids by template(
                mapping = long(index = false).subFields(::IdSubFields),
                match = "*_id",
            )
        }
        val myDoc = MyDoc()

        compile(myDoc) shouldContainExactly mapOf(
            "dynamic_templates" to listOf(
                mapOf(
                    "ids" to mapOf(
                        "match" to "*_id",
                        "mapping" to mapOf(
                            "type" to "long",
                            "index" to false,
                            "fields" to mapOf(
                                "id" to mapOf(
                                    "type" to "keyword",
                                    "doc_values" to false,
                                )
                            )
                        )
                    )
                )
            ),
            "properties" to emptyMap<String, Nothing>()
        )
    }

    @Test
    fun dynamicTemplates_subDocument() {
        class UserDoc(field: DocSourceField) : SubDocument(field) {
            val id by keyword()
            val firstName by text("first_name", analyzer="en")
            val lastName by text("last_name", analyzer="en")
        }

        class MyDoc : Document() {
            val users by template(
                mapping = obj(::UserDoc, dynamic = Dynamic.TRUE),
                match = "*_user",
            )
        }
        val myDoc = MyDoc()

        compile(myDoc) shouldContainExactly mapOf(
            "dynamic_templates" to listOf(
                mapOf(
                    "users" to mapOf(
                        "match" to "*_user",
                        "mapping" to mapOf(
                            "type" to "object",
                            "dynamic" to true,
                            "properties" to mapOf(
                                "id" to mapOf(
                                    "type" to "keyword",
                                ),
                                "first_name" to mapOf(
                                    "type" to "text",
                                    "analyzer" to "en",
                                ),
                                "last_name" to mapOf(
                                    "type" to "text",
                                    "analyzer" to "en",
                                )
                            )
                        )
                    )
                )
            ),
            "properties" to emptyMap<String, Nothing>()
        )
    }

    @Test
    fun dynamicTemplates_runtimeWithoutType() {
        class MyDoc : Document() {
            val dates by template(
                runtime = Runtime(),
                match = "*_dt",
            )
        }
        val myDoc = MyDoc()

        compile(myDoc) shouldContainExactly mapOf(
            "dynamic_templates" to listOf(
                mapOf(
                    "dates" to mapOf(
                        "match" to "*_dt",
                        "runtime" to emptyMap<String, Nothing>()
                    )
                )
            ),
            "properties" to emptyMap<String, Nothing>()
        )
    }

    @Test
    fun dynamicTemplates_runtimeWithType() {
        class MyDoc : Document() {
            val dates by template(
                runtime = Runtime(int()),
                match = "*_count",
            )
        }
        val myDoc = MyDoc()

        compile(myDoc) shouldContainExactly mapOf(
            "dynamic_templates" to listOf(
                mapOf(
                    "dates" to mapOf(
                        "match" to "*_count",
                        "runtime" to mapOf(
                            "type" to "integer"
                        )
                    )
                )
            ),
            "properties" to emptyMap<String, Nothing>()
        )
    }

    @Test
    fun mergeDocuments() {
        open class QADoc : Document() {
            val join by join(relations = mapOf("question" to listOf("answer")))
        }

        val questionDoc = object : QADoc() {
            val id by int()
            val text by text()
        }

        val answerDoc = object : QADoc() {
            override val meta = questionDoc.meta

            val id by int()
            val text by text()
            val accepted by boolean()
        }

        val commonDoc = mergeDocuments(questionDoc, answerDoc)

        compile(commonDoc) shouldContainExactly mapOf(
            "properties" to mapOf(
                "join" to mapOf(
                    "type" to "join",
                    "relations" to mapOf(
                        "question" to listOf("answer")
                    )
                ),
                "id" to mapOf(
                    "type" to "integer"
                ),
                "text" to mapOf(
                    "type" to "text"
                ),
                "accepted" to mapOf(
                    "type" to "boolean"
                )
            )
        )
    }
}
