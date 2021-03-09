package dev.evo.elasticmagic

typealias RawSource = Map<String, Any?>

abstract class Source {
    abstract fun setField(name: String, value: Any?)

    abstract fun getField(name: String): Any?
}

class StdSource : Source() {
    private val source =  mutableMapOf<String, Any?>()

    override fun setField(name: String, value: Any?) {
        source[name] = value
    }

    override fun getField(name: String): Any? {
        return source[name]
    }
}
