package dev.evo.elasticmagic.serde

enum class Platform {
    JVM, NATIVE, JS
}

expect val platform: Platform
