package dev.evo.elasticmagic

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

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
        val myType = object : SimpleFieldType<String>() {
            override val name = "mine"
            override fun deserialize(v: Any, valueFactory: (() -> String)?): String {
                return v.toString()
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
        userDoc.company.getFieldType().shouldBeInstanceOf<ObjectType<*, *>>()
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
            val company1 by obj({ companyDoc })
            val company2 by obj({ companyDoc })
        }

        val ex = shouldThrow<IllegalStateException> {
            UserDoc()
        }
        ex.message shouldBe "Field [company2] has already been initialized as [company1]"
    }

    @Test
    fun testMergeDocuments() {
        class OpinionNameFields<T> : SubFields<T>() {
            val sort by keyword()
        }

        class OpinionUserDoc : SubDocument() {
            val title by text().subFields(::OpinionNameFields)
            val phone by text()
        }

        class OpinionDoc : Document() {
            val text by text()
            val user by obj(::OpinionUserDoc)

        }
        val opinionDoc = OpinionDoc()

        class AnswerNameFields<T> : SubFields<T>() {
            val autocomplete by text()
        }

        class AnswerUserDoc : SubDocument() {
            val title by text().subFields(::AnswerNameFields)
            val companyId by int()
        }

        class AnswerDoc : Document() {
            val opinionId by int()
            val text by text()
            val user by obj(::AnswerUserDoc)
        }
        val answerDoc = AnswerDoc()

        val mergedDoc = mergeDocuments(opinionDoc, answerDoc)
        mergedDoc.fields["text"] shouldBeSameInstanceAs opinionDoc.text
        mergedDoc.fields["opinionId"] shouldBeSameInstanceAs answerDoc.opinionId

        val mergedUserDoc = mergedDoc
            .fields["user"].shouldBeInstanceOf<SubDocument.FieldWrapper>()
            .subDocument
        mergedUserDoc.fields["phone"] shouldBeSameInstanceAs opinionDoc.user.phone
        mergedUserDoc.fields["companyId"] shouldBeSameInstanceAs answerDoc.user.companyId

        val opinionTitleSubFields = mergedUserDoc
            .fields["title"].shouldBeInstanceOf<SubFields.FieldWrapper<*>>()
            .subFields
        opinionTitleSubFields.fields["sort"] shouldBeSameInstanceAs opinionDoc.user.title.sort
        opinionTitleSubFields.fields["autocomplete"] shouldBeSameInstanceAs answerDoc.user.title.autocomplete
    }

    @Test
    fun testMergeDocuments_subFields() {
        class UserNameFields<T> : SubFields<T>() {
            val sort by keyword()
        }

        class UserDoc : Document() {
            val firstName by text("first_name").subFields(::UserNameFields)
            val lastName by text("last_name")
        }
        val userDoc = UserDoc()

        class CompanyNameFields<T> : SubFields<T>() {
            val autocomplete by keyword()
        }

        class CompanyDoc : Document() {
            val firstName by text("first_name")
            val lastName by text("last_name").subFields(::CompanyNameFields)
        }
        val companyDoc = CompanyDoc()

        val mergedDoc = mergeDocuments(userDoc, companyDoc)
        val firstNameSubFields = mergedDoc
            .fields["first_name"].shouldBeInstanceOf<SubFields.FieldWrapper<*>>()
            .subFields
        firstNameSubFields.fields["sort"] shouldBeSameInstanceAs userDoc.firstName.sort
        firstNameSubFields.fields shouldNotContainKey "autocomplete"
        val lastNameSubFields = mergedDoc
            .fields["last_name"].shouldBeInstanceOf<SubFields.FieldWrapper<*>>()
            .subFields
        lastNameSubFields.fields shouldNotContainKey "sort"
        lastNameSubFields.fields["autocomplete"] shouldBeSameInstanceAs companyDoc.lastName.autocomplete
    }

    @Test
    fun testMergeDocuments_subFieldsWithDifferentTypes() {
        class NameFields<T> : SubFields<T>() {
            val sort by keyword()
        }

        class UserDoc : Document() {
            val name by text().subFields(::NameFields)
        }
        val userDoc = UserDoc()

        class CompanyDoc : Document() {
            val name by keyword().subFields(::NameFields)
        }
        val companyDoc = CompanyDoc()

        shouldThrow<IllegalArgumentException> {
            mergeDocuments(userDoc, companyDoc)
        }
    }

    @Test
    fun testMergeDocuments_samePropertyDifferentNames() {
        class UserDoc : Document() {
            val name by text()
        }
        val userDoc = UserDoc()

        class CompanyDoc : Document() {
            val name by text("company_name")
        }
        val companyDoc = CompanyDoc()

        val mergedDoc = mergeDocuments(userDoc, companyDoc)
        mergedDoc.fields["name"] shouldBeSameInstanceAs userDoc.name
        mergedDoc.fields["company_name"] shouldBeSameInstanceAs companyDoc.name
    }

    @Test
    fun testMergeDocuments_differentTypes() {
        class UserDoc : Document() {
            val name by text()
        }
        val userDoc = UserDoc()

        class CompanyDoc : Document() {
            val name by keyword()
        }
        val companyDoc = CompanyDoc()

        shouldThrow<IllegalArgumentException> {
            mergeDocuments(userDoc, companyDoc)
        }
    }

    @Test
    fun testMergeDocuments_mergeObjectWithNested() {
        class OpinionDoc : SubDocument() {
            val stars by float()
        }

        class UserDoc : Document() {
            val opinion by obj(::OpinionDoc)
        }
        val userDoc = UserDoc()

        class CompanyDoc : Document() {
            val opinion by nested(::OpinionDoc)
        }
        val companyDoc = CompanyDoc()

        shouldThrow<IllegalArgumentException> {
            mergeDocuments(userDoc, companyDoc)
        }
    }

    @Test
    fun testMergeDocuments_differentMappingParams() {
        class UserDoc : Document() {
            val name by text(analyzer = "uk")
        }
        val userDoc = UserDoc()

        class CompanyDoc : Document() {
            val name by text(analyzer = "us")
        }
        val companyDoc = CompanyDoc()

        shouldThrow<IllegalArgumentException> {
            mergeDocuments(userDoc, companyDoc)
        }
    }

    @Test
    fun testMergeDocuments_subDocumentsWithDifferentMappingParams() {
        class OpinionDoc : SubDocument() {
            val stars by float()
        }

        class UserDoc : Document() {
            val opinion by obj(::OpinionDoc)
        }
        val userDoc = UserDoc()

        class CompanyDoc : Document() {
            val opinion by obj(::OpinionDoc, enabled = false)
        }
        val companyDoc = CompanyDoc()

        shouldThrow<IllegalArgumentException> {
            mergeDocuments(userDoc, companyDoc)
        }
    }
}