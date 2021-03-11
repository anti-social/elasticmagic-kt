package dev.evo.elasticmagic.serde

interface Deserializer<OBJ> {
    interface ObjectCtx {
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

    interface ArrayCtx {
        fun hasNext(): Boolean
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
