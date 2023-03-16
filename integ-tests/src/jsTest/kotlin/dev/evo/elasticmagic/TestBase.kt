package dev.evo.elasticmagic

import kotlin.js.Promise

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

/*
 * Origin was taken from:
 * https://github.com/Kotlin/kotlinx.coroutines/blob/1.4.3/kotlinx-coroutines-core/js/test/TestBase.kt
 */
actual open class TestBase {
    private var error: Throwable? = null

    private fun setError(cause: Throwable) {
        if (error == null) {
            error = cause
        }
    }

    private fun error(message: Any, cause: Throwable? = null): Nothing {
        console.log(cause)
        val exception = IllegalStateException(
            if (cause == null) message.toString() else "$message; caused by $cause"
        )
        if (error == null) error = exception
        throw exception
    }

    // Somehow it is not work with such a wrapper. Find out why
    // actual fun runTest(block: suspend CoroutineScope.() -> Unit) {
    //     runTest(null, block)
    // }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("ACTUAL_FUNCTION_WITH_DEFAULT_ARGUMENTS")
    actual fun runTest(
        expected: ((Throwable) -> Boolean)? = null,
        block: suspend CoroutineScope.() -> Unit
    ): dynamic {
        var ex: Throwable? = null
        return GlobalScope.promise(block = block, context = CoroutineExceptionHandler { _, e ->
            if (e is CancellationException) return@CoroutineExceptionHandler // are ignored
            setError(e)
        }).catch { e ->
            ex = e
            if (expected != null) {
                if (!expected(e)) {
                    error("Unexpected exception", e)
                }
            } else {
                throw e
            }
        }.always {
            if (ex == null && expected != null) {
                error("Exception was expected but none produced")
            }
            error?.let { throw it }
        }
    }
}

private fun <T> Promise<T>.always(block: () -> Unit): Promise<T> =
    then(
        onFulfilled = { value ->
            block()
            value
        },
        onRejected = { ex ->
            block()
            throw ex
        }
    )
