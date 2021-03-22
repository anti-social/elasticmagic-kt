package dev.evo.elasticmagic

interface FieldType<T> {
    val name: String

    fun deserialize(v: Any, valueFactory: (() -> T)? = null): T
}

abstract class NumberType<T: Number> : FieldType<T>

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

object BooleanType : FieldType<Boolean> {
    override val name = "boolean"

    override fun deserialize(v: Any, valueFactory: (() -> Boolean)?) = when(v) {
        is Boolean -> v
        is String -> v.toBoolean()
        else -> throw IllegalArgumentException()
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

open class ObjectType<T: BaseSource> : FieldType<T> {
    override val name = "object"

    override fun deserialize(v: Any, valueFactory: (() -> T)?): T {
        requireNotNull(valueFactory) {
            "valueFactory argument must be passed"
        }
        return when (v) {
            is Map<*, *> -> {
                valueFactory().apply {
                    setSource(v)
                }
            }
            else -> throw IllegalArgumentException()
        }
    }
}

class NestedType<T: BaseSource> : ObjectType<T>() {
    override val name = "nested"
}
