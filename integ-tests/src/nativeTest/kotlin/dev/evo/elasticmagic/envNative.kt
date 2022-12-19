package dev.evo.elasticmagic

import kotlinx.cinterop.toKString

actual fun getenv(name: String): String? = platform.posix.getenv(name)?.toKString()
