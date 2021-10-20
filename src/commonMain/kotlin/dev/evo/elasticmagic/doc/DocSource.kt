package dev.evo.elasticmagic.doc

import dev.evo.elasticmagic.serde.Deserializer

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

typealias RawSource = Map<*, *>

fun emptySource(): Map<Nothing, Nothing> = emptyMap()

abstract class BaseDocSource {
    abstract fun toSource(): Map<String, Any?>

    abstract fun fromSource(rawSource: RawSource)

    fun withActionMeta(
        id: String,
        routing: String? = null,
        version: Long? = null,
        seqNo: Long? = null,
        primaryTerm: Long? = null,
    ): IdentDocSourceWithMeta {
        return IdentDocSourceWithMeta(
            meta = object : IdentActionMeta {
                override val id: String = id
                override val routing: String? = routing
                override val version: Long? = version
                override val seqNo: Long? = seqNo
                override val primaryTerm: Long? = primaryTerm
            },
            doc = this,
        )
    }

    fun withActionMeta(
        id: String? = null,
        routing: String? = null,
        version: Long? = null,
        seqNo: Long? = null,
        primaryTerm: Long? = null,
    ): DocSourceWithMeta {
        return DocSourceWithMeta(
            meta = object : ActionMeta {
                override val id: String? = id
                override val routing: String? = routing
                override val version: Long? = version
                override val seqNo: Long? = seqNo
                override val primaryTerm: Long? = primaryTerm
            },
            doc = this,
        )
    }
}

object DocSourceFactory {
    fun byJoin(
        vararg sourceFactories: Pair<String, () -> DocSource>
    ): (Deserializer.ObjectCtx) -> DocSource {
        val sourceFactoriesMap = sourceFactories.toMap()
        val joinFieldName = sourceFactories
            .map { (_, sourceFactory) -> sourceFactory() }
            .fold(null) { curJoinFieldName: String?, docSource ->
                if (curJoinFieldName == null) {
                    docSource.getJoinFieldName()
                } else {
                    val joinFieldName = docSource.getJoinFieldName()
                        ?: throw IllegalArgumentException(
                            "Missing join field"
                        )
                    if (curJoinFieldName != joinFieldName) {
                        throw IllegalArgumentException(
                            "Document sources have different join fields: " +
                                    "'$curJoinFieldName' and '$joinFieldName'"
                        )
                    }
                    curJoinFieldName
                }
            }
        require(joinFieldName != null) {
            "Missing join field"
        }

        return { obj ->
            val sourceObj = obj.obj("_source")
            val joinName = sourceObj.objOrNull(joinFieldName)?.string("name")
                ?: sourceObj.string(joinFieldName)
            sourceFactoriesMap[joinName]?.invoke()
                ?: throw IllegalStateException("No source factory for '$joinName' type")
        }
    }
}

open class DocSource : BaseDocSource() {
    private var fieldProps: ArrayList<FieldValueProperty<*>> = arrayListOf()
    private var fieldPropsByName: HashMap<String, FieldValueProperty<*>> = hashMapOf()
    private var joinFieldProperty: FieldValueProperty<*>? = null

    fun getJoinFieldName(): String? {
        return joinFieldProperty?.name
    }

    private fun bindProperty(fieldProperty: FieldValueProperty<*>) {
        val fieldName = fieldProperty.name
        if (fieldName in fieldPropsByName) {
            throw IllegalStateException(
                "Field [$fieldName] has already been bound to this document source"
            )
        }
        fieldProps.add(fieldProperty)
        fieldPropsByName[fieldName] = fieldProperty
        if (fieldProperty.type is JoinType) {
            val joinFieldName = joinFieldProperty?.name
            if (joinFieldName != null) {
                throw IllegalStateException(
                    "Join field has already been bound to this document source as [$joinFieldName] field"
                )
            }
            joinFieldProperty = fieldProperty
        }
    }

    override fun toSource(): Map<String, Any?> {
        val source = mutableMapOf<String, Any?>()
        for (fieldProperty in fieldProps) {
            fieldProperty.checkRequired()
            if (fieldProperty.isInitialized) {
                source[fieldProperty.name] = fieldProperty.serialize()
            }
        }
        return source
    }

    override fun fromSource(rawSource: RawSource) {
        clearSource()
        for ((fieldName, rawValue) in rawSource) {
            setFieldValue(fieldName as String, rawValue)
        }
        fieldProps.forEach(FieldValueProperty<*>::checkRequired)
    }

    private fun clearSource() {
        fieldProps.map(FieldValueProperty<*>::clear)
    }

    private fun setFieldValue(name: String, value: Any?) {
        val fieldProperty = fieldPropsByName[name]
            ?: throw IllegalArgumentException("Unknown field name: $name")
        fieldProperty.deserialize(value)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other !is DocSource) {
            return false
        }
        if (other::class != this::class) {
            return false
        }
        for ((fieldProperty, otherFieldProperty) in fieldProps.zip(other.fieldProps)) {
            if (
                fieldProperty.isInitialized &&
                otherFieldProperty.isInitialized &&
                fieldProperty.value != otherFieldProperty.value
            ) {
                return false
            }
            if (fieldProperty.isInitialized != otherFieldProperty.isInitialized) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var h = this::class.hashCode()
        for (p in fieldProps) {
            h = 37 * h + p.isInitialized.hashCode()
            h = 37 * h + p.value.hashCode()
        }
        return h
    }

    override fun toString(): String {
        return fieldProps
            .filter(FieldValueProperty<*>::isInitialized)
            .joinToString(
                separator = ", ", prefix = "${this::class.simpleName}(", postfix = ")"
            ) { prop ->
                "${prop.name}=${prop.value}"
            }
    }

    operator fun <V> BoundField<V, *>.provideDelegate(
        thisRef: DocSource, property: KProperty<*>
    ): ReadWriteProperty<DocSource, V?> {
        return OptionalValueProperty(
            getFieldName(), getFieldType()
        )
            .also {
                thisRef.bindProperty(it)
            }
    }

    fun <V> BoundField<V, *>.required(): RequiredListableValueDelegate<V> {
        return RequiredListableValueDelegate(
            getFieldName(), getFieldType()
        )
    }

    fun <V> BoundField<V, *>.list(): OptionalValueDelegate<List<V?>> {
        return OptionalValueDelegate(
            getFieldName(), OptionalListType(getFieldType())
        )
    }

    fun <V> BoundField<V, *>.default(defaultValue: () -> V): OptionalValueDelegateWithDefault<V> {
        return OptionalValueDelegateWithDefault(
            getFieldName(), getFieldType(), defaultValue
        )
    }

    operator fun <V> SubFields<V>.provideDelegate(
        thisRef: DocSource, property: KProperty<*>
    ): ReadWriteProperty<DocSource, V?> {
        return OptionalValueProperty(
            getFieldName(), getFieldType()
        )
            .also {
                thisRef.bindProperty(it)
            }
    }

    fun <V: BaseDocSource> SubDocument.source(sourceFactory: () -> V): OptionalListableValueDelegate<V> {
        val fieldType = getFieldType()
        return OptionalListableValueDelegate(
            getFieldName(), SourceType(fieldType, sourceFactory)
        )
    }

    open class OptionalValueDelegate<V>(
        protected val fieldName: String,
        protected val fieldType: FieldType<V, *>,
    ) {
        operator fun provideDelegate(
            thisRef: DocSource, property: KProperty<*>
        ): ReadWriteProperty<DocSource, V?> {
            return OptionalValueProperty(fieldName, fieldType)
                .also { thisRef.bindProperty(it) }
        }

        open fun required(): RequiredValueDelegate<V> {
            return RequiredValueDelegate(fieldName, fieldType)
        }

        fun default(defaultValue: () -> V): OptionalValueDelegateWithDefault<V> {
            return OptionalValueDelegateWithDefault(fieldName, fieldType, defaultValue)
        }
    }

    class OptionalValueDelegateWithDefault<V>(
        private val fieldName: String,
        private val fieldType: FieldType<V, *>,
        private val defaultValue: () -> V,
    ) {
        operator fun provideDelegate(
            thisRef: DocSource, property: KProperty<*>
        ): ReadWriteProperty<DocSource, V> {
            return DefaultValueProperty(fieldName, fieldType, defaultValue)
                .also { thisRef.bindProperty(it) }
        }
    }


    class OptionalListableValueDelegate<V>(
        fieldName: String,
        fieldType: FieldType<V, *>,
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

    open class RequiredValueDelegate<V>(
        protected val fieldName: String,
        protected val fieldType: FieldType<V, *>,
    ) {
        operator fun provideDelegate(
            thisRef: DocSource, property: KProperty<*>
        ): ReadWriteProperty<DocSource, V> {
            return RequiredValueProperty(
                fieldName, fieldType
            )
                .also { thisRef.bindProperty(it) }
        }

    }

    class RequiredListableValueDelegate<V>(
        fieldName: String,
        fieldType: FieldType<V, *>,
    ) : RequiredValueDelegate<V>(fieldName, fieldType) {
        fun list(): OptionalValueDelegate<List<V>> {
            return OptionalValueDelegate(
                fieldName, RequiredListType(fieldType)
            )
        }
    }

    /**
     * Container for a field value that distinguish null value from non-initialized.
     */
    private class FieldValue<V> {
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
    }

    private sealed class FieldValueProperty<V>(
        val name: String,
        val type: FieldType<V, *>,
        protected val fieldValue: FieldValue<V>,
    ) {
        abstract val isInitialized: Boolean
        abstract val value: V?

        open fun checkRequired() {}

        fun clear() = fieldValue.clear()

        abstract fun serialize(): Any?
        abstract fun deserialize(value: Any?)
    }

    private class OptionalValueProperty<V>(
        fieldName: String,
        fieldType: FieldType<V, *>,
    ) :
        FieldValueProperty<V>(fieldName, fieldType, FieldValue()),
        ReadWriteProperty<DocSource, V?>
    {
        override val isInitialized get() = fieldValue.isInitialized

        override val value get() = fieldValue.value

        override fun serialize(): Any? {
            return fieldValue.value?.let { v ->
                type.serialize(v)
            }
        }

        override fun deserialize(value: Any?) {
            fieldValue.value = if (value != null) {
                type.deserialize(value)
            } else {
                null
            }
        }

        override fun getValue(thisRef: DocSource, property: KProperty<*>): V? = value

        override fun setValue(thisRef: DocSource, property: KProperty<*>, value: V?) {
            fieldValue.value = value
        }
    }

    private class DefaultValueProperty<V>(
        fieldName: String,
        fieldType: FieldType<V, *>,
        val defaultValue: () -> V,
    ) :
        FieldValueProperty<V>(fieldName, fieldType, FieldValue()),
        ReadWriteProperty<DocSource, V>
    {
        override val isInitialized = true

        override val value get(): V {
            return when (val v = fieldValue.value) {
                null -> {
                    defaultValue().also(fieldValue::value::set)
                }
                else -> v
            }
        }

        override fun serialize(): Any {
            return type.serialize(value)
        }

        override fun deserialize(value: Any?) {
            fieldValue.value = if (value != null) {
                type.deserialize(value)
            } else {
                null
            }
        }

        override fun getValue(thisRef: DocSource, property: KProperty<*>): V = value

        override fun setValue(thisRef: DocSource, property: KProperty<*>, value: V) {
            fieldValue.value = value
        }
    }

    private class RequiredValueProperty<V>(
        fieldName: String,
        fieldType: FieldType<V, *>,
    ) :
        FieldValueProperty<V>(fieldName, fieldType, FieldValue()),
        ReadWriteProperty<DocSource, V>
    {
        override val isInitialized get() = fieldValue.isInitialized

        override fun checkRequired() {
            if (!isInitialized) {
                throw IllegalStateException("Field [$name] is required")
            }
        }

        override val value get(): V {
            return fieldValue.value ?: error("Field [$name] is required")
        }

        override fun serialize(): Any {
            return type.serialize(value)
        }

        override fun deserialize(value: Any?) {
            fieldValue.value = if (value != null) {
                type.deserialize(value)
            } else {
                throw IllegalArgumentException("Value cannot be null for required field [$name]")
            }
        }

        override fun getValue(thisRef: DocSource, property: KProperty<*>): V = value

        override fun setValue(thisRef: DocSource, property: KProperty<*>, value: V) {
            fieldValue.value = value
        }
    }
}

class DynDocSource private constructor(
    rawSource: RawSource = emptyMap<Any?, Any?>(),
    private var prefix: List<String> = emptyList(),
) : BaseDocSource() {
    private var source: MutableMap<String, Any?> = mutableMapOf()

    init {
        fromSource(rawSource)
    }

    constructor() : this(emptyMap<Any?, Any?>())

    constructor(rawSource: RawSource) : this(rawSource, prefix = emptyList())

    constructor(setup: (DynDocSource) -> Unit) : this() {
        setup(this)
    }

    private object DynSourceSerde {
        fun serialize(value: Any?): Any? {
            return when (value) {
                is DynDocSource -> value.toSource()
                is List<*> -> value.map(DynSourceSerde::serialize)
                null -> null
                else -> value
            }
        }

        fun deserialize(fieldName: String, value: Any?, prefix: List<String>): Any? {
            return when (value) {
                is Map<*, *> -> DynDocSource(value, prefix = prefix + listOf(fieldName))
                is List<*> -> value.map { deserialize(fieldName, it, prefix) }
                null -> null
                else -> value
            }
        }
    }

    override fun toSource(): Map<String, Any?> {
        val rawSource = mutableMapOf<String, Any?>()
        for ((fieldName, sourceValue) in source) {
            rawSource[fieldName] = DynSourceSerde.serialize(sourceValue)
        }
        return rawSource
    }

    override fun fromSource(rawSource: RawSource) {
        clearSource()
        for ((fieldName, fieldValue) in rawSource) {
            source[fieldName as String] = DynSourceSerde.deserialize(fieldName, fieldValue, prefix)
        }
    }

    fun clearSource() {
        source.clear()
    }

    private fun <V> setFieldValue(path: List<String>, value: V?, serialize: (V) -> Any) {
        var curSource = this.source
        for ((ix, fieldName) in path.subList(0, path.size - 1).withIndex()) {
            val subDocSource = when (val subDocSource = curSource[fieldName]) {
                null -> DynDocSource(prefix = path.subList(0, ix + 1))
                is DynDocSource -> subDocSource
                is List<*> -> throw IllegalArgumentException(
                    "Cannot traverse through a list value: ${currentFieldName(path, ix)}"
                )
                else -> throw IllegalArgumentException(
                    "Expected sub document for field: ${currentFieldName(path, ix)}"
                )
            }
            curSource[fieldName] = subDocSource
            curSource = subDocSource.source
        }

        val serializedValue = if (value != null) serialize(value) else null
        if (serializedValue is DynDocSource) {
            serializedValue.stripPrefix(path)
        }
        curSource[path.last()] = serializedValue
    }

    private fun <V> getFieldValue(path: List<String>, deserialize: (Any) -> V): V? {
        var curSource: MutableMap<*, *> = source
        for ((ix, fieldName) in path.subList(0, path.size - 1).withIndex()) {
            curSource = when (val subSource = curSource[fieldName]) {
                null -> return null
                is DynDocSource -> subSource.source
                is List<*> -> throw IllegalArgumentException(
                    "Cannot traverse through a list value: ${currentFieldName(path, ix)}"
                )
                else -> throw IllegalArgumentException(
                    "Expected sub document for field: ${currentFieldName(path, ix)}"
                )
            }
        }
        return deserialize(curSource[path.last()] ?: return null)
    }

    private fun currentFieldName(path: List<String>, curIx: Int? = null): String {
        val toIndex = if (curIx != null) curIx + 1 else path.size
        return path.subList(0, toIndex).joinToString(".")
    }

    private fun checkPathStartsWithPrefix(path: List<String>): List<String> {
        require(prefix.size <= path.size) {
            failPrefixCheck(path)
        }
        for ((name, expectedName) in path.zip(prefix)) {
            if (name != expectedName) {
                failPrefixCheck(path)
            }
        }
        return path.subList(prefix.size, path.size)
    }

    private fun failPrefixCheck(path: List<String>): Nothing {
        throw IllegalArgumentException(
            "Field name ${path.joinToString(".")} does not start with " +
                    "sub document prefix: ${prefix.joinToString(".")}"
        )
    }

    private fun stripPrefix(prefix: List<String>) {
        for ((ix, fieldName) in prefix.withIndex()) {
            when (val fieldValue = source[fieldName]) {
                null -> {
                    if (source.isNotEmpty()) {
                        throw IllegalArgumentException(
                            "Cannot bind sub document to prefix ${currentFieldName(prefix)}:" +
                                    " another fields are present"
                        )
                    }
                }
                is DynDocSource -> {
                    if (source.size > 1) {
                        throw IllegalArgumentException(
                            "Cannot bind sub document to prefix ${currentFieldName(prefix)}:" +
                                    " another fields are present"
                        )
                    }
                    source = fieldValue.source
                }
                else -> throw IllegalArgumentException(
                    "Expected sub document for field: ${currentFieldName(prefix, ix)}"
                )
            }
        }
        this.prefix = prefix
    }

    operator fun get(name: String): Any? {
        return getFieldValue(name.split('.'), AnyFieldType::deserialize)
    }

    operator fun <V> get(field: BoundField<V, *>): V? {
        val fieldType = field.getFieldType()
        val path = checkPathStartsWithPrefix(
            field.getQualifiedFieldName().split('.')
        )
        return getFieldValue(path, fieldType::deserialize)
    }

    operator fun <V: SubDocument> get(subDoc: V): DynDocSource? {
        val path = checkPathStartsWithPrefix(
            subDoc.getQualifiedFieldName().split('.')
        )
        return getFieldValue(path, AnyFieldType::deserialize) as DynDocSource?
    }

    operator fun set(name: String, value: Any?) {
        return setFieldValue(name.split('.'), value, AnyFieldType::serialize)
    }

    operator fun <V> set(field: BoundField<V, *>, value: V?) {
        val fieldType = field.getFieldType()
        val path = checkPathStartsWithPrefix(
            field.getQualifiedFieldName().split('.')
        )
        setFieldValue(path, value, fieldType::serialize)
    }

    operator fun <V: SubDocument> set(subDoc: V, value: DynDocSource?) {
        val path = checkPathStartsWithPrefix(
            subDoc.getQualifiedFieldName().split('.')
        )
        setFieldValue(path, value, AnyFieldType::serialize)
    }

    override fun toString(): String {
        return "${this::class.simpleName}(prefix = $prefix, source = $source)"
    }
}

fun <V, T> BoundField<V, T>.list(): BoundField<List<V?>, T> {
    return object : BoundField<List<V?>, T>(
        getFieldName(),
        OptionalListType(getFieldType()),
        getMappingParams(),
        getParent()
    ) {
        override fun getQualifiedFieldName(): String {
            return this@list.getQualifiedFieldName()
        }
    }
}

fun SubDocument.list(): BoundField<List<DynDocSource?>, Nothing> {
    val listType = OptionalListType(DynDocSourceFieldType)
    return object : BoundField<List<DynDocSource?>, Nothing>(
        getFieldName(), listType, emptyMap(), getParent()
    ) {
        override fun getQualifiedFieldName(): String {
            return this@list.getQualifiedFieldName()
        }
    }
}

internal object AnyFieldType : FieldType<Any, Any> {
    override val name: String
        get() = throw IllegalStateException("Should not be used in mappings")
    override val termType = Any::class

    override fun deserialize(v: Any, valueFactory: (() -> Any)?): Any {
        return v
    }

    override fun serializeTerm(v: Any): Any = serErr(v)

    override fun deserializeTerm(v: Any): Any = v
}

internal object DynDocSourceFieldType : FieldType<DynDocSource, Nothing> {
    override val name: String
        get() = throw IllegalStateException("Should not be used in mappings")
    override val termType = Nothing::class

    override fun deserialize(v: Any, valueFactory: (() -> DynDocSource)?): DynDocSource {
        return when (v) {
            is DynDocSource -> v
            else -> throw IllegalArgumentException(
                "DynDocSource object expected but was ${v::class.simpleName}"
            )
        }
    }

    override fun serializeTerm(v: Nothing): Nothing {
        throw IllegalStateException("Unreachable")
    }

    override fun deserializeTerm(v: Any): Nothing {
        throw IllegalStateException("Unreachable")
    }
}
