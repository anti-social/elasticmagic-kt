package dev.evo.elasticmagic.doc

import dev.evo.elasticmagic.Params
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class ValueDeserializationException(value: Any, type: String) :
    IllegalArgumentException("Cannot deserialize $value to $type")

interface FieldType<V> {
    val name: String

    fun serialize(v: V): Any {
        return v as Any
    }

    fun deserialize(
        v: Any,
        valueFactory: (() -> V)? = null
    ): V
}

abstract class NumberType<V: Number> : FieldType<V>

object IntType : NumberType<Int>() {
    override val name = "integer"

    override fun deserialize(v: Any, valueFactory: (() -> Int)?) = when(v) {
        is Int -> v
        is Long -> v.toInt()
        is String -> v.toInt()
        else -> throw ValueDeserializationException(v, "Int")
    }
}

object LongType : NumberType<Long>() {
    override val name = "long"

    override fun deserialize(v: Any, valueFactory: (() -> Long)?) = when(v) {
        is Int -> v.toLong()
        is Long -> v
        is String -> v.toLong()
        else -> throw ValueDeserializationException(v, "Long")
    }
}

object FloatType : NumberType<Float>() {
    override val name = "float"

    override fun deserialize(v: Any, valueFactory: (() -> Float)?) = when(v) {
        is Int -> v.toFloat()
        is Long -> v.toFloat()
        is Float -> v
        is Double -> v.toFloat()
        is String -> v.toFloat()
        else -> throw ValueDeserializationException(v, "Float")
    }
}

object DoubleType : NumberType<Double>() {
    override val name = "double"

    override fun deserialize(v: Any, valueFactory: (() -> Double)?) = when(v) {
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is Float -> v.toDouble()
        is Double -> v
        is String -> v.toDouble()
        else -> throw ValueDeserializationException(v, "Double")
    }
}

object BooleanType : FieldType<Boolean> {
    override val name = "boolean"

    override fun deserialize(v: Any, valueFactory: (() -> Boolean)?) = when(v) {
        is Boolean -> v
        is String -> v.toBoolean()
        else -> throw ValueDeserializationException(v, "Boolean")
    }
}

abstract class StringType : FieldType<String> {
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

object DateTimeType : FieldType<LocalDateTime> {
    override val name = "date"

    @Suppress("MaxLineLength")
    private val DATETIME_REGEX =
        "(\\d{4})(?:-(\\d{2}))?(?:-(\\d{2}))?(?:T(\\d{2})?(?::(\\d{2}))?(?::(\\d{2}))?(?:\\.(\\d{1,9}))?(?:Z)?)?".toRegex()

    override fun serialize(v: LocalDateTime): Any {
        return v.toInstant(TimeZone.UTC).toString()
    }

    override fun deserialize(v: Any, valueFactory: (() -> LocalDateTime)?) = when (v) {
        is String -> {
            parseDateWithOptionalTime(v)
        }
        is Number -> {
            try {
                parseDateWithOptionalTime(v.toString())
            } catch (ex: ValueDeserializationException) {
                Instant.fromEpochMilliseconds(v.toLong()).toLocalDateTime(TimeZone.UTC)
            }
        }
        else -> throw ValueDeserializationException(v, "LocalDateTime")
    }

    @Suppress("MagicNumber")
    private fun parseDateWithOptionalTime(v: String): LocalDateTime {
        val datetimeMatch = DATETIME_REGEX.matchEntire(v)
            ?: throw ValueDeserializationException(v, "LocalDateTime")
        val (year, month, day, hour, minute, second, msRaw) = datetimeMatch.destructured
        val ms = when (msRaw.length) {
            0 -> msRaw
            in 1..2 -> msRaw.padEnd(3, '0')
            else -> msRaw.substring(0, 3)
        }
        return LocalDateTime(
            year.toInt(),
            month.toIntIfNotEmpty(1),
            day.toIntIfNotEmpty(1),
            hour.toIntIfNotEmpty(0),
            minute.toIntIfNotEmpty(0),
            second.toIntIfNotEmpty(0),
            ms.toIntIfNotEmpty(0) * 1000_000
        )
    }

    private fun String.toIntIfNotEmpty(default: Int): Int {
        return if (isEmpty()) default else toInt()
    }
}

data class Join(
    val name: String,
    val parent: String? = null,
)

object JoinType : FieldType<Join> {
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
            else -> throw IllegalArgumentException(
                "Join value must be String or Map but was: ${v::class}"
            )
        }
    }
}

open class ObjectType<V: BaseDocSource> : FieldType<V> {
    override val name = "object"

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
}

class NestedType<V: BaseDocSource> : ObjectType<V>() {
    override val name = "nested"
}

open class SourceType<V: BaseDocSource>(
    val type: FieldType<BaseDocSource>,
    private val sourceFactory: () -> V
) : FieldType<V> {
    override val name = type.name

    override fun serialize(v: V): Any {
        return type.serialize(v)
    }

    override fun deserialize(v: Any, valueFactory: (() -> V)?): V {
        @Suppress("UNCHECKED_CAST")
        return type.deserialize(v, sourceFactory) as V
    }
}

class OptionalListType<V>(val type: FieldType<V>) : FieldType<List<V?>> {
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
}

class RequiredListType<V>(val type: FieldType<V>) : FieldType<List<V>> {
    override val name get() = type.name

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
}