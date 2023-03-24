package dev.evo.elasticmagic

actual fun getenv(name: String): String? = js("process.env[name]") as String?
