package dev.evo.elasticmagic.serde

interface Serializer {
    interface Ctx {
        fun serialize(): String
    }

    interface ObjectCtx : Ctx {
        fun field(name: String, value: Int?)
        fun fieldIfNotNull(name: String, value: Number?) {
            if (value != null) {
                field(name, value)
            }
        }
        fun field(name: String, value: Long?)
        fun fieldIfNotNull(name: String, value: Long?) {
            if (value != null) {
                field(name, value)
            }
        }
        fun field(name: String, value: Float?)
        fun fieldIfNotNull(name: String, value: Float?) {
            if (value != null) {
                field(name, value)
            }
        }
        fun field(name: String, value: Double?)
        fun fieldIfNotNull(name: String, value: Double?) {
            if (value != null) {
                field(name, value)
            }
        }
        fun field(name: String, value: Boolean?)
        fun fieldIfNotNull(name: String, value: Boolean?) {
            if (value != null) {
                field(name, value)
            }
        }
        fun field(name: String, value: String?)
        fun fieldIfNotNull(name: String, value: String?) {
            if (value != null) {
                field(name, value)
            }
        }
        fun field(name: String, value: Any?) {
            when (value) {
                is Int? -> field(name, value)
                is Long? -> field(name, value)
                is Float? -> field(name, value)
                is Double? -> field(name, value)
                is Boolean? -> field(name, value)
                is String? -> field(name, value)
                else -> error("Unsupported value type: ${value::class}")
            }
        }
        fun fieldIfNotNull(name: String, value: Any?) {
            if (value != null) {
                field(name, value)
            }
        }
        fun array(name: String, block: ArrayCtx.() -> Unit)
        fun array(name: String, values: List<Any?>) {
            array(name) {
                for (v in values) {
                    value(v)
                }
            }
        }
        fun arrayIfNotNull(name: String, values: List<Any?>?) {
            if (values != null) {
                array(name, values)
            }
        }
        fun obj(name: String, block: ObjectCtx.() -> Unit)
    }

    interface ArrayCtx : Ctx {
        fun value(v: Int?)
        fun valueIfNotNull(v: Int?) {
            if (v != null) {
                value(v)
            }
        }
        fun value(v: Long?)
        fun valueIfNotNull(v: Long?) {
            if (v != null) {
                value(v)
            }
        }
        fun value(v: Float?)
        fun valueIfNotNull(v: Float?) {
            if (v != null) {
                value(v)
            }
        }
        fun value(v: Double?)
        fun valueIfNotNull(v: Double?) {
            if (v != null) {
                value(v)
            }
        }
        fun value(v: Boolean?)
        fun valueIfNotNull(v: Boolean?) {
            if (v != null) {
                value(v)
            }
        }
        fun value(value: String?)
        fun valueIfNotNull(v: String?) {
            if (v != null) {
                value(v)
            }
        }
        fun value(v: Any?) {
            when (v) {
                is Int? -> value(v)
                is Long? -> value(v)
                is Float? -> value(v)
                is Double? -> value(v)
                is Boolean? -> value(v)
                is String? -> value(v)
                else -> error("Unsupported value type: ${v::class}")
            }
        }
        fun valueIfNotNull(v: Any?) {
            if (v != null) {
                value(v)
            }
        }
        fun array(block: ArrayCtx.() -> Unit)
        fun obj(block: ObjectCtx.() -> Unit)
    }

    fun obj(block: ObjectCtx.() -> Unit): ObjectCtx

    fun array(block: ArrayCtx.() -> Unit): ArrayCtx
}
