package dev.evo.elasticmagic.serde

class StdDeserializer : Deserializer<Map<String, Any?>> {
    companion object {
        fun coerceToInt(v: Any?): Int? {
            return when (v) {
                is Int -> v
                is Long -> v.toInt()
                else -> null
            }
        }

        fun coerceToLong(v: Any?): Long? {
            return when (v) {
                is Long -> v
                is Int -> v.toLong()
                else -> null
            }
        }

        fun coerceToFloat(v: Any?): Float? {
            return when (v) {
                is Float -> v
                is Double -> v.toFloat()
                is Int -> v.toFloat()
                is Long -> v.toFloat()
                else -> null
            }
        }

        fun coerceToDouble(v: Any?): Double? {
            return when (v) {
                is Double -> v
                is Float -> v.toDouble()
                is Int -> v.toDouble()
                is Long -> v.toDouble()
                else -> null
            }
        }

        fun coerceToBoolean(v: Any?): Boolean? {
            return v as? Boolean
        }

        fun coerceToString(v: Any?): String? {
            return v as? String
        }

        fun coerceToMap(v: Any?): Map<*, *>? {
            return v as? Map<*, *>
        }

        fun coerceToList(v: Any?): List<*>? {
            return v as? List<*>
        }
    }

    private class ObjectCtx(
        private val map: Map<*, *>,
    ) : Deserializer.ObjectCtx {
        override fun intOrNull(name: String): Int? = coerceToInt(map[name])

        override fun longOrNull(name: String): Long? = coerceToLong(map[name])

        override fun floatOrNull(name: String): Float? = coerceToFloat(map[name])

        override fun doubleOrNull(name: String): Double? = coerceToDouble(map[name])

        override fun booleanOrNull(name: String): Boolean? = coerceToBoolean(map[name])

        override fun stringOrNull(name: String): String? = coerceToString(map[name])

        override fun objOrNull(name: String): Deserializer.ObjectCtx? {
            return coerceToMap(map[name])?.let(::ObjectCtx)
        }

        override fun arrayOrNull(name: String): Deserializer.ArrayCtx? {
            return coerceToList(map[name])?.let(::ArrayCtx)
        }
    }

    private class ArrayCtx(
        private val iter: Iterator<Any?>,
    ) : Deserializer.ArrayCtx, Iterator<Any?> by iter {
        constructor(arr: List<Any?>) : this(arr.iterator())

        override fun intOrNull(): Int? = coerceToInt(iter.next())

        override fun longOrNull(): Long? = coerceToLong(iter.next())

        override fun floatOrNull(): Float? = coerceToFloat(iter.next())

        override fun doubleOrNull(): Double? = coerceToDouble(iter.next())

        override fun booleanOrNull(): Boolean? {
            return coerceToBoolean(iter.next())
        }

        override fun stringOrNull(): String? {
            return coerceToString(iter.next())
        }

        override fun objOrNull(): Deserializer.ObjectCtx? {
            return coerceToMap(iter.next())?.let(::ObjectCtx)
        }

        override fun arrayOrNull(): Deserializer.ArrayCtx? {
            return coerceToList(iter.next())?.let(::ArrayCtx)
        }
    }

    override fun obj(obj: Map<String, Any?>): Deserializer.ObjectCtx {
        return ObjectCtx(obj)
    }
}
