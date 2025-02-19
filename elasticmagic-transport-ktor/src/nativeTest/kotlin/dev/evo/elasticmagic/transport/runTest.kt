package dev.evo.elasticmagic.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

actual typealias TestResult = Unit

actual fun runTest(block: suspend CoroutineScope.() -> Unit): TestResult = runBlocking { block() }
