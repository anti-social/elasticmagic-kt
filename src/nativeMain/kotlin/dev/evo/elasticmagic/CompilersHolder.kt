package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.CompilerProvider

import kotlin.native.concurrent.FreezableAtomicReference
import kotlin.native.concurrent.freeze

internal actual class CompilersHolder actual constructor(
    compilers: CompilerProvider?,
    private val fetch: suspend () -> CompilerProvider
) {
    private val compilers = FreezableAtomicReference(compilers)

    actual suspend fun get(): CompilerProvider {
        val currentCompilers = compilers.value
        if (currentCompilers != null) {
            return currentCompilers
        }
        return fetch().also {
            it.freeze()
            compilers.compareAndSet(null, it)
        }
    }
}
