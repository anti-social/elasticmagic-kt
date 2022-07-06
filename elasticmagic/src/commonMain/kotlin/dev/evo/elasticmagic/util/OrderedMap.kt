package dev.evo.elasticmagic.util

class OrderedMap<K, V>() {
    private val ixs: HashMap<K, Int> = hashMapOf()
    private val ents: ArrayList<Map.Entry<K, V>?> = arrayListOf()
    private var numberOfTombstones = 0

    class Entry<K, V>(
        override val key: K,
        override val value: V
    ) : Map.Entry<K, V>

    constructor(vararg entries: Pair<K, V>) : this() {
        entries.forEach { (k, v) ->
            put(k, v)
        }
    }

    val size: Int
        get() = ents.size

    val values: Collection<V>
        get() = ents.filterNotNull().map { it.value }

    val keys: Collection<K>
        get() = ents.filterNotNull().map { it.key }

    val entries: Collection<Map.Entry<K, V>>
        get() = ents.filterNotNull()

    fun isEmpty(): Boolean {
        return ents.isEmpty()
    }

    fun containsKey(key: K): Boolean {
        return key in ixs
    }

    fun containsValue(value: V): Boolean {
        return ents.any { it?.value == value }
    }

    operator fun get(key: K): V? {
        val ix = ixs[key] ?: return null
        return ents[ix]?.value
    }

    fun put(key: K, value: V): V? {
        val ix = ixs[key]
        return if (ix == null) {
            ixs[key] = ents.size
            ents.add(Entry(key, value))
            null
        } else {
            val oldValue = ents[ix]?.value
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
            numberOfTombstones++
            ixs.remove(key)
            val oldValue = ents[ix]?.value
            ents[ix] = null
            oldValue
        }
    }

    fun clear() {
        ents.clear()
        ixs.clear()
    }
}
