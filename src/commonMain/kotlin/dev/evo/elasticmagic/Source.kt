package dev.evo.elasticmagic

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

typealias RawSource = Map<*, *>

fun emptySource(): Map<Nothing, Nothing> = emptyMap()

abstract class BaseSource() {
    abstract fun getField(name: String): Any?

    abstract fun setField(name: String, value: Any?)

    open fun setSource(source: RawSource) {
        for ((fieldName, fieldValue) in source) {
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

    override fun setSource(source: RawSource) {
        super.setSource(source)
        for (fieldValue in fieldValues) {
            if (fieldValue.isRequired && !fieldValue.isInitialized) {
                throw IllegalArgumentException("Field ${fieldValue.name} is required")
            }
        }
    }

    override fun getField(name: String): Any? {
        return fieldProperties[name]?.fieldValue?.value
    }

    override fun setField(name: String, value: Any?) {
        val fieldProperty = fieldProperties[name]
            ?: throw IllegalArgumentException("Unknown field name: $name")
        fieldProperty.set(value)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other !is Source) {
            return false
        }
        if (other::class != this::class) {
            return false
        }
        for ((fieldValue, otherFieldValue) in fieldValues.zip(other.fieldValues)) {
            if (fieldValue.name != otherFieldValue.name) {
                return false
            }
            if (fieldValue.isInitialized != otherFieldValue.isInitialized) {
                return false
            }
            if (fieldValue.value != otherFieldValue.value) {
                return false
            }
        }
        return true
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
        return OptionalValueProperty(
            getFieldName(),
            getFieldType()::deserialize
        )
            .also {
                thisRef.bindProperty(it)
            }
    }

    fun <T, V> Field<T, V>.required(): RequiredListableValueDelegate<V> {
        return RequiredListableValueDelegate(
            getFieldName(),
            getFieldType()::deserialize
        )
    }

    fun <T, V> Field<T, V>.list(): OptionalValueDelegate<List<V?>> {
        return OptionalValueDelegate(getFieldName()) { v ->
            deserializeListOfOptional(getFieldType()::deserialize, v)
        }
    }

    operator fun <V> SubFields<V>.provideDelegate(
        thisRef: Source, property: KProperty<*>
    ): ReadWriteProperty<Source, V?> {
        return OptionalValueProperty(
            getFieldName(),
            getFieldType()::deserialize
        )
            .also {
                thisRef.bindProperty(it)
            }
    }

    fun <V: BaseSource> SubDocument.source(sourceFactory: () -> V): OptionalListableValueDelegate<V> {
        val fieldType = getFieldType()
        return OptionalListableValueDelegate(
            getFieldName()
        ) { v ->
            fieldType.deserialize(v, sourceFactory) as V
        }
    }

    class FieldValue<T>(
        val name: String,
        val isRequired: Boolean,
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

    open class OptionalValueDelegate<T>(
        protected val fieldName: String,
        protected val deserialize: (Any) -> T,
    ) {
        operator fun provideDelegate(
            thisRef: Source, property: KProperty<*>
        ): ReadWriteProperty<Source, T?> {
            return OptionalValueProperty(
                fieldName, deserialize
            )
                .also { thisRef.bindProperty(it) }
        }

        open fun required(): RequiredValueDelegate<T> {
            return RequiredValueDelegate(fieldName, deserialize)
        }
    }

    class OptionalListableValueDelegate<T>(
        fieldName: String,
        deserialize: (Any) -> T,
    ) : OptionalValueDelegate<T>(fieldName, deserialize) {
        override fun required(): RequiredListableValueDelegate<T> {
            return RequiredListableValueDelegate(fieldName, deserialize)
        }

        fun list(): OptionalValueDelegate<List<T?>> {
            return OptionalValueDelegate(fieldName) { v ->
                deserializeListOfOptional(deserialize, v)
            }
        }
    }

    protected class OptionalValueProperty<T>(
        fieldName: String,
        deserialize: (Any) -> T,
    ) :
        FieldValueProperty<T>(FieldValue(fieldName, false), deserialize),
        ReadWriteProperty<Source, T?>
    {

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
            fieldValue.value = value
        }
    }

    open class RequiredValueDelegate<T>(
        protected val fieldName: String,
        protected val deserialize: (Any) -> T,
    ) {
        operator fun provideDelegate(
            thisRef: Source, property: KProperty<*>
        ): ReadWriteProperty<Source, T> {
            return RequiredValueProperty(
                fieldName, deserialize
            )
                .also { thisRef.bindProperty(it) }
        }

    }

    class RequiredListableValueDelegate<T>(
        fieldName: String,
        deserialize: (Any) -> T,
    ) : RequiredValueDelegate<T>(fieldName, deserialize) {
        fun list(): OptionalValueDelegate<List<T>> {
            return OptionalValueDelegate(fieldName) { v ->
                deserializeListOfRequired(deserialize, v)
            }
        }
    }

    protected class RequiredValueProperty<T>(
        fieldName: String,
        deserialize: (Any) -> T,
    ) :
        FieldValueProperty<T>(FieldValue(fieldName, true), deserialize),
        ReadWriteProperty<Source, T>
    {

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
            fieldValue.value = value
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
