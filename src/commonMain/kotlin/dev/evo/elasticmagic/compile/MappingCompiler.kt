package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.*
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx

open class MappingCompiler<OBJ>(
    private val serializer: Serializer<OBJ>
) : Compiler<Document, MappingCompiler.Compiled<OBJ>> {

    data class Compiled<OBJ>(val docType: String, val body: OBJ)

    override fun compile(doc: Document): Compiled<OBJ> {
        return MappingCompiler.Compiled(
            doc.docType,
            serializer.obj {
                visit(doc.meta)
                visit(doc as BaseDocument)
            }
        )
    }

    protected fun ObjectCtx.visit(meta: MetaFields) {
        for (metaField in meta._fields) {
            val mappingParams = metaField.getMappingParams()
            if (mappingParams.isEmpty()) {
                continue
            }
            obj(metaField.getQualifiedFieldName()) {
                visit(mappingParams)
            }
        }
    }

    protected fun ObjectCtx.visit(doc: BaseDocument) {
        obj("properties") {
            visit(doc as FieldSet)
        }
    }

    protected fun ObjectCtx.visit(fieldSet: FieldSet) {
        for (field in fieldSet._fields) {
            obj(field.getFieldName()) {
                visit(field)
            }
        }
    }

    protected fun ObjectCtx.visit(field: Field<*>) {
        field("type", field.getFieldType().name)
        visit(field.getMappingParams())
        val subFields = field.getSubFields()
        if (subFields != null) {
            obj("fields") {
                visit(subFields as FieldSet)
            }
        }
        val subDocument = field.getSubDocument()
        if (subDocument != null) {
            visit(subDocument)
        }
    }

    protected fun ObjectCtx.visit(params: Params) {
        for ((name, value) in params) {
            field(name, value)
        }
    }
}
