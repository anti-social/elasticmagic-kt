package dev.evo.elasticmagic

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Represents field in an Elasticsearch document.
 */
class Field<T>(
    private val name: String? = null,
    private val type: Type<T>,
) : FieldOperations() {

    fun getFieldType(): Type<T> = type

    fun <V: SubFields<T>> subFields(factory: () -> V): SubFields.SubFieldsProperty<T, V> {
        return SubFields.SubFieldsProperty(name, type, factory)
    }

    operator fun provideDelegate(
        thisRef: FieldSet, prop: KProperty<*>
    ): ReadOnlyProperty<FieldSet, Field<T>> {
        _setFieldName(name ?: prop.name)

        thisRef._fields.add(this)

        return ReadOnlyProperty { _, _ -> this }
    }
}

/**
 * Base class for any types which hold set of fields:
 * https://www.elastic.co/guide/en/elasticsearch/reference/7.10/mapping.html
 */
abstract class FieldSet : FieldOperations() {
    @Suppress("PropertyName")
    internal val _fields = ArrayList<FieldOperations>()

    override fun _bindToParent(parent: FieldOperations) {
        super._bindToParent(parent)

        for (field in _fields) {
            field._bindToParent(this)
        }
    }

    fun <T> field(name: String? = null, type: Type<T>) = Field(name, type)
    fun boolean(name: String? = null) = field(name, BooleanType)
    fun int(name: String? = null) = field(name, IntType)
    fun long(name: String? = null) = field(name, LongType)
    fun float(name: String? = null) = field(name, FloatType)
    fun double(name: String? = null) = field(name, DoubleType)
    fun keyword(name: String? = null) = field(name, KeywordType)
    fun text(name: String? = null) = field(name, TextType)
}

/**
 * Represents Elasticsearch multi-fields:
 * https://www.elastic.co/guide/en/elasticsearch/reference/7.10/multi-fields.html
 */
abstract class SubFields<T> : FieldSet() {
    private lateinit var type: Type<T>

    fun getFieldType(): Type<T> = type

    class SubFieldsProperty<T, V: SubFields<T>>(
        private val name: String?,
        private val type: Type<T>,
        private val subFieldsFactory: () -> V,
    ) {
        operator fun provideDelegate(
            thisRef: SubDocument, prop: KProperty<*>
        ): ReadOnlyProperty<SubDocument, V> {
            val fieldName = name ?: prop.name
            val subFields = subFieldsFactory().apply {
                type = this@SubFieldsProperty.type
                _setFieldName(fieldName)
                if (thisRef is Document) {
                    _bindToParent(thisRef)
                }
            }

            thisRef._fields.add(subFields)

            return ReadOnlyProperty { _, _ -> subFields }
        }
    }
}

/**
 * Represents Elasticsearch sub-document.
 */
abstract class SubDocument : FieldSet() {
    fun <V: SubDocument> `object`(name: String?, factory: () -> V): SubDocumentProperty<V> {
        return SubDocumentProperty(name, factory)
    }
    fun <V: SubDocument> `object`(factory: () -> V): SubDocumentProperty<V> {
        return `object`(null, factory)
    }
    fun <V: SubDocument> obj(name: String?, factory: () -> V) = `object`(name, factory)
    fun <V: SubDocument> obj(factory: () -> V) = `object`(null, factory)

    // TODO: make NestedDocument
    fun <V: SubDocument> nested(name: String?, factory: () -> V) = `object`(name, factory)
    fun <V: SubDocument> nested(factory: () -> V) = `object`(null, factory)

    class SubDocumentProperty<V: SubDocument>(
        private val name: String?,
        private val subDocumentFactory: () -> V,
    ) {
        operator fun provideDelegate(
            thisRef: SubDocument, prop: KProperty<*>
        ): ReadOnlyProperty<SubDocument, V> {
            val fieldName = name ?: prop.name
            val subDocument = subDocumentFactory()
            if (subDocument is Document) {
                throw IllegalStateException(
                    "Document instance cannot be a sub document: ${this::class}"
                )
            }
            subDocument._setFieldName(fieldName)
            if (thisRef is Document) {
                subDocument._bindToParent(thisRef)
            }

            thisRef._fields.add(subDocument)

            return ReadOnlyProperty { _, _ -> subDocument }
        }
    }
}

/**
 * Metadata fields:
 * https://www.elastic.co/guide/en/elasticsearch/reference/7.10/mapping-fields.html
 */
open class MetaFields : FieldSet() {
    val id by keyword("_id")
    val type by keyword("_type")
    val index by keyword("_index")

    val routing by keyword("_routing")

    val fieldNames by keyword("_field_names")
    val ignored by keyword("_ignored")

    val source by keyword("_source")
    val size by long("_size")

    // These are deprecated but retained to support Elasticsearch 5.x
    val uid by keyword("_uid")
    val parent by keyword("_parent")
}

/**
 * Base class for describing a top level Elasticsearch document.
 */
abstract class Document : SubDocument() {
    open val meta = MetaFields()
}
