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

abstract class SimpleFieldType<V> : FieldType<Nothing, V>

abstract class NumberType<V: Number> : SimpleFieldType<V>()

object IntType : NumberType<Int>() {
    override val name = "integer"

    override fun deserialize(v: Any, valueFactory: (() -> Int)?) = when(v) {
        is Int -> v
        is Long -> v.toInt()
        is String -> v.toInt()
        else -> throw IllegalArgumentException("Unexpected field value: $v")
    }
}

object LongType : NumberType<Long>() {
    override val name = "long"

    override fun deserialize(v: Any, valueFactory: (() -> Long)?) = when(v) {
        is Int -> v.toLong()
        is Long -> v
        is String -> v.toLong()
        else -> throw IllegalArgumentException()
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
        else -> throw IllegalArgumentException()
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
        else -> throw IllegalArgumentException()
    }
}

object BooleanType : SimpleFieldType<Boolean>() {
    override val name = "boolean"

    override fun deserialize(v: Any, valueFactory: (() -> Boolean)?) = when(v) {
        is Boolean -> v
        is String -> v.toBoolean()
        else -> throw IllegalArgumentException()
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

internal class SubFieldsType<V>(val type: FieldType<*, V>) : SimpleFieldType<V>() {
    override val name = type.name

    override fun deserialize(v: Any, valueFactory: (() -> V)?): V {
        return type.deserialize(v, valueFactory)
    }
}

data class Join(
    val name: String,
    val parent: String? = null,
)

object JoinType : SimpleFieldType<Join>() {
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

open class ObjectType<out T: SubDocument, V: BaseDocSource> : FieldType<T, V> {
    override val name = "object"

    override fun serialize(v: V): Map<String, Any?> {
        return v.getSource()
    }

    override fun deserialize(v: Any, valueFactory: (() -> V)?): V {
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

class NestedType<out T: SubDocument, V: BaseDocSource> : ObjectType<T, V>() {
    override val name = "nested"
}

open class SourceType<V: BaseDocSource>(
    val type: FieldType<*, BaseDocSource>,
    private val sourceFactory: () -> V
) : SimpleFieldType<V>() {
    override val name = type.name

    override fun serialize(v: V): Any {
        return type.serialize(v)
    }

    override fun deserialize(v: Any, valueFactory: (() -> V)?): V {
        @Suppress("UNCHECKED_CAST")
        return type.deserialize(v, sourceFactory) as V
    }
}

class OptionalListType<V>(val type: FieldType<*, V>) : SimpleFieldType<List<V?>>() {
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

class RequiredListType<V>(val type: FieldType<*, V>) : SimpleFieldType<List<V>>() {
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
