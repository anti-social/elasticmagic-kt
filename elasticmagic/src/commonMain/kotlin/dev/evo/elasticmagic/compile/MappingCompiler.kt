package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.ToValue
import dev.evo.elasticmagic.doc.MappingField
import dev.evo.elasticmagic.doc.BaseDocument
import dev.evo.elasticmagic.doc.BoundMappingTemplate
import dev.evo.elasticmagic.doc.BoundRuntimeField
import dev.evo.elasticmagic.doc.Document
import dev.evo.elasticmagic.doc.DynamicTemplates
import dev.evo.elasticmagic.doc.FieldSet
import dev.evo.elasticmagic.doc.MappingOptions
import dev.evo.elasticmagic.doc.MetaFields
import dev.evo.elasticmagic.doc.SubDocument
import dev.evo.elasticmagic.doc.SubDocumentField
import dev.evo.elasticmagic.doc.SubFieldsField
import dev.evo.elasticmagic.query.ArrayExpression
import dev.evo.elasticmagic.query.ObjExpression
import dev.evo.elasticmagic.serde.Serializer
import dev.evo.elasticmagic.serde.Serializer.ArrayCtx
import dev.evo.elasticmagic.serde.Serializer.ObjectCtx
import dev.evo.elasticmagic.types.AnyFieldType
import dev.evo.elasticmagic.types.ObjectType

open class MappingCompiler(
    features: ElasticsearchFeatures,
    private val searchQueryCompiler: SearchQueryCompiler,
) : BaseCompiler(features) {

    data class Compiled(val body: ObjectCtx?)

    fun compile(serializer: Serializer, input: Document): Compiled {
        val body = serializer.obj {
            visit(this, input)
        }
        return Compiled(body)
    }

    override fun dispatch(ctx: ArrayCtx, value: Any?) {
        when (value) {
            is ObjExpression -> ctx.obj {
                searchQueryCompiler.visit(this, value)
            }
            is ArrayExpression -> ctx.array {
                searchQueryCompiler.visit(ctx, value)
            }
            is ToValue<*> -> {
                ctx.value(value.toValue())
            }
            else -> super.dispatch(ctx, value)
        }
    }

    override fun dispatch(ctx: ObjectCtx, name: String, value: Any?) {
        when (value) {
            is ObjExpression -> ctx.obj(name) {
                searchQueryCompiler.visit(this, value)
            }
            is ArrayExpression -> ctx.array(name) {
                searchQueryCompiler.visit(this, value)
            }
            is ToValue<*> -> {
                ctx.field(name, value.toValue())
            }
            else -> super.dispatch(ctx, name, value)
        }
    }

    fun visit(ctx: ObjectCtx, document: Document) {
        visit(ctx, document.options)
        visit(ctx, document.meta)
        visit(ctx, document.runtime)
        visit(ctx, document as BaseDocument)

        if (document.getAllFields().any { it is BoundRuntimeField }) {
            ctx.obj("runtime") {
                visit(this, document as FieldSet) { it is BoundRuntimeField }
            }
        }

        val templates = document.getAllTemplates()
        if (templates.isNotEmpty()) {
            ctx.array("dynamic_templates") {
                for (tmpl in templates) {
                    obj {
                        visit(this, tmpl)
                    }
                }
            }
        }
    }

    private fun visit(ctx: ObjectCtx, mappingOptions: MappingOptions) {
        ctx.fieldIfNotNull("dynamic", mappingOptions.dynamic?.toValue())
        ctx.fieldIfNotNull("numeric_detection", mappingOptions.numericDetection)
        ctx.fieldIfNotNull("date_detection", mappingOptions.dateDetection)
        if (mappingOptions.dynamicDateFormats != null) {
            ctx.array("dynamic_date_formats") {
                visit(this, mappingOptions.dynamicDateFormats)
            }
        }
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

    private fun visit(ctx: ObjectCtx, template: BoundMappingTemplate<*, *, *>) {
        ctx.obj(template.name) {
            visit(this, template.matchOptions)
            visit(this, template.mapping)
        }
    }

    private fun visit(ctx: ObjectCtx, matchOptions: DynamicTemplates.MatchOptions) {
        ctx.fieldIfNotNull("match", matchOptions.match)
        ctx.fieldIfNotNull("unmatch", matchOptions.unmatch)
        ctx.fieldIfNotNull("path_match", matchOptions.pathMatch)
        ctx.fieldIfNotNull("path_unmatch", matchOptions.pathUnmatch)
        ctx.fieldIfNotNull("match_pattern", matchOptions.matchPattern?.toValue())
        ctx.fieldIfNotNull("match_mapping_type", matchOptions.matchMappingType?.toValue())
        if (matchOptions.params != null) {
            visit(ctx, matchOptions.params)
        }
    }

    private fun visit(ctx: ObjectCtx, mapping: DynamicTemplates.DynamicField<*, *, *>) {
        when (mapping) {
            is DynamicTemplates.DynamicField.Simple -> {
                ctx.obj(mapping.mappingKind.toValue()) {
                    if (mapping.params.isNotEmpty()) {
                        visit(this, mapping.params)
                    }
                }
            }
            is DynamicTemplates.DynamicField.FromField<*, *> -> {
                ctx.obj(mapping.mappingKind.toValue()) {
                    visit(this, mapping.field)
                }
            }
            is DynamicTemplates.DynamicField.FromSubFields<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                mapping as DynamicTemplates.DynamicField.FromSubFields<Any, *>

                ctx.obj("mapping") {
                    visit(this, mapping.subFields.unboundField)
                    obj("fields") {
                        val subFields = mapping.subFields.subFieldsFactory(
                            DynamicTemplates.instantiateField(
                                "", AnyFieldType, mapping.subFields.unboundField.params
                            )
                        )
                        visit(this, subFields)
                    }
                }
            }
            is DynamicTemplates.DynamicField.FromSubDocument<*> -> {
                ctx.obj("mapping") {
                    field("type", mapping.subDocument.type.name)
                    visit(this, mapping.subDocument.params)
                    val field = DynamicTemplates.instantiateField(
                        "", ObjectType(), mapping.subDocument.params
                    )
                    val subDocument = mapping.subDocument.subDocumentFactory(field)
                    visit(this, subDocument)
                }
            }
        }
    }

    private fun visit(ctx: ObjectCtx, field: FieldSet.Field<*, *>) {
        ctx.field("type", field.type.name)
        visit(ctx, field.params)
    }

    private fun visit(ctx: ObjectCtx, doc: BaseDocument) {
        ctx.obj("properties") {
            visit(this, doc as FieldSet) { it !is BoundRuntimeField }
        }
    }

    private fun visit(ctx: ObjectCtx, doc: SubDocument) {
        visit(ctx, doc.options)
        visit(ctx, doc as BaseDocument)
    }

    private fun visit(
        ctx: ObjectCtx,
        fieldSet: FieldSet,
        fieldPredicate: (MappingField<*>) -> Boolean = { true }
    ) {
        for (field in fieldSet.getAllFields()) {
            if (!fieldPredicate(field)) {
                continue
            }
            ctx.obj(field.getFieldName()) {
                visit(this, field)
            }
        }
    }

    private fun visit(ctx: ObjectCtx, field: MappingField<*>) {
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
