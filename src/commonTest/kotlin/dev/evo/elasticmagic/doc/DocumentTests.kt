package dev.evo.elasticmagic.doc

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.query.Script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
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
    fun testRuntimeFields() {
        val emptyDoc = object : Document() {
            override val runtime = object : RuntimeFields() {
                val fullName by runtime(
                    "full_name",
                    KeywordType,
                    Script(
                        Script.Source("emit(doc['first_name'].value + doc['last_name'].value)")
                    )
                )
            }
        }

        emptyDoc.runtime.score.getFieldType() shouldBe DoubleType
        emptyDoc.runtime.score.getFieldName() shouldBe "_score"
        emptyDoc.runtime.score.getQualifiedFieldName() shouldBe "_score"
        emptyDoc.runtime.doc.getFieldType() shouldBe IntType
        emptyDoc.runtime.doc.getFieldName() shouldBe "_doc"
        emptyDoc.runtime.doc.getQualifiedFieldName() shouldBe "_doc"
        emptyDoc.runtime.seqNo.getFieldType() shouldBe LongType
        emptyDoc.runtime.seqNo.getFieldName() shouldBe "_seq_no"
        emptyDoc.runtime.seqNo.getQualifiedFieldName() shouldBe "_seq_no"
        emptyDoc.runtime.fullName.getFieldType() shouldBe KeywordType
        emptyDoc.runtime.fullName.getFieldName() shouldBe "full_name"
        emptyDoc.runtime.fullName.getQualifiedFieldName() shouldBe "full_name"
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
        val myType = object : FieldType<String, String> {
            override val name = "mine"
            override val termType = String::class

            override fun serializeTerm(v: String): Any {
                return "me"
            }

            override fun deserialize(v: Any, valueFactory: (() -> String)?): String {
                return v.toString()
            }

            override fun deserializeTerm(v: Any): String = deserialize(v)
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
            val message by text(
                norms = false, boost = 0.5, analyzer = "standard"
            )
            val requestId by keyword(
                normalizer = "no_spaces"
            )
            val threadId by int(
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
        class NameFields<T>(field: BoundField<T, T>) : SubFields<T>(field) {
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
        class OpinionDoc(field: DocSourceField) : SubDocument(field) {
            val count by int()
        }

        class CompanyDoc(field: DocSourceField) : SubDocument(field) {
            val name by text()
            val opinion by obj(::OpinionDoc)
        }

        class UserDoc : Document() {
            val company by obj(::CompanyDoc)
            val opinion by obj(::OpinionDoc)
        }

        val userDoc = UserDoc()
        userDoc.company.getFieldType().shouldBeInstanceOf<ObjectType<*>>()
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
        class CompanyDoc(field: DocSourceField) : SubDocument(field)

        val myDoc = object : Document() {
            val company by obj(::CompanyDoc)
        }

        class UserDoc : Document() {
            val company1 by obj({ myDoc.company })
        }

        val ex = shouldThrow<IllegalStateException> {
            UserDoc()
        }
        ex.message shouldBe "Field [company1] has already been initialized as [company]"
    }

    @Test
    fun testMergeDocuments() {
        class OpinionNameFields<T>(field: BoundField<T, T>) : SubFields<T>(field) {
            val sort by keyword()
        }

        class OpinionUserDoc(field: DocSourceField) : SubDocument(field) {
            val title by text().subFields(::OpinionNameFields)
            val phone by text()
        }

        class OpinionDoc : Document() {
            val text by text()
            val user by obj(::OpinionUserDoc)

        }
        val opinionDoc = OpinionDoc()

        class AnswerNameFields<T>(field: BoundField<T, T>) : SubFields<T>(field) {
            val autocomplete by text()
        }

        class AnswerUserDoc(field: DocSourceField) : SubDocument(field) {
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
        mergedDoc["text"] shouldBeSameInstanceAs opinionDoc.text
        mergedDoc["opinionId"] shouldBeSameInstanceAs answerDoc.opinionId

        val mergedUserDoc = mergedDoc["user"]
            .shouldBeInstanceOf<SubDocumentField<*>>()
            .subDocument
        mergedUserDoc["phone"] shouldBeSameInstanceAs opinionDoc.user.phone
        mergedUserDoc["companyId"] shouldBeSameInstanceAs answerDoc.user.companyId

        val opinionTitleSubFields = mergedUserDoc["title"]
            .shouldBeInstanceOf<SubFieldsField<*>>()
            .subFields
        // val opinionTitleFields = opinionTitleSubFields.getFieldsByName()
        opinionTitleSubFields["sort"] shouldBeSameInstanceAs opinionDoc.user.title.sort
        opinionTitleSubFields["autocomplete"] shouldBeSameInstanceAs answerDoc.user.title.autocomplete
    }

    @Test
    fun testMergeDocuments_subFields() {
        class UserNameFields(field: BoundField<String, String>) : SubFields<String>(field) {
            val sort by keyword()
        }

        class UserDoc : Document() {
            val firstName by text("first_name").subFields(::UserNameFields)
            val lastName by text("last_name")
        }
        val userDoc = UserDoc()

        class CompanyNameFields(field: BoundField<String, String>) : SubFields<String>(field) {
            val autocomplete by keyword()
        }

        class CompanyDoc : Document() {
            val firstName by text("first_name")
            val lastName by text("last_name").subFields(::CompanyNameFields)
        }
        val companyDoc = CompanyDoc()

        val mergedDoc = mergeDocuments(userDoc, companyDoc)
        val firstNameFields = mergedDoc["first_name"]
            .shouldBeInstanceOf<SubFieldsField<*>>()
            .subFields
        firstNameFields["sort"] shouldBeSameInstanceAs userDoc.firstName.sort
        firstNameFields["autocomplete"].shouldBeNull()
        val lastNameFields = mergedDoc["last_name"]
            .shouldBeInstanceOf<SubFieldsField<*>>()
            .subFields
        lastNameFields["sort"].shouldBeNull()
        lastNameFields["autocomplete"] shouldBeSameInstanceAs companyDoc.lastName.autocomplete
    }

    @Test
    fun testMergeDocuments_subFieldsWithDifferentTypes() {
        class NameFields(field: BoundField<String, String>) : SubFields<String>(field) {
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
        mergedDoc["name"] shouldBeSameInstanceAs userDoc.name
        mergedDoc["company_name"] shouldBeSameInstanceAs companyDoc.name
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
        class OpinionDoc(field: DocSourceField) : SubDocument(field) {
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
        class OpinionDoc(field: DocSourceField) : SubDocument(field) {
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

    @Test
    fun testMergeDocuments_sameRuntimeFields() {
        val userDoc = object : Document() {
            val firstName by keyword()
            val lastName by keyword()

            override val runtime = object : RuntimeFields() {
                val fullName by runtime(
                    KeywordType,
                    Script(
                        Script.Source(
                            "emit(doc[params.firstNameField].value + doc[params.lastNameField].value)"
                        ),
                        params = Params(
                            "firstNameField" to firstName,
                            "lastNameField" to lastName,
                        )
                    )
                )
            }
        }

        val companyDoc = object : Document() {
            val firstName by keyword()
            val lastName by keyword()

            override val runtime = object : RuntimeFields() {
                val fullName by runtime(
                    KeywordType,
                    Script(
                        Script.Source(
                            "emit(doc[params.firstNameField].value + doc[params.lastNameField].value)"
                        ),
                        params = Params(
                            "firstNameField" to firstName,
                            "lastNameField" to lastName,
                        )
                    )
                )
            }
        }

        val mergedDoc = mergeDocuments(userDoc, companyDoc)
        val fullNameField = mergedDoc.runtime["fullName"].shouldNotBeNull()
        fullNameField shouldBeSameInstanceAs userDoc.runtime.fullName
    }

    @Test
    fun testMergeDocuments_differentRuntimeFields() {
        val userDoc = object : Document() {
            val firstName by keyword()
            val lastName by keyword()

            override val runtime = object : RuntimeFields() {
                val fullName by runtime(
                    KeywordType,
                    Script(
                        Script.Source(
                            "emit(doc[params.firstNameField].value + doc[params.lastNameField].value)"
                        ),
                        params = Params(
                            "firstNameField" to firstName,
                            "lastNameField" to lastName,
                        )
                    )
                )
            }
        }

        val companyDoc = object : Document() {
            val firstName by keyword()
            val lastName by keyword()

            override val runtime = object : RuntimeFields() {
                val fullName by runtime(
                    KeywordType,
                    Script(
                        Script.Source(
                            "emit(doc[params.lastNameField].value + doc[params.firstNameField].value)"
                        ),
                        params = Params(
                            "firstNameField" to firstName,
                            "lastNameField" to lastName,
                        )
                    )
                )
            }
        }

        val exc = shouldThrow<IllegalArgumentException> {
            mergeDocuments(userDoc, companyDoc)
        }
        exc.message shouldStartWith "fullName has different field params:"
    }

    // TODO: Tests that must not be compiled
    // @Test
    // fun forbidChangingNameOfMetaFields() {
    //     val userDoc = object : Document() {
    //         val id by int()
    //         val login by keyword()
    //
    //         override val meta = object : MetaFields() {
    //             override val source by MetaField("source", KeywordType, emptyMap())
    //         }
    //     }
    // }
    //
    // @Test
    // fun forbidSubDocumentInsideSubFields() {
    //     class OpinionDoc(field: DocSourceField) : SubDocument(field) {
    //         val stars by float()
    //     }
    //
    //     class NameFields(field: BoundField<String>) : SubFields<String>(field) {
    //         val sort by keyword()
    //         val opinion by obj(::OpinionDoc)
    //     }
    // }
    //
    // @Test
    // fun forbidSubFieldsInsideSubFields() {
    //     class AutocompleteFields(field: BoundField<String>) : SubFields<String>(field) {
    //         val autocomplete by keyword()
    //     }
    //
    //     class NameFields(field: BoundField<String>) : SubFields<String>(field) {
    //         val sort by keyword().subFields(::NameFields)
    //     }
    // }
}
