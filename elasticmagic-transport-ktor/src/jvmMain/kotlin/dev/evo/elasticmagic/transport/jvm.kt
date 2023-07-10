package dev.evo.elasticmagic.transport

import io.ktor.client.plugins.compression.ContentEncoding

internal actual val setupContentEncoding: ContentEncoding.Config.() -> Unit = {
    gzip()
    deflate()
    identity()
}
