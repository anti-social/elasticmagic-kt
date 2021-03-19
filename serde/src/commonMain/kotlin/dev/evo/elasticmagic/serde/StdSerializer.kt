package dev.evo.elasticmagic.serde

abstract class StdSerializer(
    private val mapFactory: () -> MutableMap<String, Any?> = ::HashMap,
    private val arrayFactory: () -> MutableList<Any?> = ::ArrayList
) : Serializer<Map<String, Any?>> {

    protected inner class ObjectCtx(
        private val map: MutableMap<String, Any?>
    ) : Serializer.ObjectCtx {
        override fun field(name: String, value: Int?) {
            map[name] = value
        }

        override fun field(name: String, value: Long?) {
            map[name] = value
        }

        override fun field(name: String, value: Float?) {
            map[name] = value
        }

        override fun field(name: String, value: Double?) {
            map[name] = value
        }

        override fun field(name: String, value: Boolean?) {
            map[name] = value
        }

        override fun field(name: String, value: String?) {
            map[name] = value
        }

        override fun array(name: String, block: Serializer.ArrayCtx.() -> Unit) {
            val childArray = arrayFactory()
            ArrayCtx(childArray).block()
            map[name] = childArray
        }

        override fun obj(name: String, block: Serializer.ObjectCtx.() -> Unit) {
            val childMap = mapFactory()
            ObjectCtx(childMap).block()
            map[name] = childMap
        }
    }

    protected inner class ArrayCtx(
        private val array: MutableList<Any?>
    ) : Serializer.ArrayCtx {
        override fun value(value: Int?) {
            array.add(value)
        }

        override fun value(value: Long?) {
            array.add(value)
        }

        override fun value(value: Float?) {
            array.add(value)
        }

        override fun value(value: Double?) {
            array.add(value)
        }

        override fun value(value: Boolean?) {
            array.add(value)
        }

        override fun value(value: String?) {
            array.add(value)
        }

        override fun array(block: Serializer.ArrayCtx.() -> Unit) {
            val childArray = arrayFactory()
            ArrayCtx(childArray).block()
            array.add(childArray)
        }

        override fun obj(block: Serializer.ObjectCtx.() -> Unit) {
            val childMap = mapFactory()
            ObjectCtx(childMap).block()
            array.add(childMap)
        }
    }

    override fun buildObj(block: Serializer.ObjectCtx.() -> Unit): Map<String, Any?> {
        val map = mapFactory()
        ObjectCtx(map).block()
        return map
    }
}
