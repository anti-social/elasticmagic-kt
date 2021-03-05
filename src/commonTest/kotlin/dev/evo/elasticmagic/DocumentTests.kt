package dev.evo.elasticmagic

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import kotlin.test.Test

class DocumentTests {

    @Test
    fun testMetaFields() {
        val emptyDoc = object : Document() {}

        emptyDoc.meta.id.getFieldType() shouldBe KeywordType
        emptyDoc.meta.id.getFieldName() shouldBe "_id"
        emptyDoc.meta.id.getQualifiedFieldName() shouldBe "_id"
        emptyDoc.meta.type.getFieldName() shouldBe "_type"
        emptyDoc.meta.type.getQualifiedFieldName() shouldBe "_type"
        emptyDoc.meta.index.getFieldName() shouldBe "_index"
        emptyDoc.meta.index.getQualifiedFieldName() shouldBe "_index"
        emptyDoc.meta.routing.getFieldName() shouldBe "_routing"
        emptyDoc.meta.routing.getQualifiedFieldName() shouldBe "_routing"
        emptyDoc.meta.fieldNames.getFieldName() shouldBe "_field_names"
        emptyDoc.meta.fieldNames.getQualifiedFieldName() shouldBe "_field_names"
        emptyDoc.meta.ignored.getFieldName() shouldBe "_ignored"
        emptyDoc.meta.ignored.getQualifiedFieldName() shouldBe "_ignored"
        emptyDoc.meta.source.getFieldName() shouldBe "_source"
        emptyDoc.meta.source.getQualifiedFieldName() shouldBe "_source"
        emptyDoc.meta.size.getFieldType() shouldBe LongType
        emptyDoc.meta.size.getFieldName() shouldBe "_size"
        emptyDoc.meta.size.getQualifiedFieldName() shouldBe "_size"

        emptyDoc.meta.uid.getFieldName() shouldBe "_uid"
        emptyDoc.meta.uid.getQualifiedFieldName() shouldBe "_uid"
        emptyDoc.meta.parent.getFieldName() shouldBe "_parent"
        emptyDoc.meta.parent.getQualifiedFieldName() shouldBe "_parent"
    }

    @Test
    fun testSubDocument() {
        class OpinionDoc : SubDocument() {
            val count by int()
        }

        class CompanyDoc : SubDocument() {
            val name by text()
            val opinion by obj(::OpinionDoc)
        }

        class UserDoc : Document() {
            val company by obj(::CompanyDoc)
            val opinion by obj(::OpinionDoc)
        }

        val userDoc = UserDoc()
        userDoc.getFieldName() shouldBe ""
        userDoc.getQualifiedFieldName() shouldBe ""
        userDoc.company.getFieldName() shouldBe "company"
        userDoc.company.getQualifiedFieldName() shouldBe "company"
        userDoc.company.name.getFieldName() shouldBe "name"
        userDoc.company.name.getQualifiedFieldName() shouldBe "company.name"
        userDoc.company.name.getFieldType() shouldBe TextType
        userDoc.company.opinion.getFieldName() shouldBe "opinion"
        userDoc.company.opinion.getQualifiedFieldName() shouldBe "company.opinion"
        userDoc.company.opinion.count.getFieldName() shouldBe "count"
        userDoc.company.opinion.count.getQualifiedFieldName() shouldBe "company.opinion.count"
        userDoc.company.opinion.count.getFieldType() shouldBe IntType
        userDoc.opinion.count.getFieldName() shouldBe "count"
        userDoc.opinion.count.getQualifiedFieldName() shouldBe "opinion.count"
        userDoc.opinion.count.getFieldType() shouldBe IntType
    }

    @Test
    fun testSubDocument_preventDoubleInitialization() {
        class CompanyDoc : SubDocument()
        val companyDoc = CompanyDoc()

        class UserDoc : Document() {
            val company1 by obj { companyDoc }
            val company2 by obj { companyDoc }
        }

        val ex = shouldThrow<IllegalStateException> {
            UserDoc()
        }
        ex.message shouldBe "Field [company2] has already been initialized as [company1]"
    }

    @Test
    fun testSubDocument_preventUninitialized() {
        class CompanyDoc : Document() {
            val name by text()
        }

        class UserDoc : Document() {
            val company by obj(::CompanyDoc)
        }

        val ex = shouldThrow<IllegalStateException> {
            UserDoc()
        }
        ex.message shouldStartWith "Document instance cannot be a sub document:"
    }
}