package dev.evo.elasticmagic.compile

import dev.evo.elasticmagic.serde.Deserializer
import dev.evo.elasticmagic.serde.Serializer

interface Compiler<I, R> {
    fun compile(input: I): R

    // fun processResult(input: )
}

interface CompilerProvider<OBJ, ARR> {
    val serializer: Serializer<OBJ, ARR>
    val deserializer: Deserializer<OBJ, ARR>

    val mapping: MappingCompiler<OBJ, ARR>

    val searchQuery: SearchQueryCompiler<OBJ, ARR>
}
