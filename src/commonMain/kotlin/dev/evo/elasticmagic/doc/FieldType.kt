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

interface FieldType<V> {
    val name: String

    fun serializeTerm(v: Any?): Any?

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

    override fun serializeTerm(v: Any?): Any = when (v) {
        is Int -> v
        is Long -> {
            if (v in Int.MIN_VALUE..Int.MAX_VALUE) {
                v
            } else {
                serErr(v)
            }
        }
        else -> serErr(v)
    }

    override fun deserialize(v: Any, valueFactory: (() -> Int)?) = when(v) {
        is Int -> v
        is Long -> v.toInt()
        is String -> v.toInt()
        else -> throw ValueDeserializationException(v, "Int")
    }
}

object LongType : NumberType<Long>() {
    override val name = "long"

    override fun serializeTerm(v: Any?): Any = when (v) {
        is Long -> v
        is Int -> v
        else -> serErr(v)
    }

    override fun deserialize(v: Any, valueFactory: (() -> Long)?) = when(v) {
        is Int -> v.toLong()
        is Long -> v
        is String -> v.toLong()
        else -> throw ValueDeserializationException(v, "Long")
    }
}

object FloatType : NumberType<Float>() {
    override val name = "float"

    override fun serializeTerm(v: Any?): Any = when (v) {
        is Float -> v
        is Number -> v
        else -> serErr(v)
    }

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

    override fun serializeTerm(v: Any?): Any = when (v) {
        is Double -> v
        is Number -> v
        else -> serErr(v)
    }

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

    override fun serializeTerm(v: Any?): Any = v as? Boolean ?: serErr(v)

    override fun deserialize(v: Any, valueFactory: (() -> Boolean)?) = when(v) {
        is Boolean -> v
        is String -> v.toBoolean()
        else -> throw ValueDeserializationException(v, "Boolean")
    }
}

abstract class StringType : FieldType<String> {
    override fun serializeTerm(v: Any?): Any = when (v) {
        is CharSequence -> v
        is Number -> v
        is Boolean -> v
        else -> serErr(v)
    }

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

data class Join(
    val name: String,
    val parent: String? = null,
)

object JoinType : FieldType<Join> {
    override val name = "join"

    override fun serializeTerm(v: Any?): Any = KeywordType.serializeTerm(v)

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

    // TODO: Find out type safe solution
    override fun serializeTerm(v: Any?): Any = serErr(v)

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

    override fun serializeTerm(v: Any?): Any = serErr(v)

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

    override fun serializeTerm(v: Any?): Any = serErr(v)

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

    override fun serializeTerm(v: Any?): Any = serErr(v)

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
