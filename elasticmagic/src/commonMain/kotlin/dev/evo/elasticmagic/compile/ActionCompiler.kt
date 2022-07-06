package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.bulk.Action
import dev.evo.elasticmagic.bulk.ActionMeta
import dev.evo.elasticmagic.bulk.ConcurrencyControl
import dev.evo.elasticmagic.bulk.DeleteAction
import dev.evo.elasticmagic.bulk.IndexAction
import dev.evo.elasticmagic.bulk.UpdateAction
import dev.evo.elasticmagic.bulk.UpdateSource
import dev.evo.elasticmagic.serde.Serializer

class ActionCompiler(
    esVersion: ElasticsearchVersion,
    private val actionMetaCompiler: ActionMetaCompiler,
    private val actionSourceCompiler: ActionSourceCompiler,
) : BaseCompiler(esVersion) {

    data class Compiled(val header: Serializer.ObjectCtx, val source: Serializer.ObjectCtx?) {
        fun toList(): List<Serializer.ObjectCtx> = listOfNotNull(header, source)
    }

    fun compile(serializer: Serializer, input: Action<*>): Compiled {
        return Compiled(
            actionMetaCompiler.compile(serializer, input),
            actionSourceCompiler.compile(serializer, input)
        )
    }
}

class ActionMetaCompiler(
    esVersion: ElasticsearchVersion,
) : BaseCompiler(esVersion) {
    fun compile(serializer: Serializer, input: Action<*>): Serializer.ObjectCtx {
        return serializer.obj {
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
    fun compile(serializer: Serializer, input: Action<*>): Serializer.ObjectCtx? {
        if (input is DeleteAction) {
            return null
        }
        return serializer.obj {
            visit(this, input)
        }
    }

    private fun visit(ctx: Serializer.ObjectCtx, action: Action<*>) {
        when (action) {
            is IndexAction<*> -> {
                visit(ctx, action.source.toSource())
            }
            is UpdateAction<*> -> {
                val source = action.source
                if (source.upsert != null) {
                    ctx.obj("upsert") {
                        visit(this, source.upsert.toSource())
                    }
                }
                ctx.fieldIfNotNull("detect_noop", source.detectNoop)

                when (source) {
                    is UpdateSource.WithDoc<*> -> {
                        ctx.obj("doc") {
                            visit(this, source.doc.toSource())
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
