package dev.evo.elasticmagic.serde

abstract class StdSerializer(
    private val objFactory: () -> StdSerializer.ObjectCtx,
    private val arrayFactory: () -> StdSerializer.ArrayCtx,
) : Serializer {

    abstract inner class ObjectCtx(
        protected val map: MutableMap<String, Any?>
    ) : Serializer.ObjectCtx {
        fun build(): Map<String, Any?> {
            return map.toMap()
        }

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
            map[name] = arrayFactory().apply(block).build()
        }

        override fun obj(name: String, block: Serializer.ObjectCtx.() -> Unit) {
            map[name] = objFactory().apply(block).build()
        }
    }

    open inner class ArrayCtx(
        protected val array: MutableList<Any?>
    ) : Serializer.ArrayCtx {
        fun build(): List<Any?> {
            return array.toList()
        }

        override fun value(v: Int?) {
            array.add(v)
        }

        override fun value(v: Long?) {
            array.add(v)
        }

        override fun value(v: Float?) {
            array.add(v)
        }

        override fun value(v: Double?) {
            array.add(v)
        }

        override fun value(v: Boolean?) {
            array.add(v)
        }

        override fun value(value: String?) {
            array.add(value)
        }

        override fun array(block: Serializer.ArrayCtx.() -> Unit) {
            array.add(arrayFactory().apply(block).build())
        }

        override fun obj(block: Serializer.ObjectCtx.() -> Unit) {
            array.add(objFactory().apply(block).build())
        }
    }

    override fun obj(block: Serializer.ObjectCtx.() -> Unit): Serializer.ObjectCtx {
        return objFactory().apply(block)
    }
}
