package dev.evo.elasticmagic.serde

import kotlin.jvm.JvmInline

@Suppress("UnnecessaryAbstractClass")
abstract class StdDeserializer : Deserializer {
    companion object {
        // We cannot distinguish double from int on JS platform
        private val isJs = platform == Platform.JS

        private fun coerceToAny(v: Any?): Any? {
            return when (v) {
                null -> null
                is Int -> v
                is Long -> v
                is Float -> v
                is Double -> v
                is Boolean -> v
                is String -> v
                is Map<*, *> -> ObjectCtx(v)
                is List<*> -> ArrayCtx(v)
                else -> throw IllegalArgumentException(
                    "Non-deserializable type: ${v::class}"
                )
            }
        }

        private fun coerceToInt(v: Any?): Int? {
            if (isJs && v is Number) {
                return v.toInt()
            }
            return when (v) {
                is Int -> v
                is Long -> v.toInt()
                else -> null
            }
        }

        private fun coerceToLong(v: Any?): Long? {
            if (isJs && v is Number) {
                return v.toLong()
            }
            return when (v) {
                is Long -> v
                is Int -> v.toLong()
                else -> null
            }
        }

        private fun coerceToFloat(v: Any?): Float? {
            if (isJs && v is Number) {
                return v.toFloat()
            }
            return when (v) {
                is Float -> v
                is Double -> v.toFloat()
                is Int -> v.toFloat()
                is Long -> v.toFloat()
                else -> null
            }
        }

        private fun coerceToDouble(v: Any?): Double? {
            if (isJs && v is Number) {
                return v.toDouble()
            }
            return when (v) {
                is Double -> v
                is Float -> v.toDouble()
                is Int -> v.toDouble()
                is Long -> v.toDouble()
                else -> null
            }
        }

        private fun coerceToBoolean(v: Any?): Boolean? {
            return v as? Boolean
        }

        private fun coerceToString(v: Any?): String? {
            return v as? String
        }

        private fun coerceToMap(v: Any?): Map<*, *>? {
            return v as? Map<*, *>
        }

        private fun coerceToList(v: Any?): List<*>? {
            return v as? List<*>
        }
    }

    @JvmInline
    value class ObjectCtx(
        private val map: Map<*, *>,
    ) : Deserializer.ObjectCtx {
        override fun iterator(): ObjectIterator {
            return ObjectIterator(map.iterator())
        }

        override fun anyOrNull(name: String): Any? = coerceToAny(map[name])

        override fun intOrNull(name: String): Int? = coerceToInt(map[name])

        override fun longOrNull(name: String): Long? = coerceToLong(map[name])

        override fun floatOrNull(name: String): Float? = coerceToFloat(map[name])

        override fun doubleOrNull(name: String): Double? = coerceToDouble(map[name])

        override fun booleanOrNull(name: String): Boolean? = coerceToBoolean(map[name])

        override fun stringOrNull(name: String): String? = coerceToString(map[name])

        override fun objOrNull(name: String): Deserializer.ObjectCtx? {
            return coerceToMap(map[name])?.let(StdDeserializer::ObjectCtx)
        }

        override fun arrayOrNull(name: String): Deserializer.ArrayCtx? {
            return coerceToList(map[name])?.let(StdDeserializer::ArrayCtx)
        }
    }

    class ObjectIterator(
        val iter: Iterator<Map.Entry<*, *>>,
    ) : Deserializer.ObjectIterator {
        private var currentEntry: Map.Entry<*, *>? = null

        private fun getCurrentEntry(): Map.Entry<*, *> {
            return currentEntry ?: throw IllegalStateException("hasNext must be called first")
        }

        override fun hasNext(): Boolean {
            return iter.hasNext().also {
                currentEntry = if (it) iter.next() else null
            }
        }

        override fun key(): String = getCurrentEntry().key as String

        private fun value() = getCurrentEntry().value

        override fun anyOrNull(): Any? = coerceToAny(value())

        override fun intOrNull(): Int? = coerceToInt(value())

        override fun longOrNull(): Long? = coerceToLong(value())

        override fun floatOrNull(): Float? = coerceToFloat(value())

        override fun doubleOrNull(): Double? = coerceToDouble(value())

        override fun booleanOrNull(): Boolean? = coerceToBoolean(value())

        override fun stringOrNull(): String? = coerceToString(value())

        override fun objOrNull(): Deserializer.ObjectCtx? {
            return coerceToMap(value())?.let(StdDeserializer::ObjectCtx)
        }

        override fun arrayOrNull(): Deserializer.ArrayCtx? {
            return coerceToList(value())?.let(StdDeserializer::ArrayCtx)
        }
    }

    @JvmInline
    value class ArrayCtx(private val array: List<Any?>) : Deserializer.ArrayCtx {
        override fun iterator(): Deserializer.ArrayIterator {
            return ArrayIterator(array.iterator())
        }
    }

    class ArrayIterator(
        private val iter: Iterator<Any?>,
    ) : Deserializer.ArrayIterator {
        private var hasNextValue: Boolean = false
        private var currentValue: Any? = null

        override fun hasNext(): Boolean {
            hasNextValue = iter.hasNext()
            if (hasNextValue) {
                currentValue = iter.next()
            }
            return hasNextValue
        }

        private fun getCurrentValue(): Any? {
            if (!hasNextValue) {
                throw IllegalStateException("hasNext must be called first")
            }
            return currentValue
        }

        override fun anyOrNull(): Any? = coerceToAny(getCurrentValue())

        override fun intOrNull(): Int? = coerceToInt(getCurrentValue())

        override fun longOrNull(): Long? = coerceToLong(getCurrentValue())

        override fun floatOrNull(): Float? = coerceToFloat(getCurrentValue())

        override fun doubleOrNull(): Double? = coerceToDouble(getCurrentValue())

        override fun booleanOrNull(): Boolean? = coerceToBoolean(getCurrentValue())

        override fun stringOrNull(): String? = coerceToString(getCurrentValue())

        override fun objOrNull(): Deserializer.ObjectCtx? {
            return coerceToMap(getCurrentValue())?.let(StdDeserializer::ObjectCtx)
        }

        override fun arrayOrNull(): Deserializer.ArrayCtx? {
            return coerceToList(getCurrentValue())?.let(StdDeserializer::ArrayCtx)
        }
    }
}
