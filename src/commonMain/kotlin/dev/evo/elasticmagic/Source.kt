package dev.evo.elasticmagic

import kotlin.properties.ReadOnlyProperty
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
    private val fieldProperties: MutableList<FieldValueProperty<*>> = mutableListOf()
    private val fieldValues: MutableMap<String, FieldValueProperty<*>> = mutableMapOf()

    private fun bindProperty(fieldName: String, fieldProperty: FieldValueProperty<*>) {
        fieldProperties.add(fieldProperty)
        fieldValues[fieldName] = fieldProperty
    }

    override fun getField(name: String): Any? {
        return fieldValues[name]?.value
    }

    override fun setField(name: String, value: Any?) {
        val fieldValue = fieldValues[name] ?: return
        fieldValue.set(value)
    }

    override fun toString(): String {
        return fieldProperties.joinToString(
            separator = ", ", prefix = "${this::class.simpleName}(", postfix = ")"
        ) { fieldProperty ->
            "${fieldProperty.name}=${fieldProperty.value}"
        }
    }

    protected abstract class FieldValueProperty<T>(
        val name: String,
        var value: T? = null,
    ) : ReadWriteProperty<Source, T> {
        abstract fun set(value: Any?)
    }

    operator fun <T> Field<T>.provideDelegate(
        thisRef: Source, property: KProperty<*>
    ): ReadWriteProperty<Source, T?> {
        val fieldName = getFieldName()
        val fieldType = getFieldType()
        val fieldProperty = object : FieldValueProperty<T?>(property.name) {
            override fun set(value: Any?) {
                this.value = if (value != null) {
                    fieldType.deserialize(value)
                } else {
                    null
                }
            }

            override fun getValue(thisRef: Source, property: KProperty<*>): T? {
                return value
            }

            override fun setValue(thisRef: Source, property: KProperty<*>, value: T?) {
                this.value = value
            }
        }
        thisRef.bindProperty(fieldName, fieldProperty)
        return fieldProperty
    }

    fun <T> Field<T>.required(): RequiredFieldValue<T> {
        return RequiredFieldValue(this)
    }

    class RequiredFieldValue<T>(val field: Field<T>) {
        operator fun provideDelegate(
            thisRef: Source, property: KProperty<*>
        ): ReadWriteProperty<Source, T> {
            val fieldName = field.getFieldName()
            val fieldType = field.getFieldType()
            val fieldProperty = object :
                FieldValueProperty<T>(property.name),
                ReadWriteProperty<Source, T>
            {
                override fun set(value: Any?) {
                    if (value == null) {
                        throw IllegalArgumentException("Value cannot be null for required field")
                    }
                    this.value = fieldType.deserialize(value)
                }

                override operator fun getValue(thisRef: Source, property: KProperty<*>): T {
                    return value ?: error("Field is not initialized")
                }

                override operator fun setValue(thisRef: Source, property: KProperty<*>, value: T) {
                    this.value = value
                }
            }
            thisRef.bindProperty(fieldName, fieldProperty)
            return fieldProperty
        }
    }

    fun <T: Source> SubDocument.source(sourceFactory: () -> T): SubSource<T> {
        return SubSource(this, sourceFactory)
    }

    class SubSource<T: Source>(
        private val subDocument: SubDocument,
        private val sourceFactory: () -> T
    ) {
        fun list(): ListFieldValue<T> {
            return ListFieldValue(subDocument) { value ->
                when (value) {
                    is Map<*, *> -> sourceFactory().apply {
                        setSource(value)
                    }
                    else -> throw IllegalArgumentException()
                }
            }
        }

        operator fun provideDelegate(
            thisRef: Source, property: KProperty<*>
        ): ReadOnlyProperty<Source, T?> {
            val fieldName = subDocument.getFieldName()
            val fieldProperty = object : FieldValueProperty<T>(property.name) {
                override fun set(value: Any?) {
                    this.value = when (value) {
                        null -> null
                        is Map<*, *> -> {
                            sourceFactory().apply {
                                setSource(value)
                            }
                        }
                        else -> throw IllegalArgumentException()
                    }
                }

                override fun getValue(thisRef: Source, property: KProperty<*>): T {
                    return value ?: error("Field is not initialized")
                }

                override fun setValue(thisRef: Source, property: KProperty<*>, value: T) {
                    TODO("not implemented")
                }
            }
            thisRef.bindProperty(fieldName, fieldProperty)
            return fieldProperty
        }
    }

    fun <T> Field<T>.list(): ListFieldValue<T> {
        return ListFieldValue(this, this.getFieldType()::deserialize)
    }

    class ListFieldValue<T>(
        val field: Named,
        val deserialize: (Any) -> T
    ) {
        operator fun provideDelegate(
            thisRef: Source, property: KProperty<*>
        ): ReadWriteProperty<Source, List<T?>> {
            val fieldName = field.getFieldName()
            val fieldProperty = object :
                FieldValueProperty<List<T?>>(property.name),
                ReadWriteProperty<Source, List<T?>>
            {
                override fun set(value: Any?) {
                    this.value = when (value) {
                        null -> emptyList()
                        is List<*> -> {
                            value.map { v ->
                                if (v != null) deserialize(v) else null
                            }
                        }
                        else -> listOf(deserialize(value))
                    }
                }

                override operator fun getValue(thisRef: Source, property: KProperty<*>): List<T?> {
                    return value ?: error("Field is not initialized")
                }

                override operator fun setValue(
                    thisRef: Source, property: KProperty<*>, value: List<T?>
                ) {
                    this.value = value
                }
            }
            thisRef.bindProperty(fieldName, fieldProperty)
            return fieldProperty
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

    val name by text()
    // FIXME:
    val keywords by name
    val status by int()
    val categories by int()
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
    val subDoc by MyDoc.subDoc.source(::MySubSource).list()
}
