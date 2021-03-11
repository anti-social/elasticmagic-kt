package dev.evo.elasticmagic.serde

interface Serializer<OBJ> {
    interface ObjectCtx {
        fun field(name: String, value: Int?)
        fun field(name: String, value: Long?)
        fun field(name: String, value: Float?)
        fun field(name: String, value: Double?)
        fun field(name: String, value: Boolean?)
        fun field(name: String, value: String?)
        fun field(name: String, value: Any?) {
            when (value) {
                is Int? -> field(name, value)
                is Long? -> field(name, value)
                is Float? -> field(name, value)
                is Double? -> field(name, value)
                is Boolean? -> field(name, value)
                is String? -> field(name, value)
                else -> error(
                    "Unsupported value type: ${if (value != null) value::class else null}"
                )
            }
        }
        fun array(name: String, block: ArrayCtx.() -> Unit)
        fun obj(name: String, block: ObjectCtx.() -> Unit)
    }

    interface ArrayCtx {
        fun value(value: Int?)
        fun value(value: Long?)
        fun value(value: Float?)
        fun value(value: Double?)
        fun value(value: Boolean?)
        fun value(value: String?)
        fun value(value: Any?) {
            when (value) {
                is Int? -> value(value)
                is Long? -> value(value)
                is Float? -> value(value)
                is Double? -> value(value)
                is Boolean? -> value(value)
                is String? -> value(value)
                else -> error(
                    "Unsupported value type: ${if (value != null) value::class else null}"
                )
            }
        }
        fun array(block: ArrayCtx.() -> Unit)
        fun obj(block: ObjectCtx.() -> Unit)
    }

    fun obj(block: ObjectCtx.() -> Unit): OBJ
}
