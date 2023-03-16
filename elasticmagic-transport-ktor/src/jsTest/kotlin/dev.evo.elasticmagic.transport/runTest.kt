package dev.evo.elasticmagic.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

@OptIn(DelicateCoroutinesApi::class)
actual fun runTest(block: suspend CoroutineScope.() -> Unit): dynamic {
    return GlobalScope.promise {
        block()
    }
}
