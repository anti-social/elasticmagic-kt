@file:Suppress("MatchingDeclarationName")

package dev.evo.elasticmagic.transport

import kotlinx.coroutines.CoroutineScope

expect class TestResult

expect fun runTest(block: suspend CoroutineScope.() -> Unit): TestResult
