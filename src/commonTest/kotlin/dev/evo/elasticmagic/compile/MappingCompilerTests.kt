package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.SubDocument
import dev.evo.elasticmagic.SubFields
import dev.evo.elasticmagic.serde.StdSerializer

import io.kotest.matchers.shouldBe
import io.kotest.matchers.maps.shouldContainExactly

import kotlin.test.Test

class MappingCompilerTests {
    private val serializer = object : StdSerializer() {
        override fun objToString(obj: Map<String, Any?>): String {
            TODO("not implemented")
        }
    }
    private val compiler = MappingCompiler(
        ElasticsearchVersion(6, 0, 0),
    )

    @Test
    fun testEmptyMapping() {
        val emptyDoc = object : Document() {}

        val compiled = compiler.compile(serializer, emptyDoc)
        compiled.docType shouldBe "_doc"
        compiled.body!! shouldContainExactly mapOf(
            "properties" to emptyMap<String, Any>()
        )
    }

    @Test
    fun testSubFields() {
        class NameFields<T> : SubFields<T>() {
            val sort by keyword(normalizer = "lowercase")
            val autocomplete by text(analyzer = "ngram")
        }

        val productDoc = object : Document() {
            override val docType = "product"

            val name by text(analyzer = "standard").subFields(::NameFields)
            val keywords by text().subFields(::NameFields)
        }

        val compiled = compiler.compile(serializer, productDoc)
        compiled.docType shouldBe "product"
        compiled.body!! shouldContainExactly mapOf(
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
    fun testSubDocument() {
        class OpinionDoc : SubDocument() {
            val count by int()
        }

        class CompanyDoc : SubDocument() {
            val name by text(analyzer = "standard")
            val opinion by obj(::OpinionDoc, params = Params("enabled" to false))
        }

        val userDoc = object : Document() {
            override val docType = "user"

            val company by obj(::CompanyDoc)
            val opinion by obj(::OpinionDoc)
        }

        val compiled = compiler.compile(serializer, userDoc)
        compiled.docType shouldBe "user"
        compiled.body!! shouldContainExactly mapOf(
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
}