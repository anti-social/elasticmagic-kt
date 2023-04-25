package dev.evo.elasticmagic.bulk

import dev.evo.elasticmagic.ToValue
import dev.evo.elasticmagic.doc.ToSource
import dev.evo.elasticmagic.query.Script


/**
 * Controls optimistic concurrency.
 *
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-index_.html#index-versioning>
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/optimistic-concurrency-control.html>
 */
enum class ConcurrencyControl(val value: String) : ToValue<String> {
    /**
     *
     * Corresponds `version_type` parameter to be equal to `external`. That means that only
     * new documents or documents with <b>greater</b> versions will be indexed.
     */
    VERSION("external"),

    /**
     * Corresponds `version_type` parameter to be equal to `external_gte`. That means than only
     * new documents or documents with <b>equal</b> or <b>greater</b> versions will be indexed.
     */
    VERSION_GTE("external_gte"),

    /**
     * Uses internal versioning that takes into account [ActionMeta.seqNo] and
     * [ActionMeta.primaryTerm] properties.
     */
    SEQ_NO("seq_no");

    override fun toValue() = value
}

/**
 * Base bulk action class.
 *
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html>
 */
sealed class Action<S> {
    /**
     * Name of the action.
     */
    abstract val name: String

    /**
     * Action's metadata.
     */
    abstract val meta: ActionMeta

    /**
     * Optional source.
     */
    abstract val source: S?

    /**
     * Concurrency control.
     */
    abstract val concurrencyControl: ConcurrencyControl?
}

/**
 * Bulk action metadata.
 */
interface ActionMeta {
    val id: String?
    val routing: String?
    val version: Long?
    val seqNo: Long?
    val primaryTerm: Long?

    companion object {
        operator fun invoke(
            id: String? = null,
            routing: String? = null,
            version: Long? = null,
            seqNo: Long? = null,
            primaryTerm: Long? = null,
        ): ActionMeta {
            return object : ActionMeta {
                override val id: String? = id
                override val routing: String? = routing
                override val version: Long? = version
                override val seqNo: Long? = seqNo
                override val primaryTerm: Long? = primaryTerm
            }
        }
    }
}

/**
 * Bulk action metadata with mandatory [id].
 */
interface IdActionMeta : ActionMeta {
    override val id: String

    companion object {
        operator fun invoke(
            id: String,
            routing: String? = null,
            version: Long? = null,
            seqNo: Long? = null,
            primaryTerm: Long? = null,
        ): IdActionMeta {
            return object : IdActionMeta {
                override val id: String = id
                override val routing: String? = routing
                override val version: Long? = version
                override val seqNo: Long? = seqNo
                override val primaryTerm: Long? = primaryTerm
            }
        }
    }
}

/**
 * Indexes a [source] document to an index.
 * If the document already exists, the action overwrites the document and increments its version.
 *
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-index_.html>
 */
open class IndexAction<S: ToSource>(
    override val meta: ActionMeta,
    override val source: S,
    override val concurrencyControl: ConcurrencyControl? = null,
    val pipeline: String? = null,
) : Action<S>() {
    override val name = "index"
}

/**
 * Creates new [source] document in an index.
 * If the document already exists, the action fails.
 *
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-index_.html>
 */
class CreateAction<S: ToSource>(
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

/**
 * Removes a document specified by an [meta]'s `id` from an index.
 *
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete.html>
 */
class DeleteAction(
    override val meta: IdActionMeta,
    override val concurrencyControl: ConcurrencyControl? = null,
) : Action<Nothing>() {
    override val name = "delete"
    override val source: Nothing? = null
}

/**
 * Updates a document specified by an [meta]'s `id`.
 *
 * @see <https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-update.html>
 */
class UpdateAction<S: ToSource>(
    override val meta: IdActionMeta,
    override val source: UpdateSource<S>,
    val retryOnConflict: Int? = null,
    override val concurrencyControl: ConcurrencyControl? = null,
) : Action<UpdateSource<S>>() {
    override val name = "update"

    // override val source: UpdateSource<S>
    //     get() = source
}

/**
 * Represents an update action's source. Has 2 flavors:
 * - [WithDoc] makes a partial update of the existing document.
 * - [WithScript] runs the specified script and indexes its result.
 */
sealed class UpdateSource<S: ToSource>(
    val upsert: S?,
    val detectNoop: Boolean?,
) {
    class WithDoc<S: ToSource>(
        val doc: S,
        val docAsUpsert: Boolean? = null,
        upsert: S? = null,
        detectNoop: Boolean? = null,
    ) : UpdateSource<S>(
        upsert = upsert,
        detectNoop = detectNoop,
    )

    class WithScript<S: ToSource>(
        val script: Script,
        val scriptedUpsert: Boolean? = null,
        upsert: S? = null,
        detectNoop: Boolean? = null,
    ) : UpdateSource<S>(
        upsert = upsert,
        detectNoop = detectNoop,
    )
}

/**
 * Combines a document source with its action metadata.
 */
class DocSourceAndMeta<M: ActionMeta>(
    val meta: M,
    val doc: ToSource
)

/**
 * A shortcut to attach action metadata to a document source.
 */
fun ToSource.withActionMeta(
    id: String,
    routing: String? = null,
    version: Long? = null,
    seqNo: Long? = null,
    primaryTerm: Long? = null,
): DocSourceAndMeta<IdActionMeta> {
    return DocSourceAndMeta(
        meta = IdActionMeta(
            id = id,
            routing = routing,
            version = version,
            seqNo = seqNo,
            primaryTerm = primaryTerm,
        ),
        doc = this,
    )
}

/**
 * A shortcut to attach action metadata to a document source.
 */
fun ToSource.withActionMeta(
    routing: String? = null,
    version: Long? = null,
    seqNo: Long? = null,
    primaryTerm: Long? = null,
): DocSourceAndMeta<ActionMeta> {
    return DocSourceAndMeta(
        meta = ActionMeta(
            id = null,
            routing = routing,
            version = version,
            seqNo = seqNo,
            primaryTerm = primaryTerm,
        ),
        doc = this,
    )
}
