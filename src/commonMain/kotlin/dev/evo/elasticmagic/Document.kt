package dev.evo.elasticmagic

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

open class BaseField : FieldOperations {
    private lateinit var name: String
    private lateinit var qualifiedName: String

    override fun getFieldName(): String = name

    override fun getQualifiedFieldName(): String {
        // Metadata fields are not bound
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
open class Field<T>(
    protected val name: String? = null,
    protected val type: Type<T>,
    protected val params: Params,
) : BaseField() {
    fun getFieldType(): Type<T> = type

    fun getMappingParams(): Params = params

    open fun getSubFields(): SubFields<*>? = null

    open fun getSubDocument(): SubDocument? = null

    fun <V: SubFields<T>> subFields(factory: () -> V): SubFields.SubFieldsProperty<T, V> {
        return SubFields.SubFieldsProperty(name, type, params, factory)
    }

    open operator fun provideDelegate(
        thisRef: FieldSet, prop: KProperty<*>
    ): ReadOnlyProperty<FieldSet, Field<T>> = FieldProperty(this, thisRef, prop)

    class FieldProperty<F: Field<T>, T>(
        private val field: F, fieldSet: FieldSet, prop: KProperty<*>
    ) : ReadOnlyProperty<FieldSet, F> {
        init {
            field._setFieldName(field.name ?: prop.name)
            fieldSet._fields.add(field)
        }

        override fun getValue(thisRef: FieldSet, property: KProperty<*>): F {
            return field
        }
    }
}

/**
 * Base class for any types which hold set of fields:
 * https://www.elastic.co/guide/en/elasticsearch/reference/7.10/mapping.html
 */
abstract class FieldSet {
    @Suppress("PropertyName")
    internal val _fields: ArrayList<Field<*>> = ArrayList()

    fun <T> field(
        name: String?,
        type: Type<T>,
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
        type: Type<T>,
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
        private val params: Params,
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

            thisRef._fields.add(FieldWrapper(subFields, type, params))

            return ReadOnlyProperty { _, _ -> subFields }
        }
    }

    internal class FieldWrapper<T>(
        private val subFields: SubFields<*>,
        type: Type<T>,
        params: Params,
    ) : Field<T>(subFields.getFieldName(), type, params) {
        override fun getFieldName(): String = subFields.getFieldName()
        override fun getQualifiedFieldName(): String = subFields.getQualifiedFieldName()

        override fun getSubFields(): SubFields<*> = subFields

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
        name: String?, factory: () -> V, params: Params = Params()
    ): SubDocument.SubDocumentProperty<V> {
        return SubDocument.SubDocumentProperty(name, ObjectType(), params, factory)
    }
    fun <V: SubDocument> `object`(
        factory: () -> V, params: Params = Params()
    ): SubDocument.SubDocumentProperty<V> {
        return `object`(null, factory, params)
    }
    fun <V: SubDocument> obj(
        name: String?, factory: () -> V, params: Params = Params(),
    ): SubDocument.SubDocumentProperty<V> {
        return `object`(name, factory, params)
    }
    fun <V: SubDocument> obj(
        factory: () -> V, params: Params = Params()
    ): SubDocument.SubDocumentProperty<V> {
        return `object`(factory, params)
    }

    fun <V: SubDocument> nested(
        name: String?, factory: () -> V, params: Params = Params()
    ): SubDocument.SubDocumentProperty<V> {
        return SubDocument.SubDocumentProperty(name, NestedType(), params, factory)
    }
    fun <V: SubDocument> nested(
        factory: () -> V, params: Params = Params()
    ): SubDocument.SubDocumentProperty<V> {
        return nested(null, factory, params)
    }
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
        private val type: Type<V>,
        private val params: Params,
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

            thisRef._fields.add(FieldWrapper(subDocument, type, params))

            return ReadOnlyProperty { _, _ -> subDocument }
        }
    }

    internal class FieldWrapper<T: SubDocument>(
        private val subDocument: SubDocument,
        type: Type<T>,
        params: Params,
    ) : Field<T>(subDocument.getFieldName(), type, params) {
        override fun getFieldName(): String = subDocument.getFieldName()

        override fun getQualifiedFieldName(): String = subDocument.getQualifiedFieldName()

        override fun getSubDocument(): SubDocument? = subDocument

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
    val id by MetaField("_id", KeywordType)
    val type by MetaField("_type", KeywordType)
    val index by MetaField("_index", KeywordType)

    open val routing by RoutingField()

    open val fieldNames by FieldNamesField()
    val ignored by MetaField("_ignored", KeywordType)

    open val source by SourceField()
    open val size by SizeField()

    // These are to support Elasticsearch 5.x
    val uid by MetaField("_uid", KeywordType)
    open val parent by ParentField()
    open val all by AllField()

    // TODO: Could we get rid of overriding provideDelegate operator?

    open class MetaField<T>(
        name: String, type: Type<T>, params: Params = Params()
    ) : Field<T>(
        name, type, params
    ) {
        override operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, MetaField<T>> = FieldProperty(this, thisRef, prop)
    }

    class RoutingField(
        required: Boolean? = null,
    ) : MetaField<String>("_routing", KeywordType, Params("required" to required)) {
        override operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, RoutingField> = FieldProperty(this, thisRef, prop)
    }

    class FieldNamesField(
        enabled: Boolean? = null,
    ) : MetaField<String>("_field_names", KeywordType, Params("enabled" to enabled)) {
        override operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, FieldNamesField> = FieldProperty(this, thisRef, prop)
    }

    // TODO: What type should the source field be?
    class SourceField(
        enabled: Boolean? = null,
        includes: List<String>? = null,
        excludes: List<String>? = null,
    ) : MetaField<String>(
        "_source",
        KeywordType,
        Params("enabled" to enabled, "includes" to includes, "excludes" to excludes)
    ) {
        override operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, SourceField> = FieldProperty(this, thisRef, prop)
    }

    class SizeField(
        enabled: Boolean? = null,
    ) : MetaField<Long>("_size", LongType, Params("enabled" to enabled)) {
        override operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, SizeField> = FieldProperty(this, thisRef, prop)
    }

    class ParentField(
        type: String? = null,
    ) : MetaField<String>("_parent", KeywordType, Params("type" to type)) {
        override operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, ParentField> = FieldProperty(this, thisRef, prop)
    }

    class AllField(
        enabled: Boolean? = null,
    ) : MetaField<String>("_all", KeywordType, Params("enabled" to enabled)) {
        override operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, AllField> = FieldProperty(this, thisRef, prop)
    }
}

/**
 * Base class for describing a top level Elasticsearch document.
 */
abstract class Document : BaseDocument() {
    open val meta = MetaFields()

    open val docType: String = "_doc"
}
