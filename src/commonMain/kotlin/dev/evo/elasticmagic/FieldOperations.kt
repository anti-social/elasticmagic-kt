package dev.evo.elasticmagic

interface Named {
    fun getFieldName(): String

    fun getQualifiedFieldName(): String
}

/**
 * Holds field operations shortcuts.
 */
interface FieldOperations : Named {
    fun eq(other: Any?): Unit = TODO("Term(this, other)")
    fun match(other: Any?): Unit = TODO("Match(this, other)")
}
