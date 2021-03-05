package dev.evo.elasticmagic

interface Type<T> {
    fun deserialize(v: Any): T
}

abstract class NumberType<T: Number> : Type<T>

object IntType : NumberType<Int>() {
    override fun deserialize(v: Any) = when(v) {
        is Int -> v
        is Long -> v.toInt()
        is String -> v.toInt()
        else -> throw IllegalArgumentException()
    }
}

object LongType : NumberType<Long>() {
    override fun deserialize(v: Any) = when(v) {
        is Int -> v.toLong()
        is Long -> v
        is String -> v.toLong()
        else -> throw IllegalArgumentException()
    }
}

object FloatType : NumberType<Float>() {
    override fun deserialize(v: Any) = when(v) {
        is Int -> v.toFloat()
        is Long -> v.toFloat()
        is Float -> v
        is Double -> v.toFloat()
        is String -> v.toFloat()
        else -> throw IllegalArgumentException()
    }
}

object DoubleType : NumberType<Double>() {
    override fun deserialize(v: Any) = when(v) {
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is Float -> v.toDouble()
        is Double -> v
        is String -> v.toDouble()
        else -> throw IllegalArgumentException()
    }
}

object BooleanType : Type<Boolean> {
    override fun deserialize(v: Any) = when(v) {
        is Boolean -> v
        is String -> v.toBoolean()
        else -> throw IllegalArgumentException()
    }
}

abstract class StringType : Type<String> {
    override fun deserialize(v: Any): String {
        return v.toString()
    }
}

object KeywordType : StringType()

object TextType : StringType()
