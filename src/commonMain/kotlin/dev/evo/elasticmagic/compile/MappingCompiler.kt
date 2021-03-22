package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.*
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx

open class MappingCompiler(
    esVersion: ElasticsearchVersion,
) : BaseCompiler<Document>(esVersion) {

    data class Compiled<T>(val docType: String, override val body: T?) : Compiler.Compiled<T>()

    override fun <T> compile(serializer: Serializer<T>, input: Document): Compiled<T> {
        val body = serializer.buildObj {
            visit(this, input.meta)
            visit(this, input as BaseDocument)
        }
        return Compiled(
            input.docType,
            body
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

    protected fun visit(ctx: ObjectCtx, field: Field<*, *>) {
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
