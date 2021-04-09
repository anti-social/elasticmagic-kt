package dev.evo.elasticmagic

sealed class Action {
    abstract val name: String
    abstract val concurrencyControl: ConcurrencyControl?

    enum class Refresh : ToValue {
        TRUE, FALSE, WAIT_FOR;

        override fun toValue(): Any = name.toLowerCase()
    }

    enum class ConcurrencyControl(val value: String) : ToValue {
        VERSION("external"), VERSION_GTE("external_gte"), SEQ_NO("seq_no");

        override fun toValue(): Any = value.toLowerCase()
    }

    abstract fun getActionMeta(): ActionMeta
    abstract fun getActionSource(): ActionSource?
}

class ActionMeta(
    val id: String? = null,
    val routing: String? = null,
    val version: Long? = null,
    val seqNo: Long? = null,
    val primaryTerm: Long? = null,
)

sealed class ActionSource

class IndexSource<S: BaseSource>(
    val meta: ActionMeta,
    val source: S,
) : ActionSource()

class IndexAction(
    val source: IndexSource<*>,
    val opType: OpType? = null,
    val pipeline: String? = null,
    override val concurrencyControl: ConcurrencyControl? = null,
) : Action() {
    override val name = "index"

    enum class OpType {
        INDEX, CREATE
    }

    override fun getActionMeta(): ActionMeta {
        return source.meta
    }

    override fun getActionSource(): IndexSource<*> {
        return source
    }
}

class CreateAction(
    val source: IndexSource<*>,
    val opType: OpType? = null,
    val pipeline: String? = null,
    override val concurrencyControl: ConcurrencyControl? = null,
) : Action() {
    override val name = "create"

    enum class OpType {
        INDEX, CREATE
    }

    override fun getActionMeta(): ActionMeta {
        return source.meta
    }

    override fun getActionSource(): IndexSource<*> {
        return source
    }
}

class DeleteAction(
    val meta: ActionMeta,
    override val concurrencyControl: ConcurrencyControl? = null,
) : Action() {
    override val name = "delete"

    override fun getActionMeta(): ActionMeta {
        return meta
    }

    override fun getActionSource(): ActionSource? {
        return null
    }
}

sealed class UpdateSource<S: BaseSource>(
    val meta: ActionMeta,
    val upsert: S?,
    val detectNoop: Boolean?,
) : ActionSource() {
    class WithScript<S: BaseSource>(
        meta: ActionMeta,
        val script: Script,
        val scriptedUpsert: Boolean? = null,
        upsert: S? = null,
        detectNoop: Boolean? = null,
    ) : UpdateSource<S>(
        meta,
        upsert = upsert,
        detectNoop = detectNoop,
    )

    class WithDoc<S: BaseSource>(
        meta: ActionMeta,
        val doc: IndexSource<S>,
        val docAsUpsert: Boolean? = null,
        upsert: S? = null,
        detectNoop: Boolean? = null,
    ) : UpdateSource<S>(
        meta,
        upsert = upsert,
        detectNoop = detectNoop,
    )
}

class UpdateAction(
    val source: UpdateSource<*>,
    val retryOnConflict: Int? = null,
    override val concurrencyControl: ConcurrencyControl? = null,
) : Action() {
    override val name = "update"

    override fun getActionMeta(): ActionMeta {
        return source.meta
    }

    override fun getActionSource(): UpdateSource<*> {
        return source
    }
}