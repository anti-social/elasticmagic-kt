package dev.evo.elasticmagic

typealias Params = Map<String, Any>
typealias MutableParams = MutableMap<String, Any>

internal fun Params(): Params {
    return emptyMap()
}

internal fun Params(vararg entries: Pair<String, Any?>): Params {
    return entries
        .mapNotNull { (k, v) -> if (v != null) k to v else null }
        .toMap()
}

internal fun Params(
    params: Params?,
    vararg entries: Pair<String, Any?>
): Params {
    return Params(
        *(params?.toList() as? List<Pair<String, Any?>> ?: emptyList()).toTypedArray() + entries
    )
}

internal fun MutableParams(): MutableParams {
    return mutableMapOf()
}

internal fun Params.toMutable(): MutableParams {
    return toMutableMap()
}

internal fun <K, V> MutableMap<K, V>.putNotNull(key: K, value: V?) {
    if (value != null) {
        this[key] = value
    }
}

internal fun <K, V> MutableMap<K, V>.putNotNullOrRemove(key: K, value: V?) {
    if (value != null) {
        this[key] = value
    } else {
        remove(key)
    }
}
