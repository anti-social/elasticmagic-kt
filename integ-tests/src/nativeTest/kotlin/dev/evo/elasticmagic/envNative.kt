package dev.evo.elasticmagic

import kotlinx.cinterop.toKString

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun getenv(name: String): String? = platform.posix.getenv(name)?.toKString()
