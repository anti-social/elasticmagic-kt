package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer

interface Compiler<I, O> {
    fun compile(input: I): O

    // fun processResult(input: )
}

interface CompilerProvider<OBJ> {
    val serializer: Serializer<OBJ>
    val deserializer: Deserializer<OBJ>

    val mapping: MappingCompiler<OBJ>

    val searchQuery: SearchQueryCompiler<OBJ>
}
