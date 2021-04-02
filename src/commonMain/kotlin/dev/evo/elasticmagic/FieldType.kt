package dev.evo.elasticmagic

interface FieldType<out T, V> {
    val name: String

    fun serialize(v: V): Any {
        return v as Any
    }

    fun deserialize(
        v: Any,
        valueFactory: (() -> V)? = null
    ): V
}

abstract class NumberType<T, V: Number> : FieldType<T, V>

object IntType : NumberType<Nothing, Int>() {
    override val name = "integer"

    override fun deserialize(v: Any, valueFactory: (() -> Int)?) = when(v) {
        is Int -> v
        is Long -> v.toInt()
        is String -> v.toInt()
        else -> throw IllegalArgumentException("Unexpected field value: $v")
    }
}

object LongType : NumberType<Nothing, Long>() {
    override val name = "long"

    override fun deserialize(v: Any, valueFactory: (() -> Long)?) = when(v) {
        is Int -> v.toLong()
        is Long -> v
        is String -> v.toLong()
        else -> throw IllegalArgumentException()
    }
}

object FloatType : NumberType<Nothing, Float>() {
    override val name = "float"

    override fun deserialize(v: Any, valueFactory: (() -> Float)?) = when(v) {
        is Int -> v.toFloat()
        is Long -> v.toFloat()
        is Float -> v
        is Double -> v.toFloat()
        is String -> v.toFloat()
        else -> throw IllegalArgumentException()
    }
}

object DoubleType : NumberType<Nothing, Double>() {
    override val name = "double"

    override fun deserialize(v: Any, valueFactory: (() -> Double)?) = when(v) {
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is Float -> v.toDouble()
        is Double -> v
        is String -> v.toDouble()
        else -> throw IllegalArgumentException()
    }
}

object BooleanType : FieldType<Nothing, Boolean> {
    override val name = "boolean"

    override fun deserialize(v: Any, valueFactory: (() -> Boolean)?) = when(v) {
        is Boolean -> v
        is String -> v.toBoolean()
        else -> throw IllegalArgumentException()
    }
}

abstract class StringType : FieldType<Nothing, String> {
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

open class ObjectType<T: SubDocument> : FieldType<T, BaseSource> {
    override val name = "object"

    override fun serialize(v: BaseSource): Any {
        return v.getSource()
    }

    override fun deserialize(v: Any, valueFactory: (() -> BaseSource)?): BaseSource {
        requireNotNull(valueFactory) {
            "valueFactory argument must be passed"
        }
        return when (v) {
            is Map<*, *> -> {
                valueFactory().apply {
                    setSource(v)
                }
            }
            else -> throw IllegalArgumentException(
                "Expected Map class but was: ${v::class}"
            )
        }
    }
}

internal class SourceType<V: BaseSource>(
    val type: FieldType<*, BaseSource>,
    private val sourceFactory: () -> V
) : FieldType<Nothing, V> {
    override val name = type.name

    override fun serialize(v: V): Any {
        return type.serialize(v)
    }

    override fun deserialize(v: Any, valueFactory: (() -> V)?): V {
        @Suppress("UNCHECKED_CAST")
        return type.deserialize(v, sourceFactory) as V
    }
}

class NestedType<T: SubDocument> : ObjectType<T>() {
    override val name = "nested"
}

internal class SubFieldsType<T, V, F: SubFields<V>>(val type: FieldType<T, V>) : FieldType<F, V> {
    override val name = type.name

    override fun deserialize(v: Any, valueFactory: (() -> V)?): V {
        return type.deserialize(v, valueFactory)
    }
}

internal class OptionalListType<V>(val type: FieldType<*, V>) : FieldType<Nothing, List<V?>> {
    override val name = type.name

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

internal class RequiredListType<V>(val type: FieldType<*, V>) : FieldType<Nothing, List<V>> {
    override val name = type.name

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
