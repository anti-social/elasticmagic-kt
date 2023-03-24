package dev.evo.elasticmagic.doc

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.ToValue
import dev.evo.elasticmagic.query.FieldOperations
import dev.evo.elasticmagic.query.Named
import dev.evo.elasticmagic.query.Script
import dev.evo.elasticmagic.types.AnyFieldType
import dev.evo.elasticmagic.types.BooleanType
import dev.evo.elasticmagic.types.ByteType
import dev.evo.elasticmagic.types.DoubleType
import dev.evo.elasticmagic.types.EnumFieldType
import dev.evo.elasticmagic.types.FieldType
import dev.evo.elasticmagic.types.FloatType
import dev.evo.elasticmagic.types.IntEnumValue
import dev.evo.elasticmagic.types.IntType
import dev.evo.elasticmagic.types.Join
import dev.evo.elasticmagic.types.JoinType
import dev.evo.elasticmagic.types.KeywordEnumValue
import dev.evo.elasticmagic.types.KeywordType
import dev.evo.elasticmagic.types.LongType
import dev.evo.elasticmagic.types.NestedType
import dev.evo.elasticmagic.types.ObjectType
import dev.evo.elasticmagic.types.ShortType
import dev.evo.elasticmagic.types.TextType
import dev.evo.elasticmagic.util.OrderedMap

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Represents field of any type in an Elasticsearch document.
 * See [FieldSet.getAllFields] and [FieldSet.get] methods.
 */
interface MappingField<T> : FieldOperations<T> {
    fun getMappingParams(): Params
}

/**
 * Represents field of a specific type. Usually it can be accessed as a document property.
 *
 * @param name - name of the field
 * @param type - type of the field
 * @param params - mapping parameters
 * @param parent - the [FieldSet] object to which the field is bound
 */
open class BoundField<V, T>(
    private val name: String,
    private val type: FieldType<V, T>,
    private val params: Params,
    private val parent: FieldSet,
) : MappingField<T> {
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

    override fun getFieldType(): FieldType<V, T> = type

    override fun getMappingParams(): Params = params

    fun getParent(): FieldSet = parent


    override fun equals(other: Any?): Boolean {
        if (other !is BoundField<*, *>) {
            return false
        }
        return qualifiedName == other.qualifiedName &&
                type == other.type &&
                params == other.params
    }

    override fun hashCode(): Int {
        var h = qualifiedName.hashCode()
        h = 37 * h + type.hashCode()
        h = 37 * h + params.hashCode()
        return h
    }

    override fun toString(): String {
        return "BoundField($name, $type, $params, $parent)"
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
    type: FieldType<Join, String>,
    relations: Map<String, List<String>>,
    params: Params,
    parent: FieldSet,
) : BoundField<Join, String>(name, type, Params(params, "relations" to relations), parent) {

    inner class Parent(private val name: String) : FieldOperations<String> {
        override fun getFieldType(): FieldType<*, String> = KeywordType

        override fun getFieldName(): String = name

        override fun getQualifiedFieldName(): String {
            return "${this@BoundJoinField.getQualifiedFieldName()}#$name"
        }
    }

    private val parentFields = relations.keys.associateWith { parentFieldName ->
        Parent(parentFieldName)
    }
    
    fun parent(name: String): FieldOperations<String> {
        return parentFields[name]
            ?: throw IllegalArgumentException(
                "Unknown parent relation: $name, possible relations: ${parentFields.keys}"
            )
    }
}

/**
 * Represents a runtime field.
 *
 * @param script - script to calculate the runtime field
 *
 * See more at https://www.elastic.co/guide/en/elasticsearch/reference/current/runtime.html
 */
class BoundRuntimeField<V>(
    name: String,
    type: FieldType<V, V>,
    val script: Script,
    parent: FieldSet,
) : BoundField<V, V>(name, type, Params("script" to script), parent)

@Suppress("UnnecessaryAbstractClass")
abstract class FieldSetShortcuts {
    protected fun <V, T> field(
        name: String?,
        type: FieldType<V, T>,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): FieldSet.Field<V, T> {
        @Suppress("NAME_SHADOWING")
        val params = Params(
            params,
            "doc_values" to docValues,
            "index" to index,
            "store" to store,
        )
        return FieldSet.Field(name, type, params)
    }

    protected fun <V, T> field(
        type: FieldType<V, T>,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): FieldSet.Field<V, T> {
        return field(
            null, type,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }

    protected fun boolean(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): FieldSet.Field<Boolean, Boolean> {
        return field(
            name, BooleanType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }

    protected fun byte(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): FieldSet.Field<Byte, Byte> {
        return field(
            name, ByteType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }

    protected fun short(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): FieldSet.Field<Short, Short> {
        return field(
            name, ShortType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }

    protected fun int(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): FieldSet.Field<Int, Int> {
        return field(
            name, IntType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }

    protected fun long(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): FieldSet.Field<Long, Long> {
        return field(
            name, LongType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }

    protected fun float(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): FieldSet.Field<Float, Float> {
        return field(
            name, FloatType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }

    protected fun double(
        name: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): FieldSet.Field<Double, Double> {
        return field(
            name, DoubleType,
            docValues = docValues,
            index = index,
            store = store,
            params = params,
        )
    }

    protected fun keyword(
        name: String? = null,
        normalizer: String? = null,
        docValues: Boolean? = null,
        index: Boolean? = null,
        store: Boolean? = null,
        params: Params? = null,
    ): FieldSet.Field<String, String> {
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

    protected fun text(
        name: String? = null,
        index: Boolean? = null,
        indexOptions: String? = null,
        store: Boolean? = null,
        norms: Boolean? = null,
        boost: Double? = null,
        analyzer: String? = null,
        searchAnalyzer: String? = null,
        params: Params? = null,
    ): FieldSet.Field<String, String> {
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

    protected fun join(
        name: String? = null,
        relations: Map<String, List<String>>,
        eagerGlobalOrdinals: Boolean? = null,
    ): FieldSet.JoinField {
        val params = Params(
            "eager_global_ordinals" to eagerGlobalOrdinals,
        )
        // TODO: relation sub-fields
        return FieldSet.JoinField(name, JoinType, relations, params = params)
    }
}

/**
 * Base class for any types which hold set of fields:
 * https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html
 */
abstract class FieldSet : FieldSetShortcuts(), Named {
    private val fields: OrderedMap<String, MappingField<*>> = OrderedMap()

    // TODO: consider to make it public
    internal fun addField(field: MappingField<*>) {
        fields[field.getFieldName()] = field
    }

    fun getAllFields(): Collection<MappingField<*>> {
        return fields.values
    }

    operator fun get(name: String): MappingField<*>? {
        return fields[name]
    }

    inline fun <reified T> getFieldByName(name: String): MappingField<T> {
        val field = this[name] ?: throw IllegalArgumentException("Missing field: [$name]")
        val termType = T::class
        if (field.getFieldType().termType != termType) {
            throw IllegalArgumentException("Expected $name field should be of type ${termType.simpleName}")
        }
        @Suppress("UNCHECKED_CAST")
        return field as MappingField<T>
    }


    open class Field<V, T>(
        val name: String?,
        val type: FieldType<V, T>,
        val params: Params,
    ) {
        operator fun provideDelegate(
            thisRef: FieldSet, prop: KProperty<*>
        ): ReadOnlyProperty<FieldSet, BoundField<V, T>> {
            val field = BoundField(
                name ?: prop.name,
                type,
                params,
                thisRef,
            )
            thisRef.addField(field)
            return ReadOnlyProperty { _, _ -> field }
        }
    }

    class JoinField(
        val name: String?,
        val type: JoinType,
        val relations: Map<String, List<String>>,
        val params: Params,
    ) {
        operator fun provideDelegate(
            thisRef: BaseDocument, prop: KProperty<*>
        ): ReadOnlyProperty<BaseDocument, BoundJoinField> {
            val field = BoundJoinField(
                name ?: prop.name,
                type,
                relations,
                params,
                thisRef,
            )
            thisRef.addField(field)
            return ReadOnlyProperty { _, _ -> field }
        }
    }
}

/**
 * Maps integer value to the corresponding enum variant.
 *
 * @param fieldValue function that provides field value of an enum variant.
 * It is not recommended to use [Enum.ordinal] property for field value as it can change
 * when new variant is added.
 */
inline fun <reified V: Enum<V>> FieldSet.Field<Int, Int>.enum(
    fieldValue: IntEnumValue<V>,
): FieldSet.Field<V, V> {
    return FieldSet.Field(
        name,
        EnumFieldType(
            enumValues(),
            fieldValue,
            type,
            V::class
        ),
        emptyMap()
    )
}

/**
 * Maps string value to the corresponding enum variant.
 *
 * @param fieldValue function that provides field value of an enum variant.
 * [Enum.name] property will be used if [fieldValue] is not provided.
 */
inline fun <reified V: Enum<V>> FieldSet.Field<String, String>.enum(
    fieldValue: KeywordEnumValue<V>? = null,
): FieldSet.Field<V, V> {
    return FieldSet.Field(
        name,
        EnumFieldType(
            enumValues(),
            fieldValue ?: KeywordEnumValue { it.name },
            type,
            V::class
        ),
        emptyMap()
    )
}

/**
 * Represents Elasticsearch multi-fields:
 * https://www.elastic.co/guide/en/elasticsearch/reference/7.10/multi-fields.html
 */
open class SubFields<V>(private val field: BoundField<V, V>) : FieldSet(), FieldOperations<V> {
    fun getBoundField(): BoundField<V, V> = field

    override fun getFieldType(): FieldType<V, V> = field.getFieldType()

    override fun getFieldName(): String = field.getFieldName()

    override fun getQualifiedFieldName(): String = field.getQualifiedFieldName()

    class UnboundSubFields<V, F: SubFields<V>>(
        internal val unboundField: Field<V, V>,
        internal val subFieldsFactory: (BoundField<V, V>) -> F,
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

internal open class WrapperField<T>(val field: MappingField<T>) : MappingField<T> {
    override fun getFieldName(): String = field.getFieldName()
    override fun getQualifiedFieldName(): String = field.getQualifiedFieldName()
    override fun getFieldType(): FieldType<*, T> = field.getFieldType()
    override fun getMappingParams(): Params = field.getMappingParams()
}

internal class SubFieldsField<T>(
    field: MappingField<T>,
    val subFields: SubFields<*>
) : WrapperField<T>(field)

abstract class BaseDocument : FieldSet() {
    fun <V, F: SubFields<V>> Field<V, V>.subFields(
        factory: (BoundField<V, V>) -> F): SubFields.UnboundSubFields<V, F> {
        return SubFields.UnboundSubFields(this, factory)
    }

    fun <T: SubDocument> `object`(
        name: String?,
        factory: (DocSourceField) -> T,
        enabled: Boolean? = null,
        dynamic: Dynamic? = null,
        params: Params = Params(),
    ): SubDocument.UnboundSubDocument<T> {
        @Suppress("NAME_SHADOWING")
        val params = Params(
            params,
            "enabled" to enabled,
            "dynamic" to dynamic,
        )
        return SubDocument.UnboundSubDocument(name, ObjectType(), params, factory)
    }

    fun <T: SubDocument> `object`(
        factory: (DocSourceField) -> T,
        enabled: Boolean? = null,
        dynamic: Dynamic? = null,
        params: Params = Params(),
    ): SubDocument.UnboundSubDocument<T> {
        return `object`(null, factory, enabled, dynamic, params)
    }

    fun <T: SubDocument> obj(
        name: String?,
        factory: (DocSourceField) -> T,
        enabled: Boolean? = null,
        dynamic: Dynamic? = null,
        params: Params = Params(),
    ): SubDocument.UnboundSubDocument<T> {
        return `object`(name, factory, enabled, dynamic, params)
    }

    fun <T: SubDocument> obj(
        factory: (DocSourceField) -> T,
        enabled: Boolean? = null,
        dynamic: Dynamic? = null,
        params: Params = Params(),
    ): SubDocument.UnboundSubDocument<T> {
        return `object`(factory, enabled, dynamic, params)
    }

    fun <T: SubDocument> nested(
        name: String?,
        factory: (DocSourceField) -> T,
        dynamic: Dynamic? = null,
        params: Params = Params()
    ): SubDocument.UnboundSubDocument<T> {
        @Suppress("NAME_SHADOWING")
        val params = Params(
            params,
            "dynamic" to dynamic,
        )
        return SubDocument.UnboundSubDocument(name, NestedType(), params, factory)
    }

    fun <T: SubDocument> nested(
        factory: (DocSourceField) -> T,
        dynamic: Dynamic? = null,
        params: Params = Params()
    ): SubDocument.UnboundSubDocument<T> {
        return nested(null, factory, dynamic, params)
    }
}

typealias DocSourceField = BoundField<BaseDocSource, Nothing>

/**
 * Represents Elasticsearch sub-document.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class SubDocument(
    private val field: DocSourceField,
    dynamic: Dynamic? = null,
) : BaseDocument(), FieldOperations<Nothing> {
    val options: MappingOptions = MappingOptions(
        dynamic = dynamic,
    )

    fun getBoundField(): DocSourceField = field

    override fun getFieldName(): String = field.getFieldName()

    override fun getQualifiedFieldName(): String = field.getQualifiedFieldName()

    override fun getFieldType(): FieldType<BaseDocSource, Nothing> = field.getFieldType()

    fun getParent(): FieldSet = field.getParent()

    class UnboundSubDocument<T: SubDocument>(
        private val name: String?,
        internal val type: ObjectType<BaseDocSource>,
        internal val params: Params,
        internal val subDocumentFactory: (DocSourceField) -> T,
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

internal class SubDocumentField<T>(
    field: MappingField<T>,
    val subDocument: SubDocument
) : WrapperField<T>(field)

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
    abstract class BaseMetaField<V, B: MappingField<V>>(
        name: String, type: FieldType<V, V>, params: Params = Params(),
        private val boundFieldFactory: (String, Params, MetaFields) -> B
    ) : Field<V, V>(name, type, params) {
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
        name: String, type: FieldType<V, V>, params: Params = Params()
    ) : BaseMetaField<V, BoundField<V, V>>(
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
    ) : BoundField<String, String>(name, KeywordType, params, parent)

    class FieldNamesField(
        enabled: Boolean? = null,
    ) : BaseMetaField<String, BoundFieldNamesField>(
        "_field_names", KeywordType, Params("enabled" to enabled),
        MetaFields::BoundFieldNamesField
    )

    class BoundFieldNamesField(
        name: String, params: Params, parent: MetaFields
    ) : BoundField<String, String>(name, KeywordType, params, parent)

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
    ) : BoundField<String, String>(name, KeywordType, params, parent)

    class SizeField(
        enabled: Boolean? = null,
    ) : BaseMetaField<Long, BoundSizeField>(
        "_size", LongType, Params("enabled" to enabled),
        MetaFields::BoundSizeField
    )

    class BoundSizeField(
        name: String, params: Params, parent: MetaFields
    ) : BoundField<Long, Long>(name, LongType, params, parent)
}

/**
 * Fields that are accessible when search query is executing. They are mostly used in scripts.
 */
class RuntimeFields : RootFieldSet() {
    val score by Field("_score", DoubleType, emptyMap())
    val doc by Field("_doc", IntType, emptyMap())
    val seqNo by Field("_seq_no", LongType, emptyMap())
}

/**
 * Controls dynamic field mapping setting.
 * See: https://www.elastic.co/guide/en/elasticsearch/reference/current/dynamic-field-mapping.html
 */
enum class Dynamic : ToValue<Any> {
    TRUE, FALSE, STRICT, RUNTIME;

    override fun toValue() = when (this) {
        TRUE -> true
        FALSE -> false
        else -> name.lowercase()
    }
}

data class MappingOptions(
    val dynamic: Dynamic? = null,
    val numericDetection: Boolean? = null,
    val dateDetection: Boolean? = null,
    val dynamicDateFormats: List<String>? = null,
)

open class RootFieldSet : BaseDocument() {
    companion object : RootFieldSet()

    override fun getFieldName(): String = ""

    override fun getQualifiedFieldName(): String = ""
}

class BoundMappingTemplate<V, T, F>(
    val name: String,
    val mapping: DynamicTemplates.DynamicField<V, T, F>,
    val matchOptions: DynamicTemplates.MatchOptions,
) {
    fun field(fieldPath: String): F {
        require(matchOptions.matches(fieldPath)) {
            "[$fieldPath] is not matched: [$matchOptions]"
        }
        return mapping.field(fieldPath)
    }
}

@Suppress("UnnecessaryAbstractClass")
abstract class DynamicTemplates : RootFieldSet() {
    private val templates: OrderedMap<String, BoundMappingTemplate<*, *, *>> = OrderedMap()

    companion object {
        internal fun <V, T> instantiateField(
            fieldPath: String, fieldType: FieldType<V, T>, params: Params? = null
        ): BoundField<V, T> {
            val fieldName = fieldPath.substringAfterLast('.')
            val parentFieldName = if (fieldName != fieldPath) {
                fieldPath.substringBeforeLast('.')
            } else {
                ""
            }
            return BoundField(
                fieldName, fieldType, params ?: Params(),
                object : FieldSet() {
                    override fun getFieldName() = parentFieldName

                    override fun getQualifiedFieldName() = parentFieldName
                }
            )
        }
    }

    internal fun addTemplate(template: BoundMappingTemplate<*, *, *>) {
        templates[template.name] = template
    }

    fun getAllTemplates(): Collection<BoundMappingTemplate<*, *, *>> {
        return templates.values
    }

    fun getTemplate(name: String): BoundMappingTemplate<*, *, *>? {
        return templates[name]
    }

    /**
     * Template without field type.
     */
    fun template(
        name: String? = null,
        mapping: Mapping,
        match: String? = null,
        unmatch: String? = null,
        pathMatch: String? = null,
        pathUnmatch: String? = null,
        matchPattern: MatchPattern? = null,
        params: Params? = null,
    ): MappingTemplate<Any, Any, BoundField<Any, Any>> {
        val mappingParams = Params(
            mapping.params,
            "index" to mapping.index,
            "doc_values" to mapping.docValues,
            "store" to mapping.store,
        )
        return MappingTemplate(
            name,
            DynamicField.Simple(
                MappingKind.MAPPING,
                AnyFieldType,
                mappingParams
            ),
            MatchOptions(
                match = match,
                unmatch = unmatch,
                pathMatch = pathMatch,
                pathUnmatch = pathUnmatch,
                matchPattern = matchPattern,
                params = params,
            ),
        )
    }

    /**
     * Template with field type detected by JSON parser.
     */
    fun <V, T> template(
        name: String? = null,
        mapping: Mapping,
        matchMappingType: MatchMappingType<V, T>,
        match: String? = null,
        unmatch: String? = null,
        pathMatch: String? = null,
        pathUnmatch: String? = null,
        matchPattern: MatchPattern? = null,
        params: Params? = null,
    ): MappingTemplate<V, T, BoundField<V, T>> {
        val mappingParams = Params(
            mapping.params,
            "index" to mapping.index,
            "doc_values" to mapping.docValues,
            "store" to mapping.store,
        )
        return MappingTemplate(
            name,
            DynamicField.Simple(
                MappingKind.MAPPING,
                matchMappingType.fieldType,
                mappingParams
            ),
            MatchOptions(
                match = match,
                unmatch = unmatch,
                pathMatch = pathMatch,
                pathUnmatch = pathUnmatch,
                matchPattern = matchPattern,
                matchMappingType = matchMappingType,
                params = params,
            ),
        )
    }

    /**
     * Template with field type from mapping.
     */
    fun <V, T> template(
        name: String? = null,
        mapping: Field<V, T>,
        matchMappingType: MatchMappingType<*, *>? = null,
        match: String? = null,
        unmatch: String? = null,
        pathMatch: String? = null,
        pathUnmatch: String? = null,
        matchPattern: MatchPattern? = null,
        params: Params? = null,
    ): MappingTemplate<V, T, BoundField<V, T>> {
        return MappingTemplate(
            name,
            DynamicField.FromField(MappingKind.MAPPING, mapping),
            MatchOptions(
                match = match,
                unmatch = unmatch,
                pathMatch = pathMatch,
                pathUnmatch = pathUnmatch,
                matchPattern = matchPattern,
                matchMappingType = matchMappingType,
                params = params,
            ),
        )
    }

    /**
     * Template for a field with sub-fields.
     */
    fun <V, F: SubFields<V>> template(
        name: String? = null,
        mapping: SubFields.UnboundSubFields<V, F>,
        matchMappingType: MatchMappingType<*, *>? = null,
        match: String? = null,
        unmatch: String? = null,
        pathMatch: String? = null,
        pathUnmatch: String? = null,
        matchPattern: MatchPattern? = null,
        params: Params? = null,
    ): MappingTemplate<V, V, F> {
        return MappingTemplate(
            name,
            DynamicField.FromSubFields(mapping),
            MatchOptions(
                match = match,
                unmatch = unmatch,
                pathMatch = pathMatch,
                pathUnmatch = pathUnmatch,
                matchPattern = matchPattern,
                matchMappingType = matchMappingType,
                params = params,
            )
        )
    }

    /**
     * Template for a sub-document field.
     */
    fun <F: SubDocument> template(
        name: String? = null,
        mapping: SubDocument.UnboundSubDocument<F>,
        matchMappingType: MatchMappingType<*, *>? = null,
        match: String? = null,
        unmatch: String? = null,
        pathMatch: String? = null,
        pathUnmatch: String? = null,
        matchPattern: MatchPattern? = null,
        params: Params? = null,
    ): MappingTemplate<Any, Nothing, F> {
        return MappingTemplate(
            name,
            DynamicField.FromSubDocument(mapping),
            MatchOptions(
                match = match,
                unmatch = unmatch,
                pathMatch = pathMatch,
                pathUnmatch = pathUnmatch,
                matchPattern = matchPattern,
                matchMappingType = matchMappingType,
                params = params,
            )
        )
    }

    /**
     * Template for a runtime field with a specified type.
     */
    fun <V, T> template(
        name: String? = null,
        runtime: Runtime.Typed<V, T>,
        matchMappingType: MatchMappingType<*, *>? = null,
        match: String? = null,
        unmatch: String? = null,
        pathMatch: String? = null,
        pathUnmatch: String? = null,
        matchPattern: MatchPattern? = null,
        params: Params? = null,
    ): MappingTemplate<V, T, BoundField<V, T>> {
        return MappingTemplate(
            name,
            DynamicField.FromField(
                MappingKind.RUNTIME,
                runtime.field,
            ),
            MatchOptions(
                match = match,
                unmatch = unmatch,
                pathMatch = pathMatch,
                pathUnmatch = pathUnmatch,
                matchPattern = matchPattern,
                matchMappingType = matchMappingType,
                params = params,
            ),
        )
    }

    /**
     * Template for a runtime field.
     */
    fun template(
        name: String? = null,
        runtime: Runtime.Simple,
        match: String? = null,
        unmatch: String? = null,
        pathMatch: String? = null,
        pathUnmatch: String? = null,
        matchPattern: MatchPattern? = null,
        params: Params? = null,
    ): MappingTemplate<Any, Any, BoundField<Any, Any>> {
        return MappingTemplate(
            name,
            DynamicField.Simple(
                MappingKind.RUNTIME,
                AnyFieldType,
                runtime.params
            ),
            MatchOptions(
                match = match,
                unmatch = unmatch,
                pathMatch = pathMatch,
                pathUnmatch = pathUnmatch,
                matchPattern = matchPattern,
                params = params,
            ),
        )
    }

    /**
     * Template for a runtime field which type is detected by a JSON parser.
     */
    fun <V, T> template(
        name: String? = null,
        runtime: Runtime.Simple,
        matchMappingType: MatchMappingType<V, T>,
        match: String? = null,
        unmatch: String? = null,
        pathMatch: String? = null,
        pathUnmatch: String? = null,
        matchPattern: MatchPattern? = null,
        params: Params? = null,
    ): MappingTemplate<V, T, BoundField<V, T>> {
        return MappingTemplate(
            name,
            DynamicField.Simple(
                MappingKind.RUNTIME,
                matchMappingType.fieldType,
                runtime.params,
            ),
            MatchOptions(
                match = match,
                unmatch = unmatch,
                pathMatch = pathMatch,
                pathUnmatch = pathUnmatch,
                matchPattern = matchPattern,
                matchMappingType = matchMappingType,
                params = params,
            ),
        )
    }

    class Mapping(
        val index: Boolean? = null,
        val docValues: Boolean? = null,
        val store: Boolean? = null,
        val params: Params = Params(),
    )

    sealed class Runtime {
        class Simple(val params: Params) : Runtime()

        class Typed<V, T>(
            val field: Field<V, T>,
        ) : Runtime()

        companion object {
            operator fun invoke(params: Params = Params()): Simple {
                return Simple(params)
            }

            operator fun <V, T> invoke(field: Field<V, T>): Typed<V, T> {
                return Typed(field)
            }
        }
    }

    data class MatchOptions(
        val match: String? = null,
        val unmatch: String? = null,
        val pathMatch: String? = null,
        val pathUnmatch: String? = null,
        val matchPattern: MatchPattern? = null,
        val matchMappingType: MatchMappingType<*, *>? = null,
        val params: Params? = null,
    ) {
        private val matchRegex = match?.let { matchPattern.regexMatch(it) }
        private val unmatchRegex = unmatch?.let { matchPattern.regexMatch(it) }
        private val pathMatchRegex = pathMatch?.let { matchPattern.regexMatch(it) }
        private val pathUnmatchRegex = pathUnmatch?.let { matchPattern.regexMatch(it) }

        private fun MatchPattern?.regexMatch(match: String): Regex {
            return when (this) {
                MatchPattern.REGEX -> match.toRegex()
                MatchPattern.SIMPLE -> match.replace("*", ".*").toRegex()
                else -> match.replace("*", ".*").toRegex()
            }
        }

        fun matches(fieldPath: String): Boolean {
            val fieldName = fieldPath.split('.').last()
            if (matchRegex != null && !matchRegex.matches(fieldName)) {
                return false
            }
            if (unmatchRegex != null && unmatchRegex.matches(fieldName)) {
                return false
            }
            if (pathMatchRegex != null && !pathMatchRegex.matches(fieldName)) {
                return false
            }
            if (pathUnmatchRegex != null && pathUnmatchRegex.matches(fieldName)) {
                return false
            }
            return true
        }
    }

    class MappingTemplate<V, T, F>(
        val name: String? = null,
        val mapping: DynamicField<V, T, F>,
        val matchOptions: MatchOptions,
    ) {
        operator fun provideDelegate(
            thisRef: Document, prop: KProperty<*>
        ): ReadOnlyProperty<Document, BoundMappingTemplate<V, T, F>> {
            val mappingTemplate = BoundMappingTemplate(
                prop.name,
                mapping,
                matchOptions,
            )
            thisRef.addTemplate(mappingTemplate)
            return ReadOnlyProperty { _, _ ->
                mappingTemplate
            }
        }
    }

    enum class MappingKind : ToValue<String> {
        MAPPING, RUNTIME;

        override fun toValue() = name.lowercase()
    }

    sealed class DynamicField<V, T, F> {
        abstract fun field(fieldPath: String): F

        class Simple<V, T>(
            val mappingKind: MappingKind,
            val fieldType: FieldType<V, T>,
            val params: Params,
        ) : DynamicField<V, T, BoundField<V, T>>() {
            override fun field(fieldPath: String): BoundField<V, T> {
                return instantiateField(fieldPath, fieldType, params)
            }
        }

        class FromField<V, T>(
            val mappingKind: MappingKind,
            val field: Field<V, T>,
        ) : DynamicField<V, T, BoundField<V, T>>() {
            override fun field(fieldPath: String): BoundField<V, T> {
                return instantiateField(fieldPath, field.type, field.params)
            }
        }

        class FromSubFields<V, F: SubFields<V>>(
            val subFields: SubFields.UnboundSubFields<V, F>
        ) : DynamicField<V, V, F>() {
            override fun field(fieldPath: String): F {
                return subFields.subFieldsFactory(
                    instantiateField(fieldPath, subFields.unboundField.type, subFields.unboundField.params)
                )
            }
        }

        class FromSubDocument<F: SubDocument>(
            val subDocument: SubDocument.UnboundSubDocument<F>
        ) : DynamicField<Any, Nothing, F>() {
            override fun field(fieldPath: String): F {
                return subDocument.subDocumentFactory(
                    instantiateField(fieldPath, subDocument.type, subDocument.params)
                )
            }
        }
    }

    sealed class MatchMappingType<V, T> : ToValue<String> {
        internal abstract val fieldType: FieldType<V, T>

        override fun toValue() = fieldType.name

        object ANY : MatchMappingType<Any, Any>() {
            override val fieldType = AnyFieldType

            override fun toValue() = "*"
        }

        object BOOLEAN : MatchMappingType<Boolean, Boolean>() {
            override val fieldType = BooleanType
        }

        object LONG : MatchMappingType<Long, Long>() {
            override val fieldType = LongType
        }

        object DOUBLE : MatchMappingType<Double, Double>() {
            override val fieldType = DoubleType
        }

        object STRING : MatchMappingType<String, String>() {
            override val fieldType = TextType

            override fun toValue() = "string"
        }

        data class Date<V>(override val fieldType: FieldType<V, V>) : MatchMappingType<V, V>()

        data class Object<V: BaseDocSource>(override val fieldType: FieldType<V, V>) : MatchMappingType<V, V>()
    }

    enum class MatchPattern : ToValue<String> {
        SIMPLE, REGEX;

        override fun toValue() = name.lowercase()
    }
}

/**
 * Base class for describing a top level Elasticsearch document.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class Document(
    val options: MappingOptions = MappingOptions()
) : DynamicTemplates() {
    open val meta = MetaFields()
    val runtime = RuntimeFields()

    constructor(
        dynamic: Dynamic? = null,
        numericDetection: Boolean? = null,
        dateDetection: Boolean? = null,
        dynamicDateFormats: List<String>? = null,
    ) : this(
        MappingOptions(
            dynamic = dynamic,
            numericDetection = numericDetection,
            dateDetection = dateDetection,
            dynamicDateFormats = dynamicDateFormats,
        )
    )

    protected fun <V> runtime(name: String, type: FieldType<V, V>, script: Script): RuntimeField<V> {
        return RuntimeField(name, type, script)
    }

    protected fun <V> runtime(type: FieldType<V, V>, script: Script): RuntimeField<V> {
        return RuntimeField(null, type, script)
    }

    fun <V> runtimeField(name: String, type: FieldType<V, V>, script: Script): BoundRuntimeField<V> {
        return BoundRuntimeField(name, type, script, this)
    }

    class RuntimeField<V>(
        val name: String?,
        val type: FieldType<V, V>,
        val script: Script
    ) {
        operator fun provideDelegate(
            thisRef: Document, prop: KProperty<*>
        ): ReadOnlyProperty<Document, BoundRuntimeField<V>> {
            val field = BoundRuntimeField(
                name ?: prop.name,
                type,
                script,
                thisRef,
            )
            thisRef.addField(field)
            return ReadOnlyProperty { _, _ -> field }
        }
    }
}

fun mergeDocuments(vararg docs: Document): Document {
    require(docs.isNotEmpty()) {
        "Nothing to merge, document list is empty"
    }

    if (docs.size == 1) {
        return docs[0]
    }

    val expectedMeta = docs[0].meta
    val expectedOptions = docs[0].options
    val expectedDocName = docs[0]::class.simpleName
    for (doc in docs.slice(1 until docs.size)) {
        checkMetaFields(doc::class.simpleName, doc.meta, expectedDocName, expectedMeta)
        if (doc.options != expectedOptions) {
            throw IllegalArgumentException(
                "Expected mapping options: $expectedOptions but was ${doc.options}"
            )
        }
    }

    return object : Document() {
        override val meta = expectedMeta

        // override val dynamicTemplates = object : DynamicTemplates() {
        //     init {
        //         mergeTemplates(docs.map(Document::dynamicTemplates)).forEach(::addTemplate)
        //     }
        // }

        init {
            mergeTemplates(docs.toList()).forEach(::addTemplate)
            mergeFieldSets(docs.toList()).forEach(::addField)
        }
    }
}

private fun mergeTemplates(templates: List<Document>): List<BoundMappingTemplate<*, *, *>> {
    val mergedTemplates = mutableListOf<BoundMappingTemplate<*, *, *>>()
    val mergedTemplatesByName = mutableMapOf<String, Int>()
    for (dynamicTemplates in templates) {
        for (template in dynamicTemplates.getAllTemplates()) {
            val templateName = template.name
            val mergedTemplateIx = mergedTemplatesByName[templateName]
            if (mergedTemplateIx == null) {
                mergedTemplatesByName[templateName] = mergedTemplates.size
                mergedTemplates.add(template)
                continue
            }
            val expectedTemplate = mergedTemplates[mergedTemplateIx]

            checkTemplatesIdentical(template, expectedTemplate)
        }
    }

    return mergedTemplates
}

private fun mergeFieldSets(fieldSets: List<FieldSet>): List<MappingField<*>> {
    val mergedFields = mutableListOf<MappingField<*>>()
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
                checkMappingFieldsIdentical(field, expectedField)

                mergedFields[mergedFieldIx] = mergeSubFields(
                    secondSubFieldsField, firstSubFieldsField
                )

                continue
            }

            // Merge sub documents
            val subDocumentField = field as? SubDocumentField
            if (subDocumentField != null) {
                checkMappingFieldsIdentical(field, expectedField)

                val expectedSubDocument = expectedField as? SubDocumentField
                requireNotNull(expectedSubDocument) {
                    "$fieldName are differ by sub document presence"
                }

                mergedFields[mergedFieldIx] = mergeSubDocuments(expectedField, subDocumentField)

                continue
            }

            checkMappingFieldsIdentical(field, expectedField)
        }
    }

    return mergedFields
}

private fun mergeSubFields(first: SubFieldsField<*>?, second: SubFieldsField<*>?): SubFieldsField<*> {
    val firstSubFields = first?.subFields
    val secondSubFields = second?.subFields

    val templateField = when {
        firstSubFields != null -> firstSubFields.getBoundField()
        secondSubFields != null -> secondSubFields.getBoundField()
        else -> error("Unreachable")
    }

    // It is your responsibility to pass correct values when (de)-serializing
    @Suppress("UNCHECKED_CAST")
    val mergedSubFields = object : SubFields<Any?>(templateField as BoundField<Any?, Any?>) {
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

private fun mergeSubDocuments(first: SubDocumentField<*>, second: SubDocumentField<*>): SubDocumentField<*> {
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
    val expectedFieldNames = expectedMetaFields.getAllFields().map(MappingField<*>::getFieldName)
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
        checkMappingFieldsIdentical(metaField, expectedMetaField)
    }
}

private fun checkFieldTypesIdentical(
    title: String,
    fieldType: FieldType<*, *>,
    expectedType: FieldType<*, *>
) {
    require(fieldType::class == expectedType::class) {
        "$title have different field types: $fieldType != $expectedType"
    }
}

private fun checkMappingFieldsIdentical(
    field: MappingField<*>, expected: MappingField<*>,
) {
    val title = "'${field.getFieldName()}' fields"

    checkFieldTypesIdentical(
        title, field.getFieldType(), expected.getFieldType()
    )

    val mappingParams = field.getMappingParams()
    val expectedParams = expected.getMappingParams()
    require(mappingParams == expectedParams) {
        "$title have different field parameters: $mappingParams != $expectedParams"
    }
}


private fun checkTemplatesIdentical(
    template: BoundMappingTemplate<*, *, *>,
    expected: BoundMappingTemplate<*, *, *>,
) {
    require(template.matchOptions == expected.matchOptions) {
        "'${template.name}' templates have different match options: " +
                "${template.matchOptions} != ${expected.matchOptions}"
    }

    checkDynamicMappingsIdentical(template.name, template.mapping, expected.mapping)
}

private fun checkDynamicMappingsIdentical(
    templateName: String,
    mapping: DynamicTemplates.DynamicField<*, *, *>,
    expected: DynamicTemplates.DynamicField<*, *, *>
) {
    val title = "'$templateName' templates"
    require(mapping::class == expected::class) {
        "$title have different field mappings: " +
                "${mapping::class} != ${expected::class}"
    }
    when (expected) {
        is DynamicTemplates.DynamicField.Simple -> {
            val dynamicMapping = mapping as DynamicTemplates.DynamicField.Simple
            require(dynamicMapping.mappingKind == expected.mappingKind) {
                "$title have different mapping king: " + "" +
                        "${dynamicMapping.mappingKind} != ${expected.mappingKind}"
            }
            checkFieldTypesIdentical(title, dynamicMapping.fieldType, expected.fieldType)
            require(dynamicMapping.params == expected.params) {
                "$title have different parameters: " +
                        "${dynamicMapping.params} != ${expected.params}"
            }
        }
        is DynamicTemplates.DynamicField.FromField -> {
            val field = mapping as DynamicTemplates.DynamicField.FromField
            require(field.mappingKind == expected.mappingKind) {
                "$title have different mapping king: " + "" +
                        "${field.mappingKind} != ${expected.mappingKind}"
            }
            checkFieldsIdentical(title, field.field, expected.field)
        }
        is DynamicTemplates.DynamicField.FromSubFields -> {
            val subFields = (mapping as DynamicTemplates.DynamicField.FromSubFields).subFields
            val expectedSubFields = expected.subFields
            checkFieldsIdentical(title, subFields.unboundField, expectedSubFields.unboundField)
            require(subFields.subFieldsFactory == expectedSubFields.subFieldsFactory) {
                "$title have different sub-document factories: " +
                        "${subFields.subFieldsFactory} != ${expectedSubFields.subFieldsFactory}"
            }
        }
        is DynamicTemplates.DynamicField.FromSubDocument -> {
            val subDoc = (mapping as DynamicTemplates.DynamicField.FromSubDocument).subDocument
            val expectedSubDoc = expected.subDocument
            checkFieldTypesIdentical(
                title, subDoc.type, expectedSubDoc.type
            )
            require(subDoc.params == expectedSubDoc.params) {
                "$title have different parameters: " +
                        "${subDoc.params} != ${expectedSubDoc.params}"
            }
            require(subDoc.subDocumentFactory == expectedSubDoc.subDocumentFactory) {
                "$title have different sub-document factories: " +
                        "${subDoc.subDocumentFactory} != ${expectedSubDoc.subDocumentFactory}"
            }
        }
    }
}

private fun checkFieldsIdentical(
    title: String,
    field: FieldSet.Field<*, *>,
    expected: FieldSet.Field<*, *>
) {
    checkFieldTypesIdentical(title, field.type, expected.type)
    require(field.params == expected.params) {
        "$title have different parameters: " +
                "${field.params} != ${expected.params}"
    }
}
