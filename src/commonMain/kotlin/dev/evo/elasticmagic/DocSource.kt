package dev.evo.elasticmagic

import dev.evo.elasticmagic.serde.Deserializer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

typealias RawSource = Map<*, *>

fun emptySource(): Map<Nothing, Nothing> = emptyMap()

abstract class BaseDocSource {
    abstract fun getSource(): Map<String, Any?>

    abstract fun setSource(rawSource: RawSource)

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
        val joinField = sourceFactories
            .map { (_, sourceFactory) -> sourceFactory() }
            .fold(null) { curJoinField: DocSource.FieldValue<*>?, docSource ->
                if (curJoinField == null) {
                    docSource.getJoinField()
                } else {
                    val joinField = docSource.getJoinField()
                        ?: throw IllegalArgumentException(
                            "Missing join field"
                        )
                    if (curJoinField.name != joinField.name) {
                        throw IllegalArgumentException(
                            "Document sources have different join fields: " +
                                    "'${curJoinField.name}' and '${joinField.name}'"
                        )
                    }
                    curJoinField
                }
            }
        require(joinField != null) {
            "Missing join field"
        }

        return { obj ->
            val sourceObj = obj.obj("_source")
            val joinName = sourceObj.objOrNull(joinField.name)?.string("name")
                ?: sourceObj.string(joinField.name)
            sourceFactoriesMap[joinName]?.invoke()
                ?: throw IllegalStateException("No source factory for '$joinName' type")
        }
    }
}

open class DocSource : BaseDocSource() {
    private val fieldValues: MutableList<FieldValue<*>> = mutableListOf()
    private val fieldProperties: MutableMap<String, FieldValueProperty<*>> = mutableMapOf()
    private var joinField: FieldValue<*>? = null

    fun getJoinField(): FieldValue<*>? {
        return joinField
    }

    private fun bindProperty(fieldProperty: FieldValueProperty<*>) {
        val fieldValue = fieldProperty.fieldValue
        fieldValues.add(fieldValue)
        fieldProperties[fieldValue.name] = fieldProperty
        if (fieldProperty.fieldType is JoinType) {
            if (joinField != null) {
                throw IllegalStateException(
                    "Join field already bound to this document source: ${joinField?.name}"
                )
            }
            joinField = fieldValue
        }
    }

    override fun getSource(): Map<String, Any?> {
        val source = mutableMapOf<String, Any?>()
        for (fieldValue in fieldValues) {
            if (fieldValue.isRequired && !fieldValue.isInitialized && fieldValue.defaultValue == null) {
                throw IllegalStateException("Field ${fieldValue.name} is required")
            }
            if (fieldValue.isInitialized || fieldValue.defaultValue != null) {
                source[fieldValue.name] = fieldValue.serialize()
            }
        }
        return source
    }

    override fun setSource(rawSource: RawSource) {
        clearSource()
        for ((fieldName, fieldValue) in rawSource) {
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
        if (other !is DocSource) {
            return false
        }
        if (other::class != this::class) {
            return false
        }
        for ((fieldValue, otherFieldValue) in fieldValues.zip(other.fieldValues)) {
            if (fieldValue != otherFieldValue) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var h = this::class.hashCode()
        for (v in fieldValues) {
            h = 37 * h + v.hashCode()
        }
        return h
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

    operator fun <V> BoundField<V>.provideDelegate(
        thisRef: DocSource, property: KProperty<*>
    ): ReadWriteProperty<DocSource, V?> {
        return OptionalValueProperty(
            getFieldName(), getFieldType()
        )
            .also {
                thisRef.bindProperty(it)
            }
    }

    fun <V> BoundField<V>.required(): RequiredListableValueDelegate<V> {
        return RequiredListableValueDelegate(
            getFieldName(), getFieldType()
        )
    }

    fun <V> BoundField<V>.list(): OptionalValueDelegate<List<V?>> {
        return OptionalValueDelegate(
            getFieldName(), OptionalListType(getFieldType())
        )
    }

    fun <V> BoundField<V>.default(defaultValue: () -> V): OptionalValueDelegateWithDefault<V> {
        return OptionalValueDelegateWithDefault(
            getFieldName(), getFieldType(), defaultValue
        )
    }

    operator fun <V> SubFields<V>.provideDelegate(
        thisRef: DocSource, property: KProperty<*>
    ): ReadWriteProperty<DocSource, V?> {
        return OptionalValueProperty<V>(
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

    class FieldValue<V>(
        val name: String,
        val type: FieldType<V>,
        val isRequired: Boolean,
        val defaultValue: (() -> V)? = null,
    ) {
        var isInitialized: Boolean = false
            private set

        private var _value: V? = null
        var value: V?
            get() {
                if (!isInitialized && defaultValue != null) {
                    isInitialized = true
                    _value = defaultValue.let { it() }
                }
                return _value
            }
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

        override fun equals(other: Any?): Boolean {
            if (other !is FieldValue<*>) {
                return false
            }
            if (name != other.name) {
                return false
            }
            if (isInitialized != other.isInitialized) {
                return false
            }
            if (value != other.value) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var h = name.hashCode()
            h = 37 * h + isInitialized.hashCode()
            h = 37 * h + value.hashCode()
            return h
        }

        override fun toString(): String {
            return "FieldValue '$name' of type ${type.name}, isRequired: $isRequired"
        }
    }

    abstract class FieldValueProperty<V>(
        val fieldValue: FieldValue<V>,
        val fieldType: FieldType<V>,
    ) {
        abstract fun set(value: Any?)
    }

    open class OptionalValueDelegate<V>(
        protected val fieldName: String,
        protected val fieldType: FieldType<V>,
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
        private val fieldType: FieldType<V>,
        private val defaultValue: () -> V,
    ) {
        operator fun provideDelegate(
            thisRef: DocSource, property: KProperty<*>
        ): ReadWriteProperty<DocSource, V?> {
            return OptionalValueProperty(fieldName, fieldType, defaultValue)
                .also { thisRef.bindProperty(it) }
        }
    }


    class OptionalListableValueDelegate<V>(
        fieldName: String,
        fieldType: FieldType<V>,
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

    protected class OptionalValueProperty<V>:
        FieldValueProperty<V>,
        ReadWriteProperty<DocSource, V?>
    {
        constructor(
            fieldName: String,
            fieldType: FieldType<V>,
        ): super(FieldValue(fieldName, fieldType, true), fieldType)

        constructor(
            fieldName: String,
            fieldType: FieldType<V>,
            defaultValue: () -> V,
        ): super(FieldValue(fieldName, fieldType, true, defaultValue), fieldType)

        override fun set(value: Any?) {
            fieldValue.value = if (value != null) {
                fieldType.deserialize(value)
            } else {
                null
            }
        }

        override fun getValue(thisRef: DocSource, property: KProperty<*>): V? {
            return fieldValue.value
        }

        override fun setValue(thisRef: DocSource, property: KProperty<*>, value: V?) {
            fieldValue.value = value
        }
    }

    open class RequiredValueDelegate<V>(
        protected val fieldName: String,
        protected val fieldType: FieldType<V>,
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
        fieldType: FieldType<V>,
    ) : RequiredValueDelegate<V>(fieldName, fieldType) {
        fun list(): OptionalValueDelegate<List<V>> {
            return OptionalValueDelegate(
                fieldName, RequiredListType(fieldType)
            )
        }
    }

    protected class RequiredValueProperty<V>(
        fieldName: String,
        fieldType: FieldType<V>,
    ) :
        FieldValueProperty<V>(FieldValue(fieldName, fieldType, true), fieldType),
        ReadWriteProperty<DocSource, V>
    {

        override fun set(value: Any?) {
            fieldValue.value = if (value != null) {
                fieldType.deserialize(value)
            } else {
                throw IllegalArgumentException("Value cannot be null for required field")
            }
        }

        override fun getValue(thisRef: DocSource, property: KProperty<*>): V {
            return fieldValue.value ?: error("Field is not initialized")
        }

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
        setSource(rawSource)
    }

    constructor() : this(emptyMap<Any?, Any?>())

    constructor(rawSource: RawSource) : this(rawSource, prefix = emptyList())

    constructor(setup: (DynDocSource) -> Unit) : this() {
        setup(this)
    }

    private object DynSourceSerde {
        fun serialize(value: Any?): Any? {
            return when (value) {
                is DynDocSource -> value.getSource()
                is List<*> -> value.map(::serialize)
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

    override fun getSource(): Map<String, Any?> {
        val rawSource = mutableMapOf<String, Any?>()
        for ((fieldName, sourceValue) in source) {
            rawSource[fieldName] = DynSourceSerde.serialize(sourceValue)
        }
        return rawSource
    }

    override fun setSource(rawSource: RawSource) {
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

    operator fun <V> get(field: BoundField<V>): V? {
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

    operator fun <V> set(field: BoundField<V>, value: V?) {
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

fun <V> BoundField<V>.list(): BoundField<List<V?>> {
    return object : BoundField<List<V?>>(
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

fun SubDocument.list(): BoundField<List<DynDocSource?>> {
    val listType = OptionalListType(DynDocSourceFieldType)
    return object : BoundField<List<DynDocSource?>>(
        getFieldName(), listType, emptyMap(), getParent()
    ) {
        override fun getQualifiedFieldName(): String {
            return this@list.getQualifiedFieldName()
        }
    }
}

internal object AnyFieldType : FieldType<Any> {
    override val name: String
        get() = throw IllegalStateException("Should not be used in mappings")

    override fun deserialize(v: Any, valueFactory: (() -> Any)?): Any {
        return v
    }
}

internal object DynDocSourceFieldType : FieldType<DynDocSource> {
    override val name: String
        get() = throw IllegalStateException("Should not be used in mappings")

    override fun deserialize(v: Any, valueFactory: (() -> DynDocSource)?): DynDocSource {
        return when (v) {
            is DynDocSource -> v
            else -> throw IllegalArgumentException(
                "DynDocSource object expected but was ${v::class.simpleName}"
            )
        }
    }
}
