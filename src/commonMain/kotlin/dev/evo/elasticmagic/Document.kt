package dev.evo.elasticmagic

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

open class BaseField : FieldOperations {
    private lateinit var name: String
    private lateinit var qualifiedName: String

    override fun getFieldName(): String = name

    override fun getQualifiedFieldName(): String {
        return if (::qualifiedName.isInitialized) {
            qualifiedName
        } else {
            name
        }
    }

    @Suppress("FunctionName")
    internal open fun _setFieldName(fieldName: String) {
        if (::name.isInitialized) {
            throw IllegalStateException(
                "Field [$fieldName] has already been initialized as [$name]")
        }
        name = fieldName
    }

    @Suppress("FunctionName")
    internal open fun _bindToParent(parent: Named) {
        if (::qualifiedName.isInitialized) {
            throw IllegalStateException(
                "Field [${getFieldName()}] has already been bound as [${getQualifiedFieldName()}]"
            )
        }
        val parentQualifiedFieldName = parent.getQualifiedFieldName()
        qualifiedName = if (parentQualifiedFieldName.isNotEmpty()) {
            "${parentQualifiedFieldName}.${getFieldName()}"
        } else {
            getFieldName()
        }
    }
}

/**
 * Represents field in an Elasticsearch document.
 */
class Field<T>(
    private val name: String? = null,
    private val type: Type<T>,
) : BaseField() {

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
abstract class FieldSet {
    @Suppress("PropertyName")
    internal val _fields: ArrayList<BaseField> = ArrayList()

    fun <T> field(name: String?, type: Type<T>) = Field(name, type)
    fun <T> field(type: Type<T>) = field(null, type)
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
abstract class SubFields<T> : FieldSet(), FieldOperations {
    private val field = BaseField()
    private lateinit var type: Type<T>

    fun getFieldType(): Type<T> = type

    override fun getFieldName(): String = field.getFieldName()

    override fun getQualifiedFieldName(): String = field.getQualifiedFieldName()

    private fun setFieldName(fieldName: String) {
        field._setFieldName(fieldName)
    }

    private fun bindToParent(parent: Named) {
        field._bindToParent(parent)
        for (subField in _fields) {
            subField._bindToParent(this)
        }
    }

    class SubFieldsProperty<T, V: SubFields<T>>(
        private val name: String?,
        private val type: Type<T>,
        private val subFieldsFactory: () -> V,
    ) {
        operator fun provideDelegate(
            thisRef: BaseDocument, prop: KProperty<*>
        ): ReadOnlyProperty<BaseDocument, V> {
            val fieldName = name ?: prop.name
            val subFields = subFieldsFactory()
            subFields.type = type
            subFields.setFieldName(fieldName)
            if (thisRef is Document) {
                subFields.bindToParent(object : Named {
                    override fun getFieldName(): String = ""
                    override fun getQualifiedFieldName(): String = ""
                })
            }

            thisRef._fields.add(FieldWrapper(subFields))

            return ReadOnlyProperty { _, _ -> subFields }
        }
    }

    private class FieldWrapper(private val subFields: SubFields<*>) : BaseField() {
        override fun getFieldName(): String = subFields.getFieldName()
        override fun getQualifiedFieldName(): String = subFields.getQualifiedFieldName()
        override fun _setFieldName(fieldName: String) {
            subFields.setFieldName(fieldName)
        }

        override fun _bindToParent(parent: Named) {
            subFields.bindToParent(parent)
        }
    }
}

abstract class BaseDocument : FieldSet() {
    fun <V: SubDocument> `object`(
        name: String?, factory: () -> V
    ): SubDocument.SubDocumentProperty<V> {
        return SubDocument.SubDocumentProperty(name, factory)
    }
    fun <V: SubDocument> `object`(factory: () -> V) = `object`(null, factory)
    fun <V: SubDocument> obj(name: String?, factory: () -> V) = `object`(name, factory)
    fun <V: SubDocument> obj(factory: () -> V) = `object`(null, factory)

    // TODO: make NestedDocument
    fun <V: SubDocument> nested(name: String?, factory: () -> V) = `object`(name, factory)
    fun <V: SubDocument> nested(factory: () -> V) = `object`(null, factory)
}

/**
 * Represents Elasticsearch sub-document.
 */
abstract class SubDocument : BaseDocument(), FieldOperations {
    private val field = BaseField()

    override fun getFieldName(): String = field.getFieldName()

    override fun getQualifiedFieldName(): String = field.getQualifiedFieldName()

    private fun setFieldName(fieldName: String) {
        field._setFieldName(fieldName)
    }

    private fun bindToParent(parent: Named) {
        field._bindToParent(parent)
        for (subField in _fields) {
            subField._bindToParent(this)
        }
    }

    class SubDocumentProperty<V: SubDocument>(
        private val name: String?,
        private val subDocumentFactory: () -> V,
    ) {
        operator fun provideDelegate(
            thisRef: BaseDocument, prop: KProperty<*>
        ): ReadOnlyProperty<BaseDocument, V> {
            val fieldName = name ?: prop.name
            val subDocument = subDocumentFactory()
            subDocument.setFieldName(fieldName)
            if (thisRef is Document) {
                subDocument.bindToParent(object : Named {
                    override fun getFieldName(): String = ""
                    override fun getQualifiedFieldName(): String = ""
                })
            }

            thisRef._fields.add(FieldWrapper(subDocument))

            return ReadOnlyProperty { _, _ -> subDocument }
        }
    }

    private class FieldWrapper(private val subDocument: SubDocument) : BaseField() {
        override fun getFieldName(): String = subDocument.getFieldName()
        override fun getQualifiedFieldName(): String = subDocument.getQualifiedFieldName()
        override fun _setFieldName(fieldName: String) {
            subDocument.setFieldName(fieldName)
        }

        override fun _bindToParent(parent: Named) {
            subDocument.bindToParent(parent)
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
abstract class Document : BaseDocument() {
    open val meta = MetaFields()
}
