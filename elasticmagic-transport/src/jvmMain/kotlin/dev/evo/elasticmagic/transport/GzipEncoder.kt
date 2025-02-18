package dev.evo.elasticmagic.transport

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

actual fun createGzipEncoder(): BaseGzipEncoder? = GzipEncoder()

internal class GzipEncoder : BaseGzipEncoder() {
    private val buf = ByteArrayOutputStream()
    private val gzipStream = GZIPOutputStream(buf)

    override fun append(value: CharSequence?): Appendable {
        val str = when (value) {
            null -> "null"
            is String -> value
            else -> value.toString()
        }
        gzipStream.write(str.encodeToByteArray(throwOnInvalidSequence = true))
        return this
    }

    override fun toByteArray(): ByteArray {
        gzipStream.finish()
        return buf.toByteArray()
    }
}
