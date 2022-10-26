package dev.evo.elasticmagic.serde

class DeserializationException(message: String, cause: Exception) : Exception(message, cause)

interface Deserializer {
    interface ObjectCtx {
        fun iterator(): ObjectIterator
        fun anyOrNull(name: String): Any?
        fun any(name: String): Any = anyOrNull(name) ?: error("no such key: [$name]")
        fun intOrNull(name: String): Int?
        fun int(name: String): Int = intOrNull(name) ?: error("not an integer: [$name]")
        fun longOrNull(name: String): Long?
        fun long(name: String): Long = longOrNull(name) ?: error("not a long: [$name]")
        fun floatOrNull(name: String): Float?
        fun float(name: String): Float = floatOrNull(name) ?: error("not a float: [$name]")
        fun doubleOrNull(name: String): Double?
        fun double(name: String): Double = doubleOrNull(name) ?: error("not a double: [$name]")
        fun booleanOrNull(name: String): Boolean?
        fun boolean(name: String): Boolean = booleanOrNull(name) ?: error("not a boolean: [$name]")
        fun stringOrNull(name: String): String?
        fun string(name: String): String = stringOrNull(name) ?: error("not a string: [$name]")
        fun objOrNull(name: String): ObjectCtx?
        fun obj(name: String): ObjectCtx = objOrNull(name) ?: error("not an object: [$name]")
        fun arrayOrNull(name: String): ArrayCtx?
        fun array(name: String): ArrayCtx = arrayOrNull(name) ?: error("not an array: [$name]")
    }

    interface ObjectIterator {
        fun hasNext(): Boolean
        fun key(): String
        fun anyOrNull(): Any?
        fun any(): Any {
            return anyOrNull() ?: error("null is not expected")
        }
        fun intOrNull(): Int?
        fun int(): Int {
            return intOrNull() ?: error("not an integer")
        }
        fun longOrNull(): Long?
        fun long(): Long {
            return longOrNull() ?: error("not a long")
        }
        fun floatOrNull(): Float?
        fun float(): Float {
            return floatOrNull() ?: error("not a float")
        }
        fun doubleOrNull(): Double?
        fun double(): Double {
            return doubleOrNull() ?: error("not a double")
        }
        fun booleanOrNull(): Boolean?
        fun boolean(): Boolean {
            return booleanOrNull() ?: error("not a boolean")
        }
        fun stringOrNull(): String?
        fun string(): String {
            return stringOrNull() ?: error("not a string")
        }
        fun objOrNull(): ObjectCtx?
        fun obj(): ObjectCtx {
            return objOrNull() ?: error("not an object")
        }
        fun arrayOrNull(): ArrayCtx?
        fun array(): ArrayCtx {
            return arrayOrNull() ?: error("not an array")
        }
    }

    interface ArrayCtx {
        fun iterator(): ArrayIterator
    }

    interface ArrayIterator {
        fun hasNext(): Boolean
        fun anyOrNull(): Any?
        fun any(): Any = anyOrNull() ?: error("missing value")
        fun intOrNull(): Int?
        fun int(): Int = intOrNull() ?: error("not a boolean")
        fun longOrNull(): Long?
        fun long(): Long = longOrNull() ?: error("not a long")
        fun floatOrNull(): Float?
        fun float(): Float = floatOrNull() ?: error("not a float")
        fun doubleOrNull(): Double?
        fun double(): Double = doubleOrNull() ?: error("not a double")
        fun booleanOrNull(): Boolean?
        fun boolean(): Boolean = booleanOrNull() ?: error("not a boolean")
        fun stringOrNull(): String?
        fun string(): String = stringOrNull() ?: error("not a string")
        fun objOrNull(): ObjectCtx?
        fun obj(): ObjectCtx = objOrNull() ?: error("not an object")
        fun arrayOrNull(): ArrayCtx?
        fun array(): ArrayCtx = arrayOrNull() ?: error("not an array")
    }

    fun objFromStringOrNull(data: String): ObjectCtx?
    fun objFromString(data: String): ObjectCtx {
        return objFromStringOrNull(data) ?: error("not an object")
    }
}

inline fun Deserializer.ObjectCtx.forEach(block: (String, Any) -> Unit) {
    val iter = iterator()
    while (iter.hasNext()) {
        block(iter.key(), iter.any())
    }
}

inline fun Deserializer.ObjectCtx.forEachObj(block: (String, Deserializer.ObjectCtx) -> Unit) {
    val iter = iterator()
    while (iter.hasNext()) {
        block(iter.key(), iter.obj())
    }
}

inline fun Deserializer.ObjectCtx.forEachArray(block: (String, Deserializer.ArrayCtx) -> Unit) {
    val iter = iterator()
    while (iter.hasNext()) {
        block(iter.key(), iter.array())
    }
}

fun Deserializer.ObjectCtx.toMap(): Map<String, Any?> {
    val iter = iterator()
    val map = mutableMapOf<String, Any?>()
    while (iter.hasNext()) {
        val value = when (val v = iter.anyOrNull()) {
            is Deserializer.ObjectCtx -> v.toMap()
            is Deserializer.ArrayCtx -> v.toList()
            else -> v
        }
        map[iter.key()] = value
    }
    return map
}

inline fun Deserializer.ArrayCtx.forEach(block: (Any) -> Unit) {
    val iter = iterator()
    while (iter.hasNext()) {
        block(iter.any())
    }
}

inline fun Deserializer.ArrayCtx.forEachObj(block: (Deserializer.ObjectCtx) -> Unit) {
    val iter = iterator()
    while (iter.hasNext()) {
        block(iter.obj())
    }
}

fun Deserializer.ArrayCtx.toList(): List<Any?> {
    val list = mutableListOf<Any?>()
    val iter = iterator()
    while (iter.hasNext()) {
        val value = when (val v = iter.anyOrNull()) {
            is Deserializer.ObjectCtx -> v.toMap()
            is Deserializer.ArrayCtx -> v.toList()
            else -> v
        }
        list.add(value)
    }
    return list
}
