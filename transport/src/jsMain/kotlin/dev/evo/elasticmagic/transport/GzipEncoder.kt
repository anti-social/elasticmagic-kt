package dev.evo.elasticmagic.transport

@Suppress("MayBeConst")
actual val isGzipSupported: Boolean = false

actual class GzipEncoder : RequestEncoder {
    override fun append(value: CharSequence?): Appendable {
        TODO("not implemented")
    }

    override fun toByteArray(): ByteArray {
        TODO("not implemented")
    }
}
