package dev.evo.elasticmagic.compile

interface Serializer<OBJ, ARR> {
    interface ObjectCtx {
        fun field(name: String, value: Any?)
        fun array(name: String, block: ArrayCtx.() -> Unit)
        fun obj(name: String, block: ObjectCtx.() -> Unit)
    }

    interface ArrayCtx {
        fun value(value: Any?)
        fun array(block: ArrayCtx.() -> Unit)
        fun obj(block: ObjectCtx.() -> Unit)
    }

    fun obj(block: ObjectCtx.() -> Unit): OBJ
    fun array(block: ArrayCtx.() -> Unit): ARR
}

class StdSerializer(
    private val ignoreNullValues: Boolean = false,
    private val mapFactory: () -> MutableMap<String, Any?> = ::HashMap,
    private val arrayFactory: () -> MutableList<Any?> = ::ArrayList
) : Serializer<Map<String, Any?>, List<Any?>> {

    private inner class ObjectCtx(
        private val map: MutableMap<String, Any?>
    ) : Serializer.ObjectCtx {
        override fun field(name: String, value: Any?) {
            if (value != null || !ignoreNullValues) {
                map[name] = value
            }
        }

        override fun array(name: String, block: Serializer.ArrayCtx.() -> Unit) {
            val childArray = arrayFactory()
            ArrayCtx(childArray).block()
            field(name, childArray)
        }

        override fun obj(name: String, block: Serializer.ObjectCtx.() -> Unit) {
            val childMap = mapFactory()
            ObjectCtx(childMap).block()
            field(name, childMap)
        }
    }

    inner class ArrayCtx(
        private val array: MutableList<Any?>
    ) : Serializer.ArrayCtx {
        override fun value(value: Any?) {
            if (value != null || !ignoreNullValues) {
                array.add(value)
            }
        }

        override fun array(block: Serializer.ArrayCtx.() -> Unit) {
            val childArray = arrayFactory()
            ArrayCtx(childArray).block()
            value(childArray)
        }

        override fun obj(block: Serializer.ObjectCtx.() -> Unit) {
            val childMap = mapFactory()
            ObjectCtx(childMap).block()
            value(childMap)
        }
    }

    override fun obj(block: Serializer.ObjectCtx.() -> Unit): Map<String, Any?> {
        val map = mapFactory()
        ObjectCtx(map).block()
        return map
    }

    override fun array(block: Serializer.ArrayCtx.() -> Unit): List<Any?> {
        val list = arrayFactory()
        ArrayCtx(list).block()
        return list
    }
}
