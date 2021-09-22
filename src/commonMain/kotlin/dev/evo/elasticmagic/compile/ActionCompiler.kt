package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.Action
import dev.evo.elasticmagic.ActionMeta
import dev.evo.elasticmagic.ConcurrencyControl
import dev.evo.elasticmagic.DeleteAction
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.IndexAction
import dev.evo.elasticmagic.UpdateAction
import dev.evo.elasticmagic.UpdateSource
import dev.evo.elasticmagic.serde.Serializer

class ActionCompiler(
    esVersion: ElasticsearchVersion,
    private val actionMetaCompiler: ActionMetaCompiler,
    private val actionSourceCompiler: ActionSourceCompiler,
) : BaseCompiler(esVersion) {

    data class Compiled<OBJ>(val header: OBJ, val source: OBJ?)

    fun <OBJ> compile(serializer: Serializer<OBJ>, input: Action<*>): Compiled<OBJ> {
        return Compiled(
            actionMetaCompiler.compile(serializer, input),
            actionSourceCompiler.compile(serializer, input)
        )
    }
}

class ActionMetaCompiler(
    esVersion: ElasticsearchVersion,
    val features: ElasticsearchFeatures,
) : BaseCompiler(esVersion) {
    fun <OBJ> compile(serializer: Serializer<OBJ>, input: Action<*>): OBJ {
        return serializer.buildObj {
            visit(this, input)
        }
    }

    private fun visit(ctx: Serializer.ObjectCtx, action: Action<*>) {
        ctx.obj(action.name) {
            val meta = action.meta
            visit(this, meta)

            when (action.concurrencyControl) {
                ConcurrencyControl.SEQ_NO -> {
                    field("if_seq_no", meta.seqNo)
                    field("if_primary_term", meta.primaryTerm)
                }
                ConcurrencyControl.VERSION -> {
                    field("version_type", "external")
                    fieldIfNotNull("version", meta.version)
                }
                ConcurrencyControl.VERSION_GTE -> {
                    field("version_type", "external_gte")
                    fieldIfNotNull("version", meta.version)
                }
                null -> {}
            }
        }
    }

    private fun visit(ctx: Serializer.ObjectCtx, meta: ActionMeta) {
        ctx.fieldIfNotNull("_id", meta.id)
        if (features.requiresMappingTypeName) {
            ctx.field("_type", "_doc")
        }
        ctx.fieldIfNotNull("routing", meta.routing)
    }
}

class ActionSourceCompiler(
    esVersion: ElasticsearchVersion,
    private val searchQueryCompiler: SearchQueryCompiler,
) : BaseCompiler(esVersion) {
    fun <OBJ> compile(serializer: Serializer<OBJ>, input: Action<*>): OBJ? {
        if (input is DeleteAction) {
            return null
        }
        return serializer.buildObj {
            visit(this, input)
        }
    }

    private fun visit(ctx: Serializer.ObjectCtx, action: Action<*>) {
        when (action) {
            is IndexAction<*> -> {
                visit(ctx, action.source.getSource())
            }
            is UpdateAction<*> -> {
                val source = action.source
                if (source.upsert != null) {
                    ctx.obj("upsert") {
                        visit(this, source.upsert.getSource())
                    }
                }
                ctx.fieldIfNotNull("detect_noop", source.detectNoop)

                when (source) {
                    is UpdateSource.WithDoc<*> -> {
                        ctx.obj("doc") {
                            visit(this, source.doc.getSource())
                        }
                        ctx.fieldIfNotNull("doc_as_upsert", source.docAsUpsert)
                    }
                    is UpdateSource.WithScript<*> -> {
                        searchQueryCompiler.visit(ctx, source.script)
                    }
                }
            }
            is DeleteAction -> {}
        }
    }
}
