package dev.evo.elasticmagic

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class BaseSource {
    abstract fun getField(name: String): Any?

    abstract fun setField(name: String, value: Any?)

    fun setSource(data: Map<*, *>) {
        for ((fieldName, fieldValue) in data) {
            setField(fieldName as String, fieldValue)
        }
    }
}

open class Source : BaseSource() {
    private val fieldValues: MutableList<FieldValue<*>> = mutableListOf()
    private val fieldProperties: MutableMap<String, FieldValueProperty<*>> = mutableMapOf()

    private fun bindProperty(fieldProperty: FieldValueProperty<*>) {
        val fieldValue = fieldProperty.fieldValue
        fieldValues.add(fieldValue)
        fieldProperties[fieldValue.name] = fieldProperty
    }

    override fun getField(name: String): Any? {
        return fieldProperties[name]?.fieldValue?.value
    }

    override fun setField(name: String, value: Any?) {
        val fieldProperty = fieldProperties[name] ?: return
        fieldProperty.set(value)
    }

    override fun toString(): String {
        return fieldValues
            .filter(FieldValue<*>::isInitialized)
            .joinToString(
                separator = ", ", prefix = "${this::class.simpleName}(", postfix = ")"
            ) { fieldValue ->
                "${fieldValue.name}=${fieldValue.value}"
            }
    }

    operator fun <T, V> Field<T, V>.provideDelegate(
        thisRef: Source, property: KProperty<*>
    ): ReadWriteProperty<Source, V?> {
        return OptionalFieldValueProperty(
            FieldValue(getFieldName()),
            getFieldType()::deserialize
        )
            .also {
                thisRef.bindProperty(it)
            }
    }

    fun <T, V> Field<T, V>.required(): RequiredFieldValueDelegate<V> {
        return RequiredFieldValueDelegate(
            getFieldName(),
            getFieldType()::deserialize
        )
    }

    fun <T, V> Field<T, V>.list(): ListFieldValueDelegate<V> {
        return ListFieldValueDelegate(getFieldName()) { v ->
            deserializeListOptional(getFieldType()::deserialize, v)
        }
    }

    operator fun <V> SubFields<V>.provideDelegate(
        thisRef: Source, property: KProperty<*>
    ): ReadWriteProperty<Source, V?> {
        return OptionalFieldValueProperty(
            FieldValue(getFieldName()),
            getFieldType()::deserialize
        )
            .also {
                thisRef.bindProperty(it)
            }
    }

    fun <V: BaseSource> SubDocument.source(sourceFactory: () -> V): OptionalFieldValueDelegate<V> {
        val fieldType = getFieldType()
        return OptionalFieldValueDelegate(
            getFieldName()
        ) { v ->
            fieldType.deserialize(v, sourceFactory) as V
        }
    }

    class FieldValue<T>(
        val name: String,
    ) {
        var isInitialized: Boolean = false
            private set
        
        var value: T? = null
            set(value) {
                isInitialized = true
                field = value
            }
    } 

    abstract class FieldValueProperty<T>(
        val fieldValue: FieldValue<T>,
        protected val deserialize: (Any) -> T,
    ) {
        abstract fun set(value: Any?)
    }

    class OptionalFieldValueDelegate<T>(
        private val fieldName: String,
        private val deserialize: (Any) -> T,
    ) {
        operator fun provideDelegate(
            thisRef: Source, property: KProperty<*>
        ): ReadWriteProperty<Source, T?> {
            return OptionalFieldValueProperty(
                FieldValue(fieldName), deserialize
            )
                .also { thisRef.bindProperty(it) }
        }

        fun required(): RequiredFieldValueDelegate<T> {
            return RequiredFieldValueDelegate(fieldName, deserialize)
        }

        fun list(): ListFieldValueDelegate<T> {
            return ListFieldValueDelegate(fieldName) { v ->
                deserializeListOptional(deserialize, v)
            }
        }
    }

    protected class OptionalFieldValueProperty<T>(
        fieldValue: FieldValue<T>,
        deserialize: (Any) -> T,
    ) : FieldValueProperty<T>(fieldValue, deserialize), ReadWriteProperty<Source, T?> {

        override fun set(value: Any?) {
            fieldValue.value = if (value != null) {
                deserialize(value)
            } else {
                null
            }
        }

        override fun getValue(thisRef: Source, property: KProperty<*>): T? {
            return fieldValue.value
        }

        override fun setValue(thisRef: Source, property: KProperty<*>, value: T?) {
            set(value)
        }
    }

    class RequiredFieldValueDelegate<T>(
        private val fieldName: String,
        private val deserialize: (Any) -> T,
    ) {
        operator fun provideDelegate(
            thisRef: Source, property: KProperty<*>
        ): ReadWriteProperty<Source, T> {
            return RequiredFieldValueProperty(
                FieldValue(fieldName), deserialize
            )
                .also { thisRef.bindProperty(it) }
        }

        fun list(): RequiredListFieldValueDelegate<T> {
            return RequiredListFieldValueDelegate(fieldName) { v ->
                deserializeListRequired(deserialize, v)
            }
        }
    }

    protected class RequiredFieldValueProperty<T>(
        fieldValue: FieldValue<T>,
        deserialize: (Any) -> T,
    ) : FieldValueProperty<T>(fieldValue, deserialize), ReadWriteProperty<Source, T> {

        override fun set(value: Any?) {
            fieldValue.value = if (value != null) {
                deserialize(value)
            } else {
                throw IllegalArgumentException("Value cannot be null for required field")
            }
        }

        override fun getValue(thisRef: Source, property: KProperty<*>): T {
            return fieldValue.value ?: error("Field is not initialized")
        }

        override fun setValue(thisRef: Source, property: KProperty<*>, value: T) {
            set(value)
        }
    }

    class ListFieldValueDelegate<T>(
        private val fieldName: String,
        private val deserialize: (Any) -> List<T?>,
    ) {
        operator fun provideDelegate(
            thisRef: Source, property: KProperty<*>
        ): ReadWriteProperty<Source, List<T?>> {
            return ListFieldValueProperty(
                FieldValue(fieldName), deserialize
            )
                .also { thisRef.bindProperty(it) }
        }
    }

    protected class ListFieldValueProperty<T>(
        fieldValue: FieldValue<List<T?>>,
        deserialize: (Any) -> List<T?>,
    ) : FieldValueProperty<List<T?>>(fieldValue, deserialize), ReadWriteProperty<Source, List<T?>> {
        override fun set(value: Any?) {
            fieldValue.value = if (value != null) {
                deserialize(value)
            } else {
                throw IllegalArgumentException("Value cannot be null for list field")
            }
        }

        override fun getValue(thisRef: Source, property: KProperty<*>): List<T?> {
            return fieldValue.value ?: emptyList()
        }

        override fun setValue(thisRef: Source, property: KProperty<*>, value: List<T?>) {
            set(value)
        }
    }

    class RequiredListFieldValueDelegate<T>(
        private val fieldName: String,
        private val deserialize: (Any) -> List<T>,
    ) {
        operator fun provideDelegate(
            thisRef: Source, property: KProperty<*>
        ): ReadWriteProperty<Source, List<T>> {
            return RequiredListFieldValueProperty(
                FieldValue(fieldName), deserialize
            )
                .also { thisRef.bindProperty(it) }
        }
    }

    protected class RequiredListFieldValueProperty<T>(
        fieldValue: FieldValue<List<T>>,
        deserialize: (Any) -> List<T>,
    ) : FieldValueProperty<List<T>>(fieldValue, deserialize), ReadWriteProperty<Source, List<T>> {
        override fun set(value: Any?) {
            fieldValue.value = if (value != null) {
                deserialize(value)
            } else {
                throw IllegalArgumentException("Value cannot be null for list field")
            }
        }

        override fun getValue(thisRef: Source, property: KProperty<*>): List<T> {
            return fieldValue.value ?: emptyList()
        }

        override fun setValue(thisRef: Source, property: KProperty<*>, value: List<T>) {
            set(value)
        }
    }
}

class StdSource : BaseSource() {
    private var data =  mutableMapOf<String, Any?>()

    override fun setField(name: String, value: Any?) {
        data[name] = value
    }

    override fun getField(name: String): Any? {
        return data[name]
    }

    override fun toString(): String {
        return "StdSource(source = $data)"
    }
}


object MyDoc : Document() {
    class MySubDoc : SubDocument() {
        val rank by float()
    }

    class NameFields<T> : SubFields<T>() {
        val sort by keyword()
    }

    val name by text().subFields(::NameFields)
    val status by int()
    // FIXME:
    // val state by status
    val categories by int()
    val tags by int()
    val subDoc by obj(::MySubDoc)
}

class MySource : Source() {
    class MySubSource : Source() {
        private val subDoc = MyDoc.subDoc

        val rank by subDoc.rank
    }

    val name by MyDoc.name
    val status by MyDoc.status.required()
    val categories by MyDoc.categories.list()
    val tags by MyDoc.tags.required().list()
    val subDoc by MyDoc.subDoc.source(::MySubSource).required().list()
}

// fun test() {
//     class MySubDoc : SubDocument() {
//         val rank by float()
//     }
//
//     class NameFields<T> : SubFields<T>() {
//         val sort by keyword()
//     }
//
//     val myDoc = object : Document() {
//         val name = text().subFields(::NameFields)
//         // FIXME:
//         val keywords by name
//         val status by int()
//         val categories by int()
//         val subDoc by obj(::MySubDoc)
//     }
//
// }
