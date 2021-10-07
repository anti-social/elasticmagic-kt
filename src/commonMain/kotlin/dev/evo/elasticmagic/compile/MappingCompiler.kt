package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.AnyField
import dev.evo.elasticmagic.BaseDocument
import dev.evo.elasticmagic.Document
import dev.evo.elasticmagic.ElasticsearchVersion
import dev.evo.elasticmagic.query.Expression
import dev.evo.elasticmagic.FieldSet
import dev.evo.elasticmagic.MetaFields
import dev.evo.elasticmagic.RuntimeFields
import dev.evo.elasticmagic.SubDocumentField
import dev.evo.elasticmagic.SubFieldsField
import dev.evo.elasticmagic.query.ToValue
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx

open class MappingCompiler(
    esVersion: ElasticsearchVersion,
    private val searchQueryCompiler: SearchQueryCompiler,
) : BaseCompiler(esVersion) {

    data class Compiled<OBJ>(val body: OBJ?)

    fun <OBJ> compile(serializer: Serializer<OBJ>, input: Document): Compiled<OBJ> {
        val body = serializer.buildObj {
            visit(this, input)
        }
        return Compiled(body)
    }

    override fun dispatch(ctx: ArrayCtx, value: Any?) {
        when (value) {
            is Expression -> ctx.obj {
                searchQueryCompiler.visit(this, value)
            }
            is ToValue -> {
                ctx.value(value.toValue())
            }
            else -> super.dispatch(ctx, value)
        }
    }

    override fun dispatch(ctx: ObjectCtx, name: String, value: Any?) {
        when (value) {
            is Expression -> ctx.obj(name) {
                searchQueryCompiler.visit(this, value)
            }
            is ToValue -> {
                ctx.field(name, value.toValue())
            }
            else -> super.dispatch(ctx, name, value)
        }
    }

    fun visit(ctx: ObjectCtx, document: Document) {
        visit(ctx, document.meta)
        visit(ctx, document.runtime)
        document.dynamic?.let { dynamic ->
            ctx.field("dynamic", dynamic.toValue())
        }
        visit(ctx, document as BaseDocument)
    }

    private fun visit(ctx: ObjectCtx, meta: MetaFields) {
        for (metaField in meta.getAllFields()) {
            val mappingParams = metaField.getMappingParams()
            if (mappingParams.isEmpty()) {
                continue
            }
            ctx.obj(metaField.getQualifiedFieldName()) {
                visit(this, mappingParams)
            }
        }
    }

    private fun visit(ctx: ObjectCtx, runtime: RuntimeFields) {
        val hasRuntimeFields = runtime.getAllFields().any { !it.isIgnored() }
        if (hasRuntimeFields) {
            ctx.obj("runtime") {
                visit(this, runtime as FieldSet)
            }
        }
    }

    private fun visit(ctx: ObjectCtx, doc: BaseDocument) {
        ctx.obj("properties") {
            visit(this, doc as FieldSet)
        }
    }

    private fun visit(ctx: ObjectCtx, fieldSet: FieldSet) {
        for (field in fieldSet.getAllFields()) {
            if (field.isIgnored()) {
                continue
            }
            ctx.obj(field.getFieldName()) {
                visit(this, field)
            }
        }
    }

    private fun visit(ctx: ObjectCtx, field: AnyField) {
        ctx.field("type", field.getFieldType().name)
        visit(ctx, field.getMappingParams())

        when (field) {
            is SubFieldsField -> {
                ctx.obj("fields") {
                    visit(this, field.subFields)
                }
            }
            is SubDocumentField -> {
                visit(ctx, field.subDocument)
            }
        }
    }
}
