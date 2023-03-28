package dev.evo.elasticmagic.qf

import dev.evo.elasticmagic.types.FieldType
import dev.evo.elasticmagic.types.ValueDeserializationException

typealias QueryFilterParams = Map<List<String>, List<String>>
typealias MutableQueryFilterParams = MutableMap<List<String>, MutableList<String>>

fun MutableQueryFilterParams(): MutableQueryFilterParams = mutableMapOf()

fun <T> FieldType<*, T>.deserializeTermOrNull(term: Any): T? {
    return try {
        deserializeTerm(term)
    } catch (e: ValueDeserializationException) {
        null
    }
}

fun <T> QueryFilterParams.decodeValues(
    key: String, fieldType: FieldType<*, T>
): List<T & Any> {
    return get(listOf(key))?.mapNotNull(fieldType::deserializeTermOrNull) ?: emptyList()
}

fun <T> QueryFilterParams.decodeValues(
    key: List<String>, fieldType: FieldType<*, T>
): List<T & Any> {
    return get(key)?.mapNotNull(fieldType::deserializeTermOrNull) ?: emptyList()
}

fun <T> QueryFilterParams.decodeLastValue(
    key: String, fieldType: FieldType<*, T>
): T? {
    return decodeValues(listOf(key), fieldType).lastOrNull()
}

fun <T> QueryFilterParams.decodeLastValue(
    key: List<String>, fieldType: FieldType<*, T>
): T? {
    return decodeValues(key, fieldType).lastOrNull()
}

sealed class MatchKey {
    abstract fun match(key: String): Boolean

    class Exact(val key: String) : MatchKey() {
        override fun match(key: String): Boolean {
            return this.key == key
        }
    }

    class Type(val fieldType: FieldType<*, *>) : MatchKey() {
        override fun match(key: String): Boolean {
            return try {
                fieldType.deserializeTerm(key)
                true
            } catch (e: ValueDeserializationException) {
                false
            }
        }
    }
}
