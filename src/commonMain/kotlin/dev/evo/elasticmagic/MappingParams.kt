package dev.evo.elasticmagic

typealias MappingParams = Map<String, Any>
typealias MutableMappingParams = MutableMap<String, Any>

internal fun MappingParams(): MappingParams {
    return mapOf()
}

internal fun MutableMappingParams(): MutableMappingParams {
    return mutableMapOf()
}

internal fun MappingParams.toMutable(): MutableMappingParams {
    return toMutableMap()
}

internal fun <K, V> MutableMap<K, V>.putNotNull(key: K, value: V?) {
    if (value != null) {
        this[key] = value
    }
}
