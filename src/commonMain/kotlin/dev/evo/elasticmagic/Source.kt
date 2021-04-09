package dev.evo.elasticmagic

// import dev.evo.elasticmagic.compile.DocSourceCompiler
import dev.evo.elasticmagic.serde.Serializer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

typealias RawSource = Map<*, *>

fun emptySource(): Map<Nothing, Nothing> = emptyMap()

abstract class BaseSource {
    abstract fun getSource(): Map<String, Any?>

    abstract fun setSource(source: RawSource)
}

open class Source : BaseSource() {
    private val fieldValues: MutableList<FieldValue<*>> = mutableListOf()
    private val fieldProperties: MutableMap<String, FieldValueProperty<*>> = mutableMapOf()

    private fun bindProperty(fieldProperty: FieldValueProperty<*>) {
        val fieldValue = fieldProperty.fieldValue
        fieldValues.add(fieldValue)
        fieldProperties[fieldValue.name] = fieldProperty
    }

    override fun getSource(): Map<String, Any?> {
        val source = mutableMapOf<String, Any?>()
        for (fieldValue in fieldValues) {
            if (fieldValue.isRequired && !fieldValue.isInitialized) {
                throw IllegalStateException("Field ${fieldValue.name} is required")
            }
            if (fieldValue.isInitialized) {
                source[fieldValue.name] = fieldValue.serialize()
            }
        }
        return source
    }

    override fun setSource(source: RawSource) {
        clearSource()
        for ((fieldName, fieldValue) in source) {
            setField(fieldName as String, fieldValue)
        }
        for (fieldValue in fieldValues) {
            if (fieldValue.isRequired && !fieldValue.isInitialized) {
                throw IllegalArgumentException("Field ${fieldValue.name} is required")
            }
        }
    }

    fun clearSource() {
        fieldValues.map(FieldValue<*>::clear)
    }

    // override fun serialize(ctx: Serializer.ObjectCtx, compiler: DocSourceCompiler) {
    //     for (fieldValue in fieldValues) {
    //         if (fieldValue.isRequired && !fieldValue.isInitialized) {
    //             throw IllegalStateException("Field ${fieldValue.name} is required")
    //         }
    //         if (fieldValue.isInitialized) {
    //             ctx.field(fieldValue.name, fieldValue.serialize())
    //         }
    //     }
    // }

    fun getField(name: String): Any? {
        return fieldProperties[name]?.fieldValue?.value
    }

    fun setField(name: String, value: Any?) {
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

    operator fun <V> Field<*, V>.provideDelegate(
        thisRef: Source, property: KProperty<*>
    ): ReadWriteProperty<Source, V?> {
        return OptionalValueProperty(
            getFieldName(), getFieldType()
        )
            .also {
                thisRef.bindProperty(it)
            }
    }

    fun <V> Field<*, V>.required(): RequiredListableValueDelegate<V> {
        return RequiredListableValueDelegate(
            getFieldName(), getFieldType()
        )
    }

    fun <V> Field<*, V>.list(): OptionalValueDelegate<List<V?>> {
        return OptionalValueDelegate(
            getFieldName(), OptionalListType(getFieldType())
        )
    }

    operator fun <V> SubFields<V>.provideDelegate(
        thisRef: Source, property: KProperty<*>
    ): ReadWriteProperty<Source, V?> {
        return OptionalValueProperty(
            getFieldName(), getFieldType()
        )
            .also {
                thisRef.bindProperty(it)
            }
    }

    fun <V: BaseSource> SubDocument.source(sourceFactory: () -> V): OptionalListableValueDelegate<V> {
        val fieldType = getFieldType()
        return OptionalListableValueDelegate(
            getFieldName(), SourceType(fieldType, sourceFactory)
        )
    }

    class FieldValue<V>(
        val name: String,
        val type: FieldType<*, V>,
        val isRequired: Boolean,
    ) {
        var isInitialized: Boolean = false
            private set

        private var _value: V? = null
        var value: V?
            get() = _value
            set(value) {
                isInitialized = true
                _value = value
            }

        fun clear() {
            isInitialized = false
            _value = null
        }

        fun serialize(): Any? {
            return value?.let(type::serialize)
        }
    } 

    abstract class FieldValueProperty<V>(
        val fieldValue: FieldValue<V>,
        protected val fieldType: FieldType<*, V>,
    ) {
        abstract fun set(value: Any?)
    }

    open class OptionalValueDelegate<V>(
        protected val fieldName: String,
        protected val fieldType: FieldType<*, V>,
    ) {
        operator fun provideDelegate(
            thisRef: Source, property: KProperty<*>
        ): ReadWriteProperty<Source, V?> {
            return OptionalValueProperty(fieldName, fieldType)
                .also { thisRef.bindProperty(it) }
        }

        open fun required(): RequiredValueDelegate<V> {
            return RequiredValueDelegate(fieldName, fieldType)
        }
    }

    class OptionalListableValueDelegate<V>(
        fieldName: String,
        fieldType: FieldType<*, V>,
    ) : OptionalValueDelegate<V>(fieldName, fieldType) {
        override fun required(): RequiredListableValueDelegate<V> {
            return RequiredListableValueDelegate(fieldName, fieldType)
        }

        fun list(): OptionalValueDelegate<List<V?>> {
            return OptionalValueDelegate(
                fieldName, OptionalListType(fieldType)
            )
        }
    }

    protected class OptionalValueProperty<V>(
        fieldName: String,
        fieldType: FieldType<*, V>,
    ) :
        FieldValueProperty<V>(FieldValue(fieldName, fieldType, false), fieldType),
        ReadWriteProperty<Source, V?>
    {

        override fun set(value: Any?) {
            fieldValue.value = if (value != null) {
                fieldType.deserialize(value)
            } else {
                null
            }
        }

        override fun getValue(thisRef: Source, property: KProperty<*>): V? {
            return fieldValue.value
        }

        override fun setValue(thisRef: Source, property: KProperty<*>, value: V?) {
            fieldValue.value = value
        }
    }

    open class RequiredValueDelegate<V>(
        protected val fieldName: String,
        protected val fieldType: FieldType<*, V>,
    ) {
        operator fun provideDelegate(
            thisRef: Source, property: KProperty<*>
        ): ReadWriteProperty<Source, V> {
            return RequiredValueProperty(
                fieldName, fieldType
            )
                .also { thisRef.bindProperty(it) }
        }

    }

    class RequiredListableValueDelegate<V>(
        fieldName: String,
        fieldType: FieldType<*, V>,
    ) : RequiredValueDelegate<V>(fieldName, fieldType) {
        fun list(): OptionalValueDelegate<List<V>> {
            return OptionalValueDelegate(
                fieldName, RequiredListType(fieldType)
            )
        }
    }

    protected class RequiredValueProperty<V>(
        fieldName: String,
        fieldType: FieldType<*, V>,
    ) :
        FieldValueProperty<V>(FieldValue(fieldName, fieldType, true), fieldType),
        ReadWriteProperty<Source, V>
    {

        override fun set(value: Any?) {
            fieldValue.value = if (value != null) {
                fieldType.deserialize(value)
            } else {
                throw IllegalArgumentException("Value cannot be null for required field")
            }
        }

        override fun getValue(thisRef: Source, property: KProperty<*>): V {
            return fieldValue.value ?: error("Field is not initialized")
        }

        override fun setValue(thisRef: Source, property: KProperty<*>, value: V) {
            fieldValue.value = value
        }
    }
}

class StdSource : BaseSource() {
    private var rawSource =  mutableMapOf<String, Any?>()

    override fun getSource(): Map<String, Any?> {
        return rawSource.toMap()
    }

    override fun setSource(source: RawSource) {
        clearSource()
        for ((fieldName, fieldValue) in source) {
            setField(fieldName as String, fieldValue)
        }
    }

    fun clearSource() {
        rawSource.clear()
    }

    fun setField(name: String, value: Any?) {
        rawSource[name] = value
    }

    fun getField(name: String): Any? {
        return rawSource[name]
    }

    override fun toString(): String {
        return "StdSource($rawSource)"
    }
}
