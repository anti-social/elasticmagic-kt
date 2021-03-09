package dev.evo.elasticmagic.transport

actual val isGzipEncoderImplemented: Boolean = false

actual class GzipEncoder : RequestEncoder {
    override fun append(value: CharSequence?): Appendable {
        TODO("not implemented")
    }

    override fun toByteArray(): ByteArray {
        TODO("not implemented")
    }
}
