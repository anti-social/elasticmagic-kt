package dev.evo.elasticmagic.serde

interface Deserializer<OBJ> {
    interface ObjectCtx {
        fun iterator(): ObjectIterator
        fun intOrNull(name: String): Int?
        fun int(name: String): Int = intOrNull(name) ?: error("not an integer")
        fun longOrNull(name: String): Long?
        fun long(name: String): Long = longOrNull(name) ?: error("not a long")
        fun floatOrNull(name: String): Float?
        fun float(name: String): Float = floatOrNull(name) ?: error("not a float")
        fun doubleOrNull(name: String): Double?
        fun double(name: String): Double = doubleOrNull(name) ?: error("not a double")
        fun booleanOrNull(name: String): Boolean?
        fun boolean(name: String): Boolean = booleanOrNull(name) ?: error("not a boolean")
        fun stringOrNull(name: String): String?
        fun string(name: String): String = stringOrNull(name) ?: error("not a string")
        fun objOrNull(name: String): ObjectCtx?
        fun obj(name: String): ObjectCtx = objOrNull(name) ?: error("not an object")
        fun arrayOrNull(name: String): ArrayCtx?
        fun array(name: String): ArrayCtx = arrayOrNull(name) ?: error("not an array")
    }

    interface ObjectIterator {
        fun hasNext(): Boolean
        fun anyOrNull(): Pair<String, Any?>
        fun intOrNull(): Pair<String, Int?>
        fun int(): Pair<String, Int> {
            return intOrNull().let { (k, v) ->
                if (v != null) k to v else error("not an integer")
            }
        }
        fun longOrNull(): Pair<String, Long?>
        fun long(): Pair<String, Long> {
            return longOrNull().let { (k, v) ->
                if (v != null) k to v else error("not an integer")
            }
        }
        fun floatOrNull(): Pair<String, Float?>
        fun float(): Pair<String, Float> {
            return floatOrNull().let { (k, v) ->
                if (v != null) k to v else error("not an integer")
            }
        }
        fun doubleOrNull(): Pair<String, Double?>
        fun double(): Pair<String, Double> {
            return doubleOrNull().let { (k, v) ->
                if (v != null) k to v else error("not an integer")
            }
        }
        fun booleanOrNull(): Pair<String, Boolean?>
        fun boolean(): Pair<String, Boolean> {
            return booleanOrNull().let { (k, v) ->
                if (v != null) k to v else error("not an integer")
            }
        }
        fun stringOrNull(): Pair<String, String?>
        fun string(): Pair<String, String> {
            return stringOrNull().let { (k, v) ->
                if (v != null) k to v else error("not an integer")
            }
        }
        fun objOrNull(): Pair<String, ObjectCtx?>
        fun obj(): Pair<String, ObjectCtx> {
            return objOrNull().let { (k, v) ->
                if (v != null) k to v else error("not an integer")
            }
        }
        fun arrayOrNull(): Pair<String, ArrayCtx?>
        fun array(): Pair<String, ArrayCtx> {
            return arrayOrNull().let { (k, v) ->
                if (v != null) k to v else error("not an integer")
            }
        }
    }

    interface ArrayCtx {
        fun hasNext(): Boolean
        fun anyOrNull(): Any?
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

    fun obj(obj: OBJ): ObjectCtx
}

fun Deserializer.ObjectCtx.toMap(): Map<String, Any?> {
    val objIterator = iterator()
    val map = mutableMapOf<String, Any?>()
    while (objIterator.hasNext()) {
        val (key, v) = objIterator.anyOrNull()
        val value = when (v) {
            is Deserializer.ObjectCtx -> v.toMap()
            is Deserializer.ArrayCtx -> v.toList()
            else -> v
        }
        map[key] = value
    }
    return map
}

fun Deserializer.ArrayCtx.toList(): List<Any?> {
    val list = mutableListOf<Any?>()
    while (hasNext()) {
        val value = when (val v = anyOrNull()) {
            is Deserializer.ObjectCtx -> v.toMap()
            is Deserializer.ArrayCtx -> v.toList()
            else -> v
        }
        list.add(value)
    }
    return list
}
