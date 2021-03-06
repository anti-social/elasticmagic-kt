package dev.evo.elasticmagic

typealias MappingParams = Map<String, Any>

internal fun MappingParams(): MutableMap<String, Any> {
    return mutableMapOf()
}

internal fun <K, V> MutableMap<K, V>.putNotNull(key: K, value: V?) {
    if (value != null) {
        this[key] = value
    }
}
