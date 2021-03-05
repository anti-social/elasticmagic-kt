package dev.evo.elasticmagic

/**
 * Holds field operations shortcuts.
 */
abstract class FieldOperations {
    // TODO: consider to make following fields lateinit
    private var name: String = ""
    private var qualifiedName: String = ""

    fun getFieldName(): String = name

    fun getQualifiedFieldName(): String {
        return if (qualifiedName.isNotEmpty()) {
            qualifiedName
        } else {
            name
        }
    }

    @Suppress("FunctionName")
    protected fun _setFieldName(fieldName: String) {
        if (name.isNotEmpty()) {
            throw IllegalStateException(
                "Field [$fieldName] has already been initialized as [$name]")
        }
        name = fieldName
    }

    // TODO: make protected
    @Suppress("FunctionName")
    internal open fun _bindToParent(parent: FieldOperations) {
        if (qualifiedName.isNotEmpty()) {
            throw IllegalStateException(
                "Field [$name] has already been bound as [$qualifiedName]"
            )
        }
        val parentQualifiedFieldName = parent.qualifiedName
        this.qualifiedName = if (parentQualifiedFieldName.isNotEmpty()) {
            "${parentQualifiedFieldName}.$name"
        } else {
            name
        }
    }

    // fun eq(other: Any?) = Term(this, other)
    // fun match(other: Any?) = Match(this, other)
}
