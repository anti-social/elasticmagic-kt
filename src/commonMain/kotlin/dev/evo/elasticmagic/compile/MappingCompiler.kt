package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.*
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx

open class MappingCompiler<OBJ>(
    esVersion: ElasticsearchVersion,
    private val serializer: Serializer<OBJ>
) : BaseCompiler<Document, MappingCompiler.Compiled<OBJ>>(esVersion) {

    data class Compiled<OBJ>(val docType: String, val body: OBJ)

    override fun compile(input: Document): Compiled<OBJ> {
        return Compiled(
            input.docType,
            serializer.obj {
                visit(this, input.meta)
                visit(this, input as BaseDocument)
            }
        )
    }

    protected fun visit(ctx: ObjectCtx, meta: MetaFields) {
        for (metaField in meta._fields) {
            val mappingParams = metaField.getMappingParams()
            if (mappingParams.isEmpty()) {
                continue
            }
            ctx.obj(metaField.getQualifiedFieldName()) {
                visit(this, mappingParams)
            }
        }
    }

    protected fun visit(ctx: ObjectCtx, doc: BaseDocument) {
        ctx.obj("properties") {
            visit(this, doc as FieldSet)
        }
    }

    protected fun visit(ctx: ObjectCtx, fieldSet: FieldSet) {
        for (field in fieldSet._fields) {
            ctx.obj(field.getFieldName()) {
                visit(this, field)
            }
        }
    }

    protected fun visit(ctx: ObjectCtx, field: Field<*>) {
        ctx.field("type", field.getFieldType().name)
        visit(ctx, field.getMappingParams())
        val subFields = field.getSubFields()
        if (subFields != null) {
            ctx.obj("fields") {
                visit(this, subFields as FieldSet)
            }
        }
        val subDocument = field.getSubDocument()
        if (subDocument != null) {
            visit(ctx, subDocument)
        }
    }
}
