package dev.evo.elasticmagic

import dev.evo.elasticmagic.compile.CompilerProvider
import java.util.concurrent.atomic.AtomicReference

internal actual class CompilersHolder actual constructor(
    compilers: CompilerProvider?,
    private val fetch: suspend () -> CompilerProvider
) {
    private val compilers = AtomicReference(compilers)

    actual suspend fun get(): CompilerProvider {
        val currentCompilers = compilers.get()
        if (currentCompilers != null) {
            return currentCompilers
        }
        return fetch().also {
            compilers.compareAndSet(null, it)
        }
    }
}