package dev.evo.elasticmagic

enum class Refresh : ToValue {
    TRUE, FALSE, WAIT_FOR;

    override fun toValue(): Any = name.toLowerCase()
}

enum class ConcurrencyControl(val value: String) : ToValue {
    VERSION("external"), VERSION_GTE("external_gte"), SEQ_NO("seq_no");

    override fun toValue(): Any = value
}

sealed class Action<S> {
    abstract val name: String
    abstract val meta: ActionMeta
    abstract val source: S?
    abstract val concurrencyControl: ConcurrencyControl?
}

data class DocSourceWithMeta(
    val meta: ActionMeta,
    val doc: BaseDocSource,
)

interface ActionMeta {
    val id: String?
    val routing: String?
    val version: Long?
    val seqNo: Long?
    val primaryTerm: Long?
}

sealed class ActionSource

// class IndexSource<S: BaseDocSource>(
//     doc: S,
// ) : ActionSource()

open class IndexAction<S: BaseDocSource>(
    override val meta: ActionMeta,
    override val source: S,
    override val concurrencyControl: ConcurrencyControl? = null,
    val pipeline: String? = null,
) : Action<S>() {
    override val name = "index"

    // override val source: S?
    //     get() =
}

class CreateAction<S: BaseDocSource>(
    meta: ActionMeta,
    source: S,
    pipeline: String? = null,
    concurrencyControl: ConcurrencyControl? = null,
) : IndexAction<S>(
    meta = meta,
    source = source,
    pipeline = pipeline,
    concurrencyControl = concurrencyControl,
) {
    override val name = "create"
}

class DeleteAction(
    override val meta: ActionMeta,
    override val concurrencyControl: ConcurrencyControl? = null,
) : Action<Nothing>() {
    override val name = "delete"
    override val source: Nothing? = null
}

sealed class UpdateSource<S: BaseDocSource>(
    val upsert: S?,
    val detectNoop: Boolean?,
) : ActionSource() {
    class WithDoc<S: BaseDocSource>(
        val doc: S,
        val docAsUpsert: Boolean? = null,
        upsert: S? = null,
        detectNoop: Boolean? = null,
    ) : UpdateSource<S>(
        upsert = upsert,
        detectNoop = detectNoop,
    )

    class WithScript<S: BaseDocSource>(
        val script: Script,
        val scriptedUpsert: Boolean? = null,
        upsert: S? = null,
        detectNoop: Boolean? = null,
    ) : UpdateSource<S>(
        upsert = upsert,
        detectNoop = detectNoop,
    )
}

class UpdateAction<S: BaseDocSource>(
    override val meta: ActionMeta,
    override val source: UpdateSource<S>,
    val retryOnConflict: Int? = null,
    override val concurrencyControl: ConcurrencyControl? = null,
) : Action<UpdateSource<S>>() {
    override val name = "update"

    // override val source: UpdateSource<S>
    //     get() = source
}
