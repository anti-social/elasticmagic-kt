package dev.evo.elasticmagic.transport

import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

@JsName("Promise")
external class MyPromise {
    fun then(onFulfilled: ((Unit) -> Unit), onRejected: ((Throwable) -> Unit)): MyPromise
    fun then(onFulfilled: ((Unit) -> Unit)): MyPromise
}

public actual typealias TestResult = MyPromise

@OptIn(DelicateCoroutinesApi::class)
actual fun runTest(block: suspend CoroutineScope.() -> Unit): TestResult {
    val result = GlobalScope.promise {
        block()
    }
    return result as TestResult
}
