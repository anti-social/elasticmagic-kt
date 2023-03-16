package dev.evo.elasticmagic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/*
 * Origin was taken from:
 * https://github.com/Kotlin/kotlinx.coroutines/blob/1.4.3/kotlinx-coroutines-core/native/test/TestBase.kt
 */
actual open class TestBase {
    private var error: Throwable? = null

    private fun setError(cause: Throwable) {
        if (error == null) {
            error = cause
        }
    }
    private fun error(message: Any, cause: Throwable? = null): Nothing {
        val exception = IllegalStateException(message.toString(), cause)
        if (error == null) error = exception
        throw exception
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
                if (!expected(e))
                    error("Unexpected exception: $e", e)
            } else
                throw e
        } finally {
            if (ex == null && expected != null) {
                error("Exception was expected but none produced")
            }
        }
    }
}
