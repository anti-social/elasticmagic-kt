package dev.evo.elasticmagic.doc

import dev.evo.elasticmagic.Params

class ValueSerializationException(value: Any?, cause: Throwable? = null) :
    IllegalArgumentException("Cannot serialize [$value]", cause)

fun serErr(v: Any?, cause: Throwable? = null): Nothing =
    throw ValueSerializationException(v, cause)

class ValueDeserializationException(value: Any, type: String, cause: Throwable? = null) :
    IllegalArgumentException("Cannot deserialize [$value] to [$type]", cause)

fun deErr(v: Any, type: String, cause: Throwable? = null): Nothing =
    throw ValueDeserializationException(v, type, cause)

interface FieldType<V, T> {
    val name: String

    fun serialize(v: V): Any {
        return v as Any
    }

    fun deserialize(
        v: Any,
        valueFactory: (() -> V)? = null
    ): V

    fun serializeTerm(v: T): Any {
        return v as Any
    }

    fun deserializeTerm(v: Any): T
}

abstract class SimpleFieldType<V> : FieldType<V, V> {
    override fun deserializeTerm(v: Any): V = deserialize(v)

}

abstract class NumberType<V: Number> : SimpleFieldType<V>()

object IntType : NumberType<Int>() {
    override val name = "integer"

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

object LongType : NumberType<Long>() {
    override val name = "long"

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

object FloatType : NumberType<Float>() {
    override val name = "float"

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

object DoubleType : NumberType<Double>() {
    override val name = "double"

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

object BooleanType : SimpleFieldType<Boolean>() {
    override val name = "boolean"

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

abstract class StringType : SimpleFieldType<String>() {
    override fun deserialize(v: Any, valueFactory: (() -> String)?): String {
        return v.toString()
    }
}

object KeywordType : StringType() {
    override val name = "keyword"
}

object TextType : StringType() {
    override val name = "text"
}

abstract class BaseDateTimeType<V> : SimpleFieldType<V>() {
    override val name = "date"

    protected abstract val dateTypeName: String

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
        val datetimeMatch = DATETIME_REGEX.matchEntire(v) ?: deErr(v, dateTypeName)
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

data class Range<V>(
    val gt: V? = null,
    val gte: V? = null,
    val lt: V? = null,
    val lte: V? = null,
)

@Suppress("UnnecessaryAbstractClass")
abstract class RangeType<V>(private val valueType: FieldType<V, V>) : FieldType<Range<V>, V> {
    override val name = "${valueType.name}_range"

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
            val gt = v["gt"]?.let(valueType::deserialize)
            val gte = v["gte"]?.let(valueType::deserialize)
            val lt = v["lt"]?.let(valueType::deserialize)
            val lte = v["lte"]?.let(valueType::deserialize)
            Range(gt = gt, gte = gte, lt = lt, lte = lte)
        }
        else -> deErr(v, "Map")
    }

    override fun serializeTerm(v: V): Any = valueType.serializeTerm(v)

    override fun deserializeTerm(v: Any): V = valueType.deserializeTerm(v)
}

object IntRangeType : RangeType<Int>(IntType)

object LongRangeType : RangeType<Long>(LongType)

object FloatRangeType : RangeType<Float>(FloatType)

object DoubleRangeType : RangeType<Double>(DoubleType)

data class Join(
    val name: String,
    val parent: String? = null,
)

object JoinType : FieldType<Join, String> {
    override val name = "join"

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

open class ObjectType<V: BaseDocSource> : FieldType<V, Nothing> {
    override val name = "object"

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

class NestedType<V: BaseDocSource> : ObjectType<V>() {
    override val name = "nested"
}

open class SourceType<V: BaseDocSource>(
    val type: FieldType<BaseDocSource, Nothing>,
    private val sourceFactory: () -> V
) : FieldType<V, Nothing> {
    override val name = type.name

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

class OptionalListType<V, T>(val type: FieldType<V, T>) : FieldType<List<V?>, T> {
    override val name get() = type.name

    override fun serialize(v: List<V?>): Any {
        return v.map { w ->
            if (w != null) {
                type.serialize(w)
            } else {
                null
            }
        }
    }

    override fun deserialize(v: Any, valueFactory: (() -> List<V?>)?): List<V?> {
        return when (v) {
            is List<*> -> {
                v.map {
                    if (it != null) {
                        type.deserialize(it)
                    } else {
                        null
                    }
                }
            }
            else -> listOf(type.deserialize(v))
        }
    }

    override fun serializeTerm(v: T): Any = serErr(v)

    override fun deserializeTerm(v: Any): T {
        throw IllegalStateException("Unreachable")
    }
}

class RequiredListType<V, T>(val type: FieldType<V, T>) : FieldType<List<V>, T> {
    override val name get() = type.name

    override fun serializeTerm(v: T): Any = serErr(v)

    override fun serialize(v: List<V>): Any {
        return v.map(type::serialize)
    }

    override fun deserialize(v: Any, valueFactory: (() -> List<V>)?): List<V> {
        return when (v) {
            is List<*> -> {
                v.map {
                    if (it != null) {
                        type.deserialize(it)
                    } else {
                        throw IllegalArgumentException("null is not allowed")
                    }
                }
            }
            else -> listOf(type.deserialize(v))
        }
    }

    override fun deserializeTerm(v: Any): T {
        throw IllegalStateException("Unreachable")
    }
}
