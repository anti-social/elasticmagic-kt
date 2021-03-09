package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.SubDocument
import dev.evo.elasticmagic.SubFields

import io.kotest.matchers.shouldBe
import io.kotest.matchers.maps.shouldContainExactly

import kotlin.test.Test

class MappingCompilerTests {
    private val compiler = MappingCompiler(StdSerializer())

    @Test
    fun testEmptyMapping() {
        val emptyDoc = object : Document() {}

        val res = compiler.compile(emptyDoc)
        res.docType shouldBe "_doc"
        res.body shouldContainExactly mapOf(
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

        val res = compiler.compile(productDoc)
        res.docType shouldBe "product"
        res.body shouldContainExactly mapOf(
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

        val res = compiler.compile(userDoc)
        res.docType shouldBe "user"
        res.body shouldContainExactly mapOf(
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