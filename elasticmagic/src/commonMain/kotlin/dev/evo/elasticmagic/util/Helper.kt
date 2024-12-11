package dev.evo.elasticmagic.util

import kotlin.time.Duration

private const val USE_MILLISECONDS_WHILE_SECONDS_LESS_THAN = 10

fun Duration.toTimeoutString() = if (inWholeSeconds > USE_MILLISECONDS_WHILE_SECONDS_LESS_THAN) {
    "${inWholeSeconds}s"
} else {
    "${inWholeMilliseconds}ms"
}
