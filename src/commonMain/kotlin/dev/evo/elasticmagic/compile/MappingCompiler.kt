package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.*
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx

open class MappingCompiler(
    esVersion: ElasticsearchVersion,
) : BaseCompiler(esVersion) {

    data class Compiled<OBJ>(val body: OBJ?)

    fun <OBJ> compile(serializer: Serializer<OBJ>, input: Document): Compiled<OBJ> {
        val body = serializer.buildObj {
            visit(this, input)
        }
        return Compiled(body)
    }

    fun visit(ctx: ObjectCtx, document: Document) {
        visit(ctx, document.meta)
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

    private fun visit(ctx: ObjectCtx, doc: BaseDocument) {
        ctx.obj("properties") {
            visit(this, doc as FieldSet)
        }
    }

    private fun visit(ctx: ObjectCtx, fieldSet: FieldSet) {
        for (field in fieldSet.getAllFields()) {
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
                println(field.subFields.getAllFields())
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
