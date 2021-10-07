package dev.evo.elasticmagic.doc

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.Named
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.query.ToValue

import kotlinx.datetime.LocalDateTime

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Controls dynamic field mapping setting.
 * See: https://www.elastic.co/guide/en/elasticsearch/reference/current/dynamic-field-mapping.html
 */
enum class Dynamic : ToValue {
    TRUE, FALSE, STRICT, RUNTIME;

    override fun toValue() = name.lowercase()
}

/**
 * Represents field of any type in an Elasticsearch document.
 * See [FieldSet.getAllFields] and [FieldSet.getFieldsByName] methods.
 */
interface AnyField : FieldOperations {
    fun getFieldType(): FieldType<*>

    fun getMappingParams(): Params

    fun getParent(): Named

    fun isIgnored(): Boolean
}

/**
 * Represents field of a specific type. Usually it can be accessed as a document property.
 *
 * @param name - name of the field
 * @param type - type of the field
 * @param params - mapping parameters
 * @param parent - the [FieldSet] object to which the field is bound
 */
open class BoundField<V>(
    private val name: String,
    private val type: FieldType<V>,
    private val params: Params,
    private val parent: FieldSet,
    private val ignored: Boolean = false,
) : AnyField {
    private val qualifiedName = run {
        val parentQualifiedName = parent.getQualifiedFieldName()
        if (parentQualifiedName.isNotEmpty()) {
            "${parentQualifiedName}.${getFieldName()}"
        } else {
            getFieldName()
        }
    }

    override fun getFieldName(): String = name

    override fun getQualifiedFieldName(): String = qualifiedName

    override fun getFieldType(): FieldType<V> = type

    override fun getMappingParams(): Params = params

    override fun getParent(): FieldSet = parent

    override fun isIgnored(): Boolean = ignored

    override fun equals(other: Any?): Boolean {
        if (other !is BoundField<*>) {
            return false
        }
        return qualifiedName == other.qualifiedName &&
                type == other.type &&
                params == other.params &&
                ignored == other.ignored
    }

    override fun hashCode(): Int {
        var h = qualifiedName.hashCode()
        h = 37 * h + type.hashCode()
        h = 37 * h + params.hashCode()
        h = 37 * h + ignored.hashCode()
        return h
    }
}

/**
 * Represents join field.
 *
 * @param relations - map of parent to child relations
 *
 * See more at https://www.elastic.co/guide/en/elasticsearch/reference/current/parent-join.html
 */
class BoundJoinField(
    name: String,
    type: FieldType<Join>,
    relations: Map<String, List<String>>,
    params: Params,
    parent: FieldSet,
    ignored: Boolean,
) : BoundField<Join>(name, type, Params(params, "relations" to relations), parent, ignored) {

    inner class Parent(private val name: String) : FieldOperations {
        override fun getFieldName(): String {
            return name
        }

        override fun getQualifiedFieldName(): String {
            return "${this@BoundJoinField.getQualifiedFieldName()}#$name"
        }
    }

    private val parentFields = relations.keys.associateWith { parentFieldName ->
        Parent(parentFieldName)
    }
    
    fun parent(name: String): FieldOperations {
        return parentFields[name]
            ?: throw IllegalArgumentException(
                "Unknown parent relation: $name, possible relations: ${parentFields.keys}"
            )
    }
}

/**
 * Base class for any types which hold set of fields:
 * https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html
 */
abstract class FieldSet : Named {
    private val fields: ArrayList<AnyField> = arrayListOf()
    private val fieldsByName: HashMap<String, Int> = hashMapOf()

    // TODO: consider to make it public
    internal fun addField(field: AnyField) {
        val existingFieldIx = fieldsByName[field.getFieldName()]
        if (existingFieldIx != null) {
            fields[existingFieldIx] = field
        } else {
            fieldsByName[field.getFieldName()] = fields.size
            fields.add(field)
        }
    }

    fun getAllFields(): List<AnyField> {
        return fields.toList()
    }

    operator fun get(name: String): AnyField? {
        return fields[fieldsByName[name] ?: return null]
    }

    fun <T> field(
        name: String?,
        type: FieldType<T>,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): Field<T> {
        @Suppress("NAME_SHADOWING")
        val params = Params(
            params,
            "doc_values" to docValues,
            "index" to index,
            "store" to store,
        )
        return Field(name, type, params)
    }
    fun <T> field(
        type: FieldType<T>,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): Field<T> {
        return field(
            null, type,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun boolean(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): Field<Boolean> {
        return field(
            name, BooleanType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun int(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): Field<Int> {
        return field(
            name, IntType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun long(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): Field<Long> {
        return field(
            name, LongType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun float(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): Field<Float> {
        return field(
            name, FloatType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun double(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): Field<Double> {
        return field(
            name, DoubleType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun date(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): Field<LocalDateTime> {
        return field(
            name, DateTimeType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun keyword(
        name: String? = null,
        normalizer: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): Field<String> {
        @Suppress("NAME_SHADOWING")
        val params = Params(
            params,
            "normalizer" to normalizer,
        )

        return field(
            name, KeywordType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }
    fun text(
        name: String? = null,
        index: Boolean? = null,
        indexOptions: String? = null,
        store: Boolean? = null,
        norms: Boolean? = null,
        boost: Double? = null,
        analyzer: String? = null,
        searchAnalyzer: String? = null,
        params: Params? = null,
    ): Field<String> {
        @Suppress("NAME_SHADOWING")
        val params = Params(
            params,
            "index_options" to indexOptions,
            "norms" to norms,
            "boost" to boost,
            "analyzer" to analyzer,
            "search_analyzer" to searchAnalyzer,
        )
        return field(
            name, TextType,
            index = index,
            store = store,
            params = params,
        )
    }
    fun join(
        name: String? = null,
        relations: Map<String, List<String>>,
        eagerGlobalOrdinals: Boolean? = null,
    ): JoinField {
        val params = Params(
            "eager_global_ordinals" to eagerGlobalOrdinals,
        )
        // TODO: relation sub-fields
        return JoinField(name, JoinType, relations, params = params)
    }

    open class Field<V>(
        val name: String?,
        val type: FieldType<V>,
        val params: Params,
        val ignored: Boolean = false,
    ) {
        operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, BoundField<V>> {
            val field = BoundField(
                name ?: prop.name,
                type,
                params,
                thisRef,
                ignored,
            )
            thisRef.addField(field)
            return ReadOnlyProperty { _, _ -> field }
        }
    }

    class JoinField(
        name: String?,
        type: JoinType,
        val relations: Map<String, List<String>>,
        params: Params,
    ) : Field<Join>(name, type, params) {
        operator fun provideDelegate(
            thisRef: BaseDocument, prop: KProperty<*>
        ): ReadOnlyProperty<BaseDocument, BoundJoinField> {
            val field = BoundJoinField(
                name ?: prop.name,
                type,
                relations,
                params,
                thisRef,
                ignored,
            )
            thisRef.addField(field)
            return ReadOnlyProperty { _, _ -> field }
        }
    }
}

/**
 * Represents Elasticsearch multi-fields:
 * https://www.elastic.co/guide/en/elasticsearch/reference/7.10/multi-fields.html
 */
open class SubFields<V>(private val field: BoundField<V>) : FieldSet() {
    fun getBoundField(): BoundField<V> = field

    fun getFieldType(): FieldType<V> = field.getFieldType()

    override fun getFieldName(): String = field.getFieldName()

    override fun getQualifiedFieldName(): String = field.getQualifiedFieldName()

    class UnboundSubFields<V, F: SubFields<V>>(
        private val unboundField: Field<V>,
        private val subFieldsFactory: (BoundField<V>) -> F,
    ) {
        operator fun provideDelegate(
            thisRef: BaseDocument, prop: KProperty<*>
        ): ReadOnlyProperty<BaseDocument, F> {
            val field = BoundField(
                unboundField.name ?: prop.name,
                unboundField.type,
                unboundField.params,
                thisRef,
            )
            val subFields = subFieldsFactory(field)
            thisRef.addField(SubFieldsField(field, subFields))
            if (subFields.field != field) {
                throw IllegalStateException(
                    "Field [${field.getFieldName()}] has already been initialized as [${subFields.getFieldName()}]"
                )
            }
            return ReadOnlyProperty { _, _ -> subFields }
        }
    }
}

internal open class WrapperField(val field: AnyField) : AnyField {
    override fun getFieldName(): String = field.getFieldName()
    override fun getQualifiedFieldName(): String = field.getQualifiedFieldName()
    override fun getFieldType(): FieldType<*> = field.getFieldType()
    override fun getMappingParams(): Params = field.getMappingParams()
    override fun getParent(): Named = field.getParent()
    override fun isIgnored(): Boolean = field.isIgnored()
}

internal class SubFieldsField(
    field: AnyField,
    val subFields: SubFields<*>
) : WrapperField(field)

abstract class BaseDocument : FieldSet() {
    fun <V, F: SubFields<V>> Field<V>.subFields(factory: (BoundField<V>) -> F): SubFields.UnboundSubFields<V, F> {
        return SubFields.UnboundSubFields(this, factory)
    }

    fun <T: SubDocument> `object`(
        name: String?,
        factory: (DocSourceField) -> T,
        enabled: Boolean? = null,
        params: Params = Params(),
    ): SubDocument.UnboundSubDocument<T> {
        @Suppress("NAME_SHADOWING")
        val params = Params(
            params,
            "enabled" to enabled,
        )
        return SubDocument.UnboundSubDocument(name, ObjectType(), params, factory)
    }
    fun <T: SubDocument> `object`(
        factory: (DocSourceField) -> T,
        enabled: Boolean? = null,
        params: Params = Params(),
    ): SubDocument.UnboundSubDocument<T> {
        return `object`(null, factory, enabled, params)
    }
    fun <T: SubDocument> obj(
        name: String?,
        factory: (DocSourceField) -> T,
        enabled: Boolean? = null,
        params: Params = Params(),
    ): SubDocument.UnboundSubDocument<T> {
        return `object`(name, factory, enabled, params)
    }
    fun <T: SubDocument> obj(
        factory: (DocSourceField) -> T,
        enabled: Boolean? = null,
        params: Params = Params(),
    ): SubDocument.UnboundSubDocument<T> {
        return `object`(factory, enabled, params)
    }

    fun <T: SubDocument> nested(
        name: String?,
        factory: (DocSourceField) -> T,
        params: Params = Params()
    ): SubDocument.UnboundSubDocument<T> {
        return SubDocument.UnboundSubDocument(name, NestedType(), params, factory)
    }
    fun <T: SubDocument> nested(
        factory: (DocSourceField) -> T,
        params: Params = Params()
    ): SubDocument.UnboundSubDocument<T> {
        return nested(null, factory, params)
    }
}

typealias DocSourceField = BoundField<BaseDocSource>

/**
 * Represents Elasticsearch sub-document.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class SubDocument(
    private val field: DocSourceField
) : BaseDocument(), FieldOperations {
    fun getBoundField(): DocSourceField = field

    override fun getFieldName(): String = field.getFieldName()

    override fun getQualifiedFieldName(): String = field.getQualifiedFieldName()

    fun getFieldType(): FieldType<BaseDocSource> = field.getFieldType()

    fun getParent(): FieldSet = field.getParent()

    class UnboundSubDocument<T: SubDocument>(
        private val name: String?,
        private val type: ObjectType<BaseDocSource>,
        private val params: Params,
        private val subDocumentFactory: (DocSourceField) -> T,
    ) {
        operator fun provideDelegate(
            thisRef: BaseDocument, prop: KProperty<*>
        ): ReadOnlyProperty<BaseDocument, T> {
            val field = BoundField(
                name ?: prop.name,
                type,
                params,
                thisRef,
            )
            val subDocument = subDocumentFactory(field)
            if (subDocument.field != field) {
                throw IllegalStateException(
                    "Field [${field.getFieldName()}] has already been initialized as [${subDocument.getFieldName()}]"
                )
            }
            thisRef.addField(SubDocumentField(field, subDocument))

            return ReadOnlyProperty { _, _ -> subDocument }
        }
    }
}

internal class SubDocumentField(
    field: AnyField,
    val subDocument: SubDocument
) : WrapperField(field)

/**
 * Metadata fields:
 * https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-fields.html
 */
open class MetaFields : RootFieldSet() {
    val id by MetaField("_id", KeywordType)
    val type by MetaField("_type", KeywordType)
    val index by MetaField("_index", KeywordType)

    open val routing by RoutingField()

    open val fieldNames by FieldNamesField()
    val ignored by MetaField("_ignored", KeywordType)

    open val source by SourceField()
    open val size by SizeField()

    @Suppress("UnnecessaryAbstractClass")
    abstract class BaseMetaField<V, B: AnyField>(
        name: String, type: FieldType<V>, params: Params = Params(),
        private val boundFieldFactory: (String, Params, MetaFields) -> B
    ) : Field<V>(name, type, params) {
        operator fun provideDelegate(
            thisRef: MetaFields, prop: KProperty<*>
        ): ReadOnlyProperty<MetaFields, B> {
            val field = boundFieldFactory(
                name ?: prop.name,
                params,
                thisRef,
            )
            thisRef.addField(field)
            return ReadOnlyProperty { _, _ -> field}
        }
    }

    open class MetaField<V>(
        name: String, type: FieldType<V>, params: Params = Params()
    ) : BaseMetaField<V, BoundField<V>>(
        name, type, params,
        { n, p, m -> BoundField(n, type, p, m) }
    )

    class RoutingField(
        val required: Boolean? = null,
    ) : BaseMetaField<String, BoundRoutingField>(
        "_routing", KeywordType, Params("required" to required),
        MetaFields::BoundRoutingField
    )

    class BoundRoutingField(
        name: String, params: Params, parent: MetaFields
    ) : BoundField<String>(name, KeywordType, params, parent)

    class FieldNamesField(
        enabled: Boolean? = null,
    ) : BaseMetaField<String, BoundFieldNamesField>(
        "_field_names", KeywordType, Params("enabled" to enabled),
        MetaFields::BoundFieldNamesField
    )

    class BoundFieldNamesField(
        name: String, params: Params, parent: MetaFields
    ) : BoundField<String>(name, KeywordType, params, parent)

    // TODO: What type should the source field be?
    // TODO: Add constructor where `includes` & `excludes` arguments have type of `List<FieldOperations>`
    class SourceField(
        enabled: Boolean? = null,
        includes: List<String>? = null,
        excludes: List<String>? = null,
    ) : BaseMetaField<String, BoundSourceField>(
        "_source",
        KeywordType,
        Params("enabled" to enabled, "includes" to includes, "excludes" to excludes),
        MetaFields::BoundSourceField
    )

    class BoundSourceField(
        name: String, params: Params, parent: MetaFields
    ) : BoundField<String>(name, KeywordType, params, parent)

    class SizeField(
        enabled: Boolean? = null,
    ) : BaseMetaField<Long, BoundSizeField>(
        "_size", LongType, Params("enabled" to enabled),
        MetaFields::BoundSizeField
    )

    class BoundSizeField(
        name: String, params: Params, parent: MetaFields
    ) : BoundField<Long>(name, LongType, params, parent)
}

open class RuntimeFields : RootFieldSet() {
    val score by Field("_score", DoubleType, emptyMap(), ignored = true)
    val doc by Field("_doc", IntType, emptyMap(), ignored = true)
    val seqNo by Field("_seq_no", LongType, emptyMap(), ignored = true)

    fun <V> runtime(name: String, type: FieldType<V>, script: Script): RuntimeField<V> {
        return RuntimeField(name, type, script)
    }

    fun <V> runtime(type: FieldType<V>, script: Script): RuntimeField<V> {
        return RuntimeField(null, type, script)
    }

    class RuntimeField<V>(
        name: String?, type: FieldType<V>, script: Script
    ) : Field<V>(name, type, mapOf("script" to script))
}

@Suppress("UnnecessaryAbstractClass")
abstract class RootFieldSet : BaseDocument() {
    companion object : RootFieldSet()

    override fun getFieldName(): String = ""

    override fun getQualifiedFieldName(): String = ""
}

/**
 * Base class for describing a top level Elasticsearch document.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class Document : RootFieldSet() {
    open val meta = MetaFields()
    open val runtime = RuntimeFields()

    open val dynamic: Dynamic? = null
}

fun mergeDocuments(vararg docs: Document): Document {
    require(docs.isNotEmpty()) {
        "Nothing to merge, document list is empty"
    }

    if (docs.size == 1) {
        return docs[0]
    }

    val expectedMeta = docs[0].meta
    val expectedDocName = docs[0]::class.simpleName
    for (doc in docs.slice(1 until docs.size)) {
        checkMetaFields(doc::class.simpleName, doc.meta, expectedDocName, expectedMeta)
    }

    return object : Document() {
        override val meta = expectedMeta
        override val runtime = object : RuntimeFields() {
            init {
                mergeFieldSets(docs.map(Document::runtime)).forEach(::addField)
            }
        }

        init {
            mergeFieldSets(docs.toList()).forEach(::addField)
        }
    }
}

private fun mergeFieldSets(fieldSets: List<FieldSet>): List<AnyField> {
    val mergedFields = mutableListOf<AnyField>()
    val mergedFieldsByName = mutableMapOf<String, Int>()
    for (fields in fieldSets) {
        for (field in fields.getAllFields()) {
            val fieldName = field.getFieldName()
            val mergedFieldIx = mergedFieldsByName[fieldName]
            if (mergedFieldIx == null) {
                mergedFieldsByName[fieldName] = mergedFields.size
                mergedFields.add(field)
                continue
            }
            val expectedField = mergedFields[mergedFieldIx]

            // Merge sub fields
            // One document can have sub fields but another does not
            val firstSubFieldsField = field as? SubFieldsField
            val secondSubFieldsField = expectedField as? SubFieldsField
            if (firstSubFieldsField != null || secondSubFieldsField != null) {
                checkFieldsIdentical(field, expectedField)

                mergedFields[mergedFieldIx] = mergeSubFields(
                    secondSubFieldsField, firstSubFieldsField
                )

                continue
            }

            // Merge sub documents
            val subDocumentField = field as? SubDocumentField
            if (subDocumentField != null) {
                checkFieldsIdentical(field, expectedField)

                val expectedSubDocument = expectedField as? SubDocumentField
                requireNotNull(expectedSubDocument) {
                    "$fieldName are differ by sub document presence"
                }

                mergedFields[mergedFieldIx] = mergeSubDocuments(expectedField, subDocumentField)

                continue
            }

            checkFieldsIdentical(field, expectedField)
        }
    }

    return mergedFields
}

private fun mergeSubFields(first: SubFieldsField?, second: SubFieldsField?): SubFieldsField {
    val firstSubFields = first?.subFields
    val secondSubFields = second?.subFields

    val templateField = when {
        firstSubFields != null -> firstSubFields.getBoundField()
        secondSubFields != null -> secondSubFields.getBoundField()
        else -> error("Unreachable")
    }

    // It is your responsibility to pass correct values when (de)-serializing
    @Suppress("UNCHECKED_CAST")
    val mergedSubFields = object : SubFields<Any?>(templateField as BoundField<Any?>) {
        init {
            mergeFieldSets(listOfNotNull(secondSubFields, firstSubFields))
                .forEach(::addField)
        }

    }

    return SubFieldsField(
        templateField,
        mergedSubFields,
    )
}

private fun mergeSubDocuments(first: SubDocumentField, second: SubDocumentField): SubDocumentField {
    val firstSubDocument = first.subDocument
    val secondSubDocument = second.subDocument

    val mergedSubDocument = object : SubDocument(firstSubDocument.getBoundField()) {
        init {
            mergeFieldSets(listOf(secondSubDocument, firstSubDocument))
                .forEach(::addField)
        }

        override fun getFieldName(): String = firstSubDocument.getFieldName()
    }

    return SubDocumentField(
        first,
        mergedSubDocument,
    )
}

private fun checkMetaFields(
    docName: String?,
    metaFields: MetaFields,
    expectedDocName: String?,
    expectedMetaFields: MetaFields
) {
    val expectedFieldNames = expectedMetaFields.getAllFields().map(AnyField::getFieldName)
    for (expectedFieldName in expectedFieldNames) {
        requireNotNull(metaFields[expectedFieldName]) {
            "$expectedDocName has meta field $expectedFieldName but $docName does not"
        }
    }
    for (metaField in metaFields.getAllFields()) {
        val metaFieldName = metaField.getFieldName()
        val expectedMetaField = expectedMetaFields[metaFieldName]
        requireNotNull(expectedMetaField) {
            "$docName has meta field $metaFieldName but $expectedDocName does not"
        }
        checkFieldsIdentical(metaField, expectedMetaField)
    }
}

private fun checkFieldsIdentical(
    field: AnyField, expected: AnyField,
) {
    val fieldName = field.getFieldName()
    val expectedName = expected.getFieldName()
    require(fieldName == expectedName) {
        "Different field names: $fieldName != $expectedName"
    }

    val fieldType = field.getFieldType()
    val expectedType = expected.getFieldType()
    require(fieldType::class == expectedType::class) {
        "$fieldName has different field types: $fieldType != $expectedType"
    }

    val mappingParams = field.getMappingParams()
    val expectedParams = expected.getMappingParams()
    require(mappingParams == expectedParams) {
        "$fieldName has different field params: $mappingParams != $expectedParams"
    }
}
