package dev.evo.elasticmagic.types

import dev.evo.elasticmagic.Params
import dev.evo.elasticmagic.doc.BaseDocSource
import dev.evo.elasticmagic.doc.DynDocSource

import kotlin.reflect.KClass

/**
 * An exception for serialization errors.
 */
class ValueSerializationException(value: Any?, cause: Throwable? = null) :
    IllegalArgumentException("Cannot serialize [$value]", cause)

/**
 * A shortcut to throw [ValueSerializationException].
 */
fun serErr(v: Any?, cause: Throwable? = null): Nothing =
    throw ValueSerializationException(v, cause)

/**
 * An exception for deserialization errors.
 */
class ValueDeserializationException(value: Any, type: String, cause: Throwable? = null) :
    IllegalArgumentException("Cannot deserialize [$value] to [$type]", cause)

/**
 * A shortcut to throw [ValueDeserializationException].
 */
fun deErr(v: Any, type: String, cause: Throwable? = null): Nothing =
    throw ValueDeserializationException(v, type, cause)

/**
 * A field type is responsible for serialization/deserialization of
 * document and term values. Term values are used in queries, aggregations etc.
 *
 * @param V the type of field value.
 * @param T the type of term value.
 */
interface FieldType<V, T> {
    /**
     * Name of Elasticsearch mapping type.
     */
    val name: String

    /**
     * Term class is used inside [FieldSet.getFieldByName] method to check term type before casting.
     */
    val termType: KClass<*>

    /**
     * Serializes field value to Elasticsearch.
     *
     * @param v is a value from document source.
     */
    fun serialize(v: V): Any {
        return v as Any
    }

    /**
     * Deserializes field value from Elasticsearch.
     *
     * @param v is a value from Elasticsearch response.
     * @param valueFactory is a function that produces an instance of [V].
     * Required for [ObjectType.deserialize] to create a document source.
     */
    fun deserialize(
        v: Any,
        valueFactory: (() -> V)? = null
    ): V

    /**
     * Serializes term value to Elasticsearch.
     */
    fun serializeTerm(v: T): Any {
        return v as Any
    }

    /**
     * Deserializes term value from Elasticsearch.
     */
    fun deserializeTerm(v: Any): T
}

/**
 * Base field type for types with the same field and term value types.
 */
abstract class SimpleFieldType<V> : FieldType<V, V> {
    override fun serializeTerm(v: V): Any = serialize(v)

    override fun deserializeTerm(v: Any): V = deserialize(v)
}

/**
 * Base class for numeric field types.
 *
 * See: <https://www.elastic.co/guide/en/elasticsearch/reference/current/number.html>
 */
@Suppress("UnnecessaryAbstractClass")
abstract class NumberType<V: Number> : SimpleFieldType<V>()

/**
 * Integer field type represents signed integer value from [Byte.MIN_VALUE] to [Byte.MAX_VALUE].
 */
object ByteType : NumberType<Byte>() {
    override val name = "byte"
    override val termType = Byte::class

    override fun serialize(v: Byte) = v.toInt()

    override fun deserialize(v: Any, valueFactory: (() -> Byte)?): Byte {
        val w = when (v) {
            is Int -> v.toLong()
            else -> v
        }
        return when(w) {
            is Long -> {
                if (w > Byte.MAX_VALUE || w < Byte.MIN_VALUE) {
                    deErr(v, "Byte")
                }
                w.toByte()
            }
            is String -> try {
                w.toByte()
            } catch (ex: NumberFormatException) {
                deErr(w, "Byte", ex)
            }
            else -> deErr(w, "Byte")
        }
    }
}

/**
 * Integer field type represents signed integer value from [Short.MIN_VALUE] to [Short.MAX_VALUE].
 */
object ShortType : NumberType<Short>() {
    override val name = "short"
    override val termType = Short::class

    override fun serialize(v: Short) = v.toInt()

    override fun deserialize(v: Any, valueFactory: (() -> Short)?): Short {
        val w = when (v) {
            is Int -> v.toLong()
            else -> v
        }
        return when(w) {
            is Long -> {
                if (w > Short.MAX_VALUE || w < Short.MIN_VALUE) {
                    deErr(v, "Short")
                }
                w.toShort()
            }
            is String -> try {
                w.toShort()
            } catch (ex: NumberFormatException) {
                deErr(w, "Short", ex)
            }
            else -> deErr(w, "Short")
        }
    }
}

/**
 * Integer field type represents signed integer value from [Int.MIN_VALUE] to [Int.MAX_VALUE].
 */
object IntType : NumberType<Int>() {
    override val name = "integer"
    override val termType = Int::class

    override fun deserialize(v: Any, valueFactory: (() -> Int)?) = when(v) {
        is Int -> v
        is Long -> {
            if (v > Int.MAX_VALUE || v < Int.MIN_VALUE) {
                deErr(v, "Int")
            }
            v.toInt()
        }
        is String -> try {
            v.toInt()
        } catch (ex: NumberFormatException) {
            deErr(v, "Int", ex)
        }
        else -> deErr(v, "Int")
    }
}

/**
 * Long field type represents signed integer value from [Long.MIN_VALUE] to [Long.MAX_VALUE].
 */
object LongType : NumberType<Long>() {
    override val name = "long"
    override val termType = Long::class

    override fun deserialize(v: Any, valueFactory: (() -> Long)?) = when(v) {
        is Int -> v.toLong()
        is Long -> v
        is String -> try {
            v.toLong()
        } catch (ex: NumberFormatException) {
            deErr(v, "Long", ex)
        }
        else -> deErr(v, "Long")
    }
}

/**
 * Float field type represents single-precision floating point value.
 */
object FloatType : NumberType<Float>() {
    override val name = "float"
    override val termType = Float::class

    override fun deserialize(v: Any, valueFactory: (() -> Float)?) = when(v) {
        is Int -> v.toFloat()
        is Long -> v.toFloat()
        is Float -> v
        is Double -> v.toFloat()
        is String -> try {
            v.toFloat()
        } catch (ex: NumberFormatException) {
            deErr(v, "Float", ex)
        }
        else -> deErr(v, "Float")
    }
}

/**
 * Double field type represents double-precision floating point value.
 */
object DoubleType : NumberType<Double>() {
    override val name = "double"
    override val termType = Double::class

    override fun deserialize(v: Any, valueFactory: (() -> Double)?) = when(v) {
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is Float -> v.toDouble()
        is Double -> v
        is String -> try {
            v.toDouble()
        } catch (ex: NumberFormatException) {
            deErr(v, "Double", ex)
        }
        else -> deErr(v, "Double")
    }
}

/**
 * Represents boolean values.
 *
 * See: <https://www.elastic.co/guide/en/elasticsearch/reference/current/boolean.html>
 */
object BooleanType : SimpleFieldType<Boolean>() {
    override val name = "boolean"
    override val termType = Boolean::class

    override fun deserialize(v: Any, valueFactory: (() -> Boolean)?) = when(v) {
        is Boolean -> v
        "true" -> true
        "false" -> false
        else -> deErr(v, "Boolean")
    }

    override fun deserializeTerm(v: Any): Boolean = when (v) {
        is Boolean -> v
        "true" -> true
        "false" -> false
        is Int -> v != 0
        is Long -> v != 0L
        is Float -> v != 0.0F
        is Double -> v != 0.0
        else -> deErr(v, "Boolean")
    }
}

/**
 * Base class for string types.
 */
abstract class StringType : SimpleFieldType<String>() {
    override val termType = String::class

    override fun deserialize(v: Any, valueFactory: (() -> String)?): String {
        return v.toString()
    }
}

/**
 * Keyword field type is used for not-analyzed strings.
 *
 * See: <https://www.elastic.co/guide/en/elasticsearch/reference/current/keyword.html>
 */
object KeywordType : StringType() {
    override val name = "keyword"
}

/**
 * Text field type is used for full-text search.
 *
 * See: <https://www.elastic.co/guide/en/elasticsearch/reference/current/text.html>
 */
object TextType : StringType() {
    override val name = "text"
}

/**
 * Base class for date types. Core module doesn't provide any specific implementations.
 * One of implementation you can find inside `kotlinx-datetime` module.
 */
abstract class BaseDateTimeType<V> : SimpleFieldType<V>() {
    override val name = "date"

    companion object {
        private val DATETIME_REGEX = Regex(
            // Date
            "(\\d{4})(?:-(\\d{2}))?(?:-(\\d{2}))?" +
            "(?:T" +
                // Time
                "(\\d{2})?(?::(\\d{2}))?(?::(\\d{2}))?(?:\\.(\\d{1,9}))?" +
                // Timezone
                "(?:Z|([+-]\\d{2}(?::?\\d{2})?))?" +
            ")?"
        )

    }

    protected class DateTime(
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minute: Int,
        val second: Int,
        val ms: Int,
        val tz: String,
    )

    @Suppress("MagicNumber")
    protected fun parseDateWithOptionalTime(v: String): DateTime {
        val datetimeMatch = DATETIME_REGEX.matchEntire(v) ?: deErr(v, termType.simpleName ?: "<unknown>")
        val (year, month, day, hour, minute, second, msRaw, tz) = datetimeMatch.destructured
        val ms = when (msRaw.length) {
            0 -> msRaw
            in 1..2 -> msRaw.padEnd(3, '0')
            else -> msRaw.substring(0, 3)
        }
        return DateTime(
            year.toInt(),
            month.toIntIfNotEmpty(1),
            day.toIntIfNotEmpty(1),
            hour.toIntIfNotEmpty(0),
            minute.toIntIfNotEmpty(0),
            second.toIntIfNotEmpty(0),
            ms.toIntIfNotEmpty(0) * 1000_000,
            tz,
        )
    }

    private fun String.toIntIfNotEmpty(default: Int): Int {
        return if (isEmpty()) default else toInt()
    }
}

/**
 * A class that represents field value for range types.
 *
 * @param V is a specific type of range.
 *
 * See: <https://www.elastic.co/guide/en/elasticsearch/reference/current/range.html>
 */
data class Range<V>(
    val gt: V? = null,
    val gte: V? = null,
    val lt: V? = null,
    val lte: V? = null,
)

/**
 * A base class for range field types.
 *
 * @param V the range value type.
 * @param type is a field type corresponding to [V] type.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class RangeType<V>(private val type: FieldType<V, V>) : FieldType<Range<V>, V> {
    override val name = "${type.name}_range"
    override val termType = type.termType

    override fun serialize(v: Range<V>): Any {
        return Params(
            "gt" to v.gt,
            "gte" to v.gte,
            "lt" to v.lt,
            "lte" to v.lte,
        )
    }

    override fun deserialize(v: Any, valueFactory: (() -> Range<V>)?): Range<V> = when (v) {
        is Map<*, *> -> {
            val gt = v["gt"]?.let(type::deserialize)
            val gte = v["gte"]?.let(type::deserialize)
            val lt = v["lt"]?.let(type::deserialize)
            val lte = v["lte"]?.let(type::deserialize)
            Range(gt = gt, gte = gte, lt = lt, lte = lte)
        }
        is String -> {
            // Ranges can have 2 flavors:
            // - stored: "[0 : 10)"
            // - docvalue: "\x02\x80\x81\x81\x82"
            //   https://github.com/elastic/elasticsearch/blob/26c86871fc091900952e88e252c36fbfedf8d5fa/server/src/main/java/org/elasticsearch/index/mapper/BinaryRangeUtil.java#L107
            TODO("Implement deserializing from string")
        }
        else -> deErr(v, "Map")
    }

    override fun serializeTerm(v: V): Any = type.serializeTerm(v)

    override fun deserializeTerm(v: Any): V = type.deserializeTerm(v)
}

/**
 * A range of signed 32-bit integers.
 */
object IntRangeType : RangeType<Int>(IntType)

/**
 * A range of signed 64-bit integers.
 */
object LongRangeType : RangeType<Long>(LongType)

/**
 * A range of single-precision floating point values.
 */
object FloatRangeType : RangeType<Float>(FloatType)

/**
 * A range of double-precision floating point values.
 */
object DoubleRangeType : RangeType<Double>(DoubleType)

/**
 * An interface that provides field value for an enum.
 * We need this interface hierarchy to be able to make multiple [enum] extension functions
 * without signature clashing.
 */
fun interface EnumValue<V: Enum<V>, T> {
    fun get(v: V): T
}

/**
 * An interface that provides integer field value for an enum.
 */
fun interface IntEnumValue<V: Enum<V>> : EnumValue<V, Int>

/**
 * An interface that provides string field value for an enum.
 */
fun interface KeywordEnumValue<V: Enum<V>> : EnumValue<V, String>

/**
 * A field type that transforms enum variants to field values and vice verse.
 *
 * @param V the type of enum
 * @param enumValues an array of enum variants. Usually got by calling [enumValues] function.
 * @param fieldValue function interface that takes enum variant and returns field value.
 * @param type original field type
 * @param termType should be `V::class`
 */
class EnumFieldType<V: Enum<V>>(
    enumValues: Array<V>,
    private val fieldValue: EnumValue<V, *>,
    private val type: FieldType<*, *>,
    override val termType: KClass<*>,
) : SimpleFieldType<V>() {
    override val name = type.name

    private val valueToEnumValue = enumValues.associateBy(fieldValue::get)

    override fun serialize(v: V): Any {
        return fieldValue.get(v) ?: throw IllegalStateException("Unreachable")
    }

    override fun deserialize(v: Any, valueFactory: (() -> V)?): V {
        if (v::class == termType) {
            @Suppress("UNCHECKED_CAST")
            return v as V
        }
        return valueToEnumValue[type.deserialize(v)]
            ?: deErr(v, this::class.simpleName ?: "<unknown>")
    }
}

/**
 * Join field value.
 *
 * @param name is a name of the relation.
 * @param parent is an optional parent document id.
 */
data class Join(
    val name: String,
    val parent: String? = null,
)

/**
 * Join field type represents parent-child relations between documents in an index.
 *
 * See: <https://www.elastic.co/guide/en/elasticsearch/reference/current/parent-join.html>
 */
object JoinType : FieldType<Join, String> {
    override val name = "join"
    override val termType = String::class

    override fun serialize(v: Join): Any {
        if (v.parent != null) {
            return Params(
                "name" to v.name,
                "parent" to v.parent,
            )
        }
        return v.name
    }

    override fun deserialize(v: Any, valueFactory: (() -> Join)?): Join {
        return when (v) {
            is String -> Join(v, null)
            is Map<*, *> -> {
                val name = v["name"] as String
                val parent = v["parent"] as String?
                Join(name, parent)
            }
            else -> deErr(v, "Join")
        }
    }

    override fun deserializeTerm(v: Any): String {
        return KeywordType.deserializeTerm(v)
    }
}

/**
 * Object field type is used to represent sub-documents.
 *
 * @param V is a type of document source.
 *
 * See: <https://www.elastic.co/guide/en/elasticsearch/reference/current/object.html>
 */
open class ObjectType<V: BaseDocSource> : FieldType<V, Nothing> {
    override val name = "object"
    override val termType = Nothing::class

    override fun serializeTerm(v: Nothing): Nothing {
        throw IllegalStateException("Unreachable")
    }

    override fun serialize(v: V): Map<String, Any?> {
        return v.toSource()
    }

    override fun deserialize(v: Any, valueFactory: (() -> V)?): V {
        requireNotNull(valueFactory) {
            "valueFactory argument must be passed"
        }
        return when (v) {
            is Map<*, *> -> {
                valueFactory().apply {
                    fromSource(v)
                }
            }
            else -> throw IllegalArgumentException(
                "Expected Map class but was: ${v::class}"
            )
        }
    }

    override fun deserializeTerm(v: Any): Nothing {
        throw IllegalStateException("Unreachable")
    }
}

/**
 * Nested field type allows indexing array of objects as separate documents.
 *
 * See: <https://www.elastic.co/guide/en/elasticsearch/reference/current/nested.html>
 */
class NestedType<V: BaseDocSource> : ObjectType<V>() {
    override val name = "nested"
}

/**
 * Needed to bind specific document source to [ObjectType] at runtime.
 * Used by [dev.evo.elasticmagic.doc.DocSource].
 */
internal class SourceType<V: BaseDocSource>(
    val type: FieldType<BaseDocSource, Nothing>,
    private val sourceFactory: () -> V
) : FieldType<V, Nothing> {
    override val name = type.name
    override val termType = Nothing::class

    override fun serialize(v: V): Any {
        return type.serialize(v)
    }

    override fun deserialize(v: Any, valueFactory: (() -> V)?): V {
        @Suppress("UNCHECKED_CAST")
        return type.deserialize(v, sourceFactory) as V
    }

    override fun serializeTerm(v: Nothing): Nothing {
        throw IllegalStateException("Unreachable")
    }

    override fun deserializeTerm(v: Any): Nothing {
        throw IllegalStateException("Unreachable")
    }
}

/**
 * Serializes/deserializes [type] into list of optional values.
 * Used by [dev.evo.elasticmagic.doc.DocSource].
 */
internal class OptionalListType<V, T>(val type: FieldType<V, T>) : FieldType<MutableList<V?>, T> {
    override val name get() = type.name
    override val termType = type.termType

    override fun serialize(v: MutableList<V?>): Any {
        return v.map { w ->
            if (w != null) {
                type.serialize(w)
            } else {
                null
            }
        }
    }

    override fun deserialize(v: Any, valueFactory: (() -> MutableList<V?>)?): MutableList<V?> {
        return when (v) {
            is List<*> -> {
                v.map {
                    if (it != null) {
                        type.deserialize(it)
                    } else {
                        null
                    }
                }
                    .toMutableList()
            }
            else -> mutableListOf(type.deserialize(v))
        }
    }

    override fun serializeTerm(v: T): Any = serErr(v)

    override fun deserializeTerm(v: Any): T {
        throw IllegalStateException("Unreachable")
    }
}

/**
 * Serializes/deserializes [type] into list of required values.
 * Used by [dev.evo.elasticmagic.doc.DocSource].
 */
internal class RequiredListType<V, T>(val type: FieldType<V, T>) : FieldType<MutableList<V>, T> {
    override val name get() = type.name
    override val termType = type.termType

    override fun serializeTerm(v: T): Any = serErr(v)

    override fun serialize(v: MutableList<V>): Any {
        return v.map(type::serialize)
    }

    override fun deserialize(v: Any, valueFactory: (() -> MutableList<V>)?): MutableList<V> {
        return when (v) {
            is List<*> -> {
                v.map {
                    if (it != null) {
                        type.deserialize(it)
                    } else {
                        throw IllegalArgumentException("null is not allowed")
                    }
                }
                    .toMutableList()
            }
            else -> mutableListOf(type.deserialize(v))
        }
    }

    override fun deserializeTerm(v: Any): T {
        throw IllegalStateException("Unreachable")
    }
}

/**
 * Represents any field type.
 * Used by [DynDocSource] for fields navigation.
 */
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

/**
 * Serializes/deserializes [DynDocSource].
 */
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
