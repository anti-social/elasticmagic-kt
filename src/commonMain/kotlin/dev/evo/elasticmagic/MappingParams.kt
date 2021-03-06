package dev.evo.elasticmagic

typealias MappingParams = Map<String, Any>
typealias MutableMappingParams = MutableMap<String, Any>

internal fun MappingParams(): MappingParams {
    return emptyMap()
}

internal fun MappingParams(vararg entries: Pair<String, Any?>): MappingParams {
    return entries
        .mapNotNull { (k, v) -> if (v != null) k to v else null }
        .toMap()
}

internal fun MappingParams(
    params: MappingParams?,
    vararg entries: Pair<String, Any?>
): MappingParams {
    return MappingParams(
        *(params?.toList() as? List<Pair<String, Any?>> ?: emptyList()).toTypedArray() + entries
    )
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
