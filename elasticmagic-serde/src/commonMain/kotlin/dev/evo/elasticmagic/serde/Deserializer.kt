package dev.evo.elasticmagic.serde

class DeserializationException(message: String, cause: Exception? = null) : Exception(message, cause)

interface Deserializer {
    companion object {
        fun err(message: String): Nothing {
            throw DeserializationException(message)
        }
    }

    interface ObjectCtx {
        fun iterator(): ObjectIterator
        fun anyOrNull(name: String): Any?
        fun any(name: String): Any = anyOrNull(name) ?: err("no such key: [$name]")
        fun intOrNull(name: String): Int?
        fun int(name: String): Int = intOrNull(name) ?: err("not an integer: [$name]")
        fun longOrNull(name: String): Long?
        fun long(name: String): Long = longOrNull(name) ?: err("not a long: [$name]")
        fun floatOrNull(name: String): Float?
        fun float(name: String): Float = floatOrNull(name) ?: err("not a float: [$name]")
        fun doubleOrNull(name: String): Double?
        fun double(name: String): Double = doubleOrNull(name) ?: err("not a double: [$name]")
        fun booleanOrNull(name: String): Boolean?
        fun boolean(name: String): Boolean = booleanOrNull(name) ?: err("not a boolean: [$name]")
        fun stringOrNull(name: String): String?
        fun string(name: String): String = stringOrNull(name) ?: err("not a string: [$name]")
        fun objOrNull(name: String): ObjectCtx?
        fun obj(name: String): ObjectCtx = objOrNull(name) ?: err("not an object: [$name]")
        fun arrayOrNull(name: String): ArrayCtx?
        fun array(name: String): ArrayCtx = arrayOrNull(name) ?: err("not an array: [$name]")
    }

    interface ObjectIterator {
        fun hasNext(): Boolean
        fun key(): String
        fun anyOrNull(): Any?
        fun any(): Any {
            return anyOrNull() ?: err("null is not expected")
        }
        fun intOrNull(): Int?
        fun int(): Int {
            return intOrNull() ?: err("not an integer")
        }
        fun longOrNull(): Long?
        fun long(): Long {
            return longOrNull() ?: err("not a long")
        }
        fun floatOrNull(): Float?
        fun float(): Float {
            return floatOrNull() ?: err("not a float")
        }
        fun doubleOrNull(): Double?
        fun double(): Double {
            return doubleOrNull() ?: err("not a double")
        }
        fun booleanOrNull(): Boolean?
        fun boolean(): Boolean {
            return booleanOrNull() ?: err("not a boolean")
        }
        fun stringOrNull(): String?
        fun string(): String {
            return stringOrNull() ?: err("not a string")
        }
        fun objOrNull(): ObjectCtx?
        fun obj(): ObjectCtx {
            return objOrNull() ?: err("not an object")
        }
        fun arrayOrNull(): ArrayCtx?
        fun array(): ArrayCtx {
            return arrayOrNull() ?: err("not an array")
        }
    }

    interface ArrayCtx {
        fun iterator(): ArrayIterator
    }

    interface ArrayIterator {
        fun hasNext(): Boolean
        fun anyOrNull(): Any?
        fun any(): Any = anyOrNull() ?: err("missing value")
        fun intOrNull(): Int?
        fun int(): Int = intOrNull() ?: err("not a boolean")
        fun longOrNull(): Long?
        fun long(): Long = longOrNull() ?: err("not a long")
        fun floatOrNull(): Float?
        fun float(): Float = floatOrNull() ?: err("not a float")
        fun doubleOrNull(): Double?
        fun double(): Double = doubleOrNull() ?: err("not a double")
        fun booleanOrNull(): Boolean?
        fun boolean(): Boolean = booleanOrNull() ?: err("not a boolean")
        fun stringOrNull(): String?
        fun string(): String = stringOrNull() ?: err("not a string")
        fun objOrNull(): ObjectCtx?
        fun obj(): ObjectCtx = objOrNull() ?: err("not an object")
        fun arrayOrNull(): ArrayCtx?
        fun array(): ArrayCtx = arrayOrNull() ?: err("not an array")
    }

    fun objFromStringOrNull(data: String): ObjectCtx?
    fun objFromString(data: String): ObjectCtx {
        return objFromStringOrNull(data) ?: err("not an object")
    }
}

inline fun Deserializer.ObjectCtx.forEach(block: (String, Any?) -> Unit) {
    val iter = iterator()
    while (iter.hasNext()) {
        block(iter.key(), iter.anyOrNull())
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

inline fun Deserializer.ArrayCtx.forEach(block: (Any?) -> Unit) {
    val iter = iterator()
    while (iter.hasNext()) {
        block(iter.anyOrNull())
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
