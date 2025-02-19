package dev.evo.elasticmagic

import kotlinx.coroutines.CoroutineScope

expect class TestResult

/*
 * Origin was Taken from:
 * https://github.com/Kotlin/kotlinx.coroutines/blob/1.4.3/kotlinx-coroutines-core/common/test/TestBase.common.kt
 */
expect open class TestBase constructor() {
    fun runTest(
        expected: ((Throwable) -> Boolean)? = null,
        block: suspend CoroutineScope.() -> Unit
    ): TestResult
}
