package dev.evo.elasticmagic

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

import kotlin.test.Test

class DocumentTests {

    @Test
    fun testMetaFields() {
        val emptyDoc = object : Document() {}

        emptyDoc.docType shouldBe "_doc"
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
        emptyDoc.meta.all.getFieldName() shouldBe "_all"
        emptyDoc.meta.all.getQualifiedFieldName() shouldBe "_all"
    }

    @Test
    fun testMetaFieldParams() {
        val emptyDoc = object : Document() {
            override val meta = object : MetaFields() {
                override val routing by RoutingField(required = true)
                override val source by SourceField(enabled = false)
                override val size by SizeField(enabled = true)
            }
        }

        emptyDoc.meta.routing.getFieldName() shouldBe "_routing"
        emptyDoc.meta.routing.getQualifiedFieldName() shouldBe "_routing"
        emptyDoc.meta.routing.getMappingParams() shouldContainExactly mapOf(
            "required" to true,
        )
        emptyDoc.meta.source.getFieldName() shouldBe "_source"
        emptyDoc.meta.source.getQualifiedFieldName() shouldBe "_source"
        emptyDoc.meta.source.getMappingParams() shouldContainExactly mapOf(
            "enabled" to false,
        )
        emptyDoc.meta.size.getFieldName() shouldBe "_size"
        emptyDoc.meta.size.getQualifiedFieldName() shouldBe "_size"
        emptyDoc.meta.size.getMappingParams() shouldContainExactly mapOf(
            "enabled" to true,
        )
    }

    @Test
    fun testCustomFieldType() {
        val myType = object : Type<String> {
            override val name = "mine"
            override fun deserialize(v: Any): String {
                TODO("not implemented")
            }
        }

        val userDoc = object : Document() {
            val status by field(myType)
            val cls by field("class", myType)
        }

        userDoc.status.getFieldType() shouldBe myType
        userDoc.status.getFieldName() shouldBe "status"
        userDoc.status.getQualifiedFieldName() shouldBe "status"
        userDoc.cls.getFieldType() shouldBe myType
        userDoc.cls.getFieldName() shouldBe "class"
        userDoc.cls.getQualifiedFieldName() shouldBe "class"
    }

    @Test
    fun testMappingParameters() {
        val logEventDoc = object : Document() {
            val message = text(
                norms = false, boost = 0.5, analyzer = "standard"
            )
            val requestId = keyword(
                normalizer = "no_spaces"
            )
            val threadId = int(
                index = false,
            )
        }

        logEventDoc.message.getFieldType() shouldBe TextType
        logEventDoc.message.getMappingParams() shouldContainExactly mapOf(
            "boost" to 0.5,
            "norms" to false,
            "analyzer" to "standard",
        )
        logEventDoc.requestId.getFieldType() shouldBe KeywordType
        logEventDoc.requestId.getMappingParams() shouldContainExactly mapOf(
            "normalizer" to "no_spaces",
        )
        logEventDoc.threadId.getFieldType() shouldBe IntType
        logEventDoc.threadId.getMappingParams() shouldContainExactly mapOf(
            "index" to false,
        )
    }

    @Test
    fun testSubFields() {
        class NameFields<T> : SubFields<T>() {
            val sort by keyword()
            val autocomplete by text()
        }

        val productDoc = object : Document() {
            val name by text().subFields(::NameFields)
            val keywords by text().subFields(::NameFields)
        }

        productDoc.name.getFieldType() shouldBe TextType
        productDoc.name.getFieldName() shouldBe "name"
        productDoc.name.getQualifiedFieldName() shouldBe "name"
        productDoc.name.sort.getFieldType() shouldBe KeywordType
        productDoc.name.sort.getFieldName() shouldBe "sort"
        productDoc.name.sort.getQualifiedFieldName() shouldBe "name.sort"
        productDoc.name.autocomplete.getFieldType() shouldBe TextType
        productDoc.name.autocomplete.getFieldName() shouldBe "autocomplete"
        productDoc.name.autocomplete.getQualifiedFieldName() shouldBe "name.autocomplete"
        productDoc.keywords.getFieldType() shouldBe TextType
        productDoc.keywords.getFieldName() shouldBe "keywords"
        productDoc.keywords.getQualifiedFieldName() shouldBe "keywords"
        productDoc.keywords.sort.getFieldType() shouldBe KeywordType
        productDoc.keywords.sort.getFieldName() shouldBe "sort"
        productDoc.keywords.sort.getQualifiedFieldName() shouldBe "keywords.sort"
        productDoc.keywords.autocomplete.getFieldType() shouldBe TextType
        productDoc.keywords.autocomplete.getFieldName() shouldBe "autocomplete"
        productDoc.keywords.autocomplete.getQualifiedFieldName() shouldBe "keywords.autocomplete"

        productDoc.name shouldNotBeSameInstanceAs productDoc.keywords
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

        userDoc.opinion shouldNotBeSameInstanceAs userDoc.company.opinion
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
}