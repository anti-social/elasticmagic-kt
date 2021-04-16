package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.CompilerProvider

internal actual class CompilersHolder actual constructor(
    private var compilers: CompilerProvider?,
    private val fetch: suspend () -> CompilerProvider
) {
    actual suspend fun get(): CompilerProvider {
        val currentCompilers = compilers
        if (currentCompilers != null) {
            return currentCompilers
        }
        return fetch().also {
            if (compilers != null) {
                compilers = it
            }
        }
    }
}
