package dev.evo.elasticmagic

import kotlin.js.Promise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

@JsName("Promise")
external class MyPromise {
    fun then(onFulfilled: ((Unit) -> Unit), onRejected: ((Throwable) -> Unit)): MyPromise
    fun then(onFulfilled: ((Unit) -> Unit)): MyPromise
}

/** Always a `Promise<Unit>` */
public actual typealias TestResult = MyPromise

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

    @OptIn(DelicateCoroutinesApi::class)
    actual fun runTest(
        expected: ((Throwable) -> Boolean)?,
        block: suspend CoroutineScope.() -> Unit
    ): TestResult {
        var ex: Throwable? = null
        val result = GlobalScope.promise(block = block, context = CoroutineExceptionHandler { _, e ->
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
        return result as TestResult
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
