package dev.evo.elasticmagic.serde

abstract class StdDeserializer : Deserializer<Map<String, Any?>> {
    companion object {
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
            return when (v) {
                is Int -> v
                is Long -> v.toInt()
                else -> null
            }
        }

        private fun coerceToLong(v: Any?): Long? {
            return when (v) {
                is Long -> v
                is Int -> v.toLong()
                else -> null
            }
        }

        private fun coerceToFloat(v: Any?): Float? {
            return when (v) {
                is Float -> v
                is Double -> v.toFloat()
                is Int -> v.toFloat()
                is Long -> v.toFloat()
                else -> null
            }
        }

        private fun coerceToDouble(v: Any?): Double? {
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

    protected class ObjectCtx(
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

    protected class ObjectIterator(
        val iter: Iterator<Map.Entry<*, *>>,
    ) : Deserializer.ObjectIterator {
        private var currentyEntry: Map.Entry<*, *>? = null

        override fun hasNext(): Boolean {
            return iter.hasNext().also {
                currentyEntry = if (it) iter.next() else null
            }
        }

        override fun anyOrNull(): Pair<String, Any?> {
            val (key, value) = currentyEntry!!
            return key as String to coerceToAny(value)
        }

        override fun intOrNull(): Pair<String, Int?> {
            val (key, value) = currentyEntry!!
            return key as String to coerceToInt(value)
        }

        override fun longOrNull(): Pair<String, Long?> {
            val (key, value) = currentyEntry!!
            return key as String to coerceToLong(value)
        }

        override fun floatOrNull(): Pair<String, Float?> {
            val (key, value) = currentyEntry!!
            return key as String to coerceToFloat(value)
        }

        override fun doubleOrNull(): Pair<String, Double?> {
            val (key, value) = currentyEntry!!
            return key as String to coerceToDouble(value)
        }

        override fun booleanOrNull(): Pair<String, Boolean?> {
            val (key, value) = currentyEntry!!
            return key as String to coerceToBoolean(value)
        }

        override fun stringOrNull(): Pair<String, String?> {
            val (key, value) = currentyEntry!!
            return key as String to coerceToString(value)
        }

        override fun objOrNull(): Pair<String, Deserializer.ObjectCtx?> {
            val (key, value) = currentyEntry!!
            return key as String to coerceToMap(value)?.let(StdDeserializer::ObjectCtx)
        }

        override fun arrayOrNull(): Pair<String, Deserializer.ArrayCtx?> {
            val (key, value) = currentyEntry!!
            return key as String to coerceToList(value)?.let(StdDeserializer::ArrayCtx)
        }
    }

    protected class ArrayCtx(
        private val iter: Iterator<Any?>,
    ) : Deserializer.ArrayCtx {
        private var currentValue: Any? = null

        constructor(arr: List<Any?>) : this(arr.iterator())

        override fun hasNext(): Boolean {
            return iter.hasNext().also {
                currentValue = if (it) iter.next() else null
            }
        }

        override fun anyOrNull(): Any? = coerceToAny(currentValue!!)

        override fun intOrNull(): Int? = coerceToInt(currentValue!!)

        override fun longOrNull(): Long? = coerceToLong(currentValue!!)

        override fun floatOrNull(): Float? = coerceToFloat(currentValue!!)

        override fun doubleOrNull(): Double? = coerceToDouble(currentValue!!)

        override fun booleanOrNull(): Boolean? = coerceToBoolean(currentValue!!)

        override fun stringOrNull(): String? = coerceToString(currentValue!!)

        override fun objOrNull(): Deserializer.ObjectCtx? {
            return coerceToMap(currentValue!!)?.let(StdDeserializer::ObjectCtx)
        }

        override fun arrayOrNull(): Deserializer.ArrayCtx? {
            return coerceToList(currentValue!!)?.let(StdDeserializer::ArrayCtx)
        }
    }

    override fun wrapObj(obj: Map<String, Any?>): Deserializer.ObjectCtx {
        return ObjectCtx(obj)
    }
}
