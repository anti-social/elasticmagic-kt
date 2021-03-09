package dev.evo.elasticmagic.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

actual fun runTest(block: suspend CoroutineScope.() -> Unit): dynamic {
    return GlobalScope.promise {
        block()
    }
}
