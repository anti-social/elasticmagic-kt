package dev.evo.elasticmagic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

import java.util.concurrent.atomic.AtomicReference

/*
 * Origin was taken from:
 * https://github.com/Kotlin/kotlinx.coroutines/blob/1.4.3/kotlinx-coroutines-core/jvm/test/TestBase.kt
 */
actual open class TestBase {
    private var error = AtomicReference<Throwable>()

    private fun setError(e: Throwable) {
        error.compareAndSet(null, e)
    }

    private fun error(message: Any, cause: Throwable? = null): Nothing {
        throw IllegalStateException(message.toString(), cause).also {
            setError(it)
        }
    }

    actual fun runTest(
        expected: ((Throwable) -> Boolean)?,
        block: suspend CoroutineScope.() -> Unit
    ) {
        var ex: Throwable? = null
        try {
            runBlocking(block = block, context = CoroutineExceptionHandler { _, e ->
                if (e is CancellationException) return@CoroutineExceptionHandler // are ignored
                setError(e)
            })
        } catch (e: Throwable) {
            ex = e
            if (expected != null) {
                if (!expected(e)) {
                    error("Unexpected exception", e)
                }
            } else {
                throw e
            }
        } finally {
            if (ex == null && expected != null) {
                error("Exception was expected but none produced")
            }
            error.get()?.let { throw it }
        }
    }
}
