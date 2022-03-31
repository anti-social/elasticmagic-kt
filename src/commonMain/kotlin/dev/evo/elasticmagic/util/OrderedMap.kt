package dev.evo.elasticmagic.util

internal class OrderedMap<K, V> {
    private val ixs: HashMap<K, Int> = hashMapOf()
    private val ents: ArrayList<Map.Entry<K, V>> = arrayListOf()

    class Entry<K, V>(
        override val key: K,
        override val value: V
    ) : Map.Entry<K, V>

    val size: Int
        get() = ents.size

    val values: Collection<V>
        get() = ents.map { it.value }

    val keys: Collection<K>
        get() = ents.map { it.key }

    val entries: Collection<Map.Entry<K, V>>
        get() = ents

    fun isEmpty(): Boolean {
        return ents.isEmpty()
    }

    fun containsKey(key: K): Boolean {
        return key in ixs
    }

    fun containsValue(value: V): Boolean {
        return ents.any { it.value == value }
    }

    operator fun get(key: K): V? {
        return ents[ixs[key] ?: return null].value
    }

    fun put(key: K, value: V): V? {
        val ix = ixs[key]
        return if (ix == null) {
            ixs[key] = ents.size
            ents.add(Entry(key, value))
            null
        } else {
            val oldValue = ents[ix].value
            ents[ix] = Entry(key, value)
            oldValue
        }
    }

    operator fun set(key: K, value: V) {
        put(key, value)
    }

    fun putAll(from: Map<out K, V>) {
        for ((key, value) in from) {
            put(key, value)
        }
    }

    fun remove(key: K): V? {
        val ix = ixs[key]
        return if (ix == null) {
            null
        } else {
            ixs.remove(key)
            ents.removeAt(ix).value
        }
    }

    fun clear() {
        ents.clear()
        ixs.clear()
    }
}
