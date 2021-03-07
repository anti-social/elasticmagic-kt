package dev.evo.elasticmagic.compile

interface Compiler<I, R> {
    fun compile(input: I): R
}
